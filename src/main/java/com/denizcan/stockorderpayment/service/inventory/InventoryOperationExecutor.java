package com.denizcan.stockorderpayment.service.inventory;

import com.denizcan.stockorderpayment.domain.inventory.*;
import com.denizcan.stockorderpayment.domain.branch.*;
import com.denizcan.stockorderpayment.domain.stock.Stock;
import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.domain.user.User;
import com.denizcan.stockorderpayment.repository.branch.*;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.inventory.*;
import com.denizcan.stockorderpayment.web.inventory.dto.InventoryOperationRequest;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class InventoryOperationExecutor {
    private final EntityManager em;
    private final ProductRepository products;
    private final BranchRepository branches;
    private final StockRepository central;
    private final BranchStockRepository branchStock;
    private final InventoryTransactionRepository centralHistory;
    private final BranchStockMovementRepository branchHistory;
    private final StockOperationTypeRepository types;

    @Transactional
    public UUID execute(User actor, InventoryOperationRequest request, String fingerprint, SortedMap<Long,Integer> quantities) {
        Map<Long,Branch> locations = new TreeMap<>();
        for (Long id : new TreeSet<>(java.util.stream.Stream.of(request.sourceBranchId(), request.targetBranchId())
                .filter(Objects::nonNull).toList())) {
            Branch branch = branches.findForUpdate(id).filter(Branch::isActive).orElseThrow(() -> conflict("Şube/depo bulunamadı veya arşivlenmiş."));
            locations.put(id, branch);
        }
        String label = blank(request.label());
        if (request.typeId() != null) {
            var type = types.findById(request.typeId()).filter(StockOperationType::isActive)
                    .orElseThrow(() -> conflict("Seçilen hareket adı artık kullanılamıyor."));
            if (type.getKind() != request.kind()) throw conflict("Hareket adı ile temel hareket türü uyuşmuyor.");
            label = type.getName();
        }
        var kind = request.kind();
        StockOperation operation = new StockOperation(request.requestId(), fingerprint, actor.getId(), actor.getUsername(), kind,
                request.sourceBranchId(), request.targetBranchId(), kind == StockOperation.Kind.IN ? null : locationName(request.sourceBranchId(), locations),
                kind == StockOperation.Kind.OUT ? null : locationName(request.targetBranchId(), locations), request.typeId(), label, blank(request.note()));
        em.persist(operation); em.flush();

        Map<Long,Product> catalog = new HashMap<>();
        Map<String,Stock> centralLines = new HashMap<>();
        Map<String,BranchStock> branchLines = new HashMap<>();
        SortedSet<Long> stockLocations = new TreeSet<>();
        if (kind != StockOperation.Kind.IN) stockLocations.add(request.sourceBranchId() == null ? 0L : request.sourceBranchId());
        if (kind != StockOperation.Kind.OUT) stockLocations.add(request.targetBranchId() == null ? 0L : request.targetBranchId());
        // Lock all affected stock rows before changing any product total. This matches order-product ordering.
        for (Long productId : quantities.keySet()) {
            Product product = products.findById(productId).filter(Product::isActive).orElseThrow(() -> conflict("Ürün bulunamadı veya arşivlenmiş."));
            catalog.put(productId, product);
            for (Long location : stockLocations) {
                if (location == 0L) {
                    Stock line = central.findByProductIdForUpdate(productId).orElseGet(() -> central.saveAndFlush(new Stock(productId, 0)));
                    centralLines.put(key(productId, location), line);
                } else {
                    BranchStock line = branchStock.findForUpdate(location, productId).orElseGet(() ->
                            branchStock.saveAndFlush(new BranchStock(locations.get(location), product, 0)));
                    branchLines.put(key(productId, location), line);
                }
            }
        }
        for (var item : quantities.entrySet()) {
            Long productId = item.getKey(); int amount = item.getValue(); Product product = catalog.get(productId);
            Integer sourceRemaining = null, targetRemaining = null;
            if (kind != StockOperation.Kind.IN) sourceRemaining = change(productId, request.sourceBranchId(), -amount, actor, operation,
                    centralLines, branchLines);
            if (kind != StockOperation.Kind.OUT) targetRemaining = change(productId, request.targetBranchId(), amount, actor, operation,
                    centralLines, branchLines);
            long delta = kind == StockOperation.Kind.TRANSFER ? 0 : kind == StockOperation.Kind.IN ? amount : -amount;
            if (products.adjustQuantity(productId, delta) != 1) throw conflict("Ürün veya toplam stok başka bir işlemde değişmiş.");
            operation.getLines().add(new StockOperationLine(productId, product.getName(), product.getSku(), amount, sourceRemaining, targetRemaining));
        }
        em.flush();
        return operation.getId();
    }

    private int change(Long productId, Long branchId, int delta, User actor, StockOperation operation,
                       Map<String,Stock> centralLines, Map<String,BranchStock> branchLines) {
        if (branchId == null) {
            Stock line = centralLines.get(key(productId, 0L)); int previous = line.getQuantity();
            if (delta < 0 && line.getAvailableQuantity() < -(long)delta) throw conflict("Merkezde satılabilir stok yetersiz.");
            int next = quantity(previous, delta); line.setQuantity(next);
            InventoryTransaction history = new InventoryTransaction(productId,
                    delta > 0 ? InventoryTransactionType.STOCK_IN : InventoryTransactionType.STOCK_OUT, delta, previous, next, operation.getNote());
            history.setActor(actor.getUsername()); history.setReason(operation.getLabel()); history.setOperationId(operation.getId());
            centralHistory.save(history); return next;
        }
        BranchStock line = branchLines.get(key(productId, branchId)); int previous = line.getQuantity();
        int next = quantity(previous, delta); line.setQuantity(next);
        BranchStockMovement history = new BranchStockMovement(line, actor, previous, null);
        history.describe(operation.getLabel(), operation.getNote()); history.setOperationId(operation.getId());
        branchHistory.save(history); return next;
    }

    private static int quantity(int previous, int delta) {
        long next = (long) previous + delta;
        if (next < 0) throw conflict("Kaynak stok yetersiz. İşlemin hiçbir satırı kaydedilmedi.");
        if (next > Integer.MAX_VALUE) throw conflict("Stok adedi desteklenen sınırı aşıyor.");
        return (int) next;
    }
    private static String key(Long product, Long location) { return product + ":" + location; }
    private static String locationName(Long id, Map<Long,Branch> locations) { return id == null ? "Merkez" : locations.get(id).getName(); }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.strip(); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
