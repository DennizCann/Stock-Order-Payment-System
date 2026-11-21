package com.denizcan.stockorderpayment.service.inventory;

import com.denizcan.stockorderpayment.domain.inventory.*;
import com.denizcan.stockorderpayment.domain.user.*;
import com.denizcan.stockorderpayment.repository.inventory.StockOperationRepository;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import com.denizcan.stockorderpayment.web.inventory.dto.InventoryOperationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.*;

@Service @RequiredArgsConstructor
public class InventoryOperationService {
    private final UserRepository users;
    private final StockOperationRepository operations;
    private final InventoryOperationExecutor executor;
    private final PlatformTransactionManager transactions;
    private final ObjectMapper json;

    public Operation create(String username, InventoryOperationRequest request) {
        Actor actor = actor(username);
        authorize(actor, request);
        SortedMap<Long,Integer> items = new TreeMap<>();
        for (var item : request.items()) {
            if (item.productId() == null || item.quantity() == null || item.productId() <= 0 || item.quantity() <= 0) throw bad("Ürün ve pozitif adet gerekiyor.");
            try { items.merge(item.productId(), item.quantity(), Math::addExact); }
            catch (ArithmeticException exception) { throw bad("Aynı ürünün toplam adedi desteklenen sınırı aşıyor."); }
        }
        String fingerprint = fingerprint(actor.user().getId(), request, items);
        var existing = operations.findById(request.requestId());
        if (existing.isPresent()) return replay(existing.get(), fingerprint, actor);
        UUID id;
        try { id = executor.execute(actor.user(), request, fingerprint, items); }
        catch (DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException | jakarta.persistence.EntityExistsException exception) {
            // The losing transaction has rolled back before checking a concurrent retry's committed receipt.
            var committed = operations.findById(request.requestId());
            if (committed.isPresent()) return replay(committed.get(), fingerprint, actor);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stok başka bir işlemde değişmiş. İşlem kaydedilmedi; tekrar deneyebilirsin.");
        }
        return response(operations.findById(id).orElseThrow(), actor);
    }

    @Transactional(readOnly = true)
    public List<Operation> list(String username) {
        Actor actor = actor(username);
        if (!actor.permissions().contains(actor.branch() == null ? Permission.STOCK_VIEW : Permission.BRANCH_STOCK_VIEW)) throw denied();
        var page = PageRequest.of(0, 100);
        var ids = actor.branch() == null ? operations.recentIds(page) : operations.recentIdsForBranch(actor.branch(), page);
        if (ids.isEmpty()) return List.of();
        return operations.findAllByIdInOrderByCreatedAtDescIdDesc(ids).stream().map(o -> response(o, actor)).toList();
    }

    private void authorize(Actor actor, InventoryOperationRequest request) {
        if (request.requestId() == null || request.kind() == null || request.items() == null || request.items().isEmpty()) throw bad("İşlem numarası, tür ve ürünler gerekiyor.");
        if (request.kind() == StockOperation.Kind.IN && request.sourceBranchId() != null
                || request.kind() == StockOperation.Kind.OUT && request.targetBranchId() != null) throw bad("Hareket türüne uygun kaynak ve hedef seç.");
        if (request.kind() == StockOperation.Kind.TRANSFER) {
            if (Objects.equals(request.sourceBranchId(), request.targetBranchId())) throw bad("Aktarım kaynağı ve hedefi farklı olmalı.");
            if (actor.branch() != null || !actor.permissions().contains(Permission.STOCK_TRANSFER)) throw denied();
        } else if (actor.branch() != null) {
            Long target = request.kind() == StockOperation.Kind.IN ? request.targetBranchId() : request.sourceBranchId();
            if (!actor.permissions().contains(Permission.BRANCH_STOCK_MANAGE) || !actor.branch().equals(target)) throw denied();
        } else if (!actor.permissions().contains(Permission.STOCK_MANAGE)) throw denied();
    }

    private Actor actor(String username) {
        var template = new TransactionTemplate(transactions); template.setReadOnly(true);
        return template.execute(status -> {
            User user = users.findByUsername(username).filter(User::isEnabled).orElseThrow(InventoryOperationService::denied);
            Long branch = user.getRole() == Role.BRANCH_EMPLOYEE && user.getBranch() != null ? user.getBranch().getId() : null;
            if (user.getRole() == Role.BRANCH_EMPLOYEE && branch == null) throw denied();
            return new Actor(user, Set.copyOf(Permission.forUser(user)), branch);
        });
    }

    private Operation replay(StockOperation operation, String fingerprint, Actor actor) {
        if (!operation.getFingerprint().equals(fingerprint)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Bu işlem numarası farklı bilgilerle kullanılmış. Önce önceki işlemi kontrol et.");
        return response(operation, actor);
    }

    private String fingerprint(Long actor, InventoryOperationRequest r, SortedMap<Long,Integer> items) {
        try {
            byte[] payload = json.writeValueAsBytes(List.of(actor, r.kind().name(), Objects.toString(r.sourceBranchId(), "central"),
                    Objects.toString(r.targetBranchId(), "central"), Objects.toString(r.typeId(), ""), normalized(r.label()), normalized(r.note()), items));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (Exception exception) { throw new IllegalStateException("Operation fingerprint unavailable", exception); }
    }

    private Operation response(StockOperation o, Actor actor) {
        boolean sourceVisible = actor.branch() == null || actor.branch().equals(o.getSourceBranchId());
        boolean targetVisible = actor.branch() == null || actor.branch().equals(o.getTargetBranchId());
        return new Operation(o.getId(), o.getKind(), o.getSourceBranchId(), o.getTargetBranchId(), o.getSourceName(), o.getTargetName(),
                o.getLabel(), o.getNote(), o.getUsername(), o.getCreatedAt(), o.getLines().stream()
                .map(l -> new Line(l.getProductId(), l.getProductName(), l.getSku(), l.getQuantity(),
                        sourceVisible ? l.getSourceRemaining() : null, targetVisible ? l.getTargetRemaining() : null)).toList());
    }
    private static String normalized(String value) { return value == null ? "" : value.strip(); }
    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Bu stok hareketi için yetkiniz yok."); }
    private record Actor(User user, Set<Permission> permissions, Long branch) {}
    public record Operation(UUID id, StockOperation.Kind kind, Long sourceBranchId, Long targetBranchId, String sourceName,
                            String targetName, String label, String note, String username, Instant createdAt, List<Line> lines) {}
    public record Line(Long productId, String productName, String sku, int quantity, Integer sourceRemaining, Integer targetRemaining) {}
}
