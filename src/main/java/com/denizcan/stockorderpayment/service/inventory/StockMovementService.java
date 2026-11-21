package com.denizcan.stockorderpayment.service.inventory;

import com.denizcan.stockorderpayment.domain.inventory.InventoryTransaction;
import com.denizcan.stockorderpayment.domain.inventory.InventoryTransactionType;
import com.denizcan.stockorderpayment.domain.branch.BranchStockMovement;
import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.domain.user.*;
import com.denizcan.stockorderpayment.repository.branch.*;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.stock.StockRepository;
import com.denizcan.stockorderpayment.repository.inventory.InventoryTransactionRepository;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import com.denizcan.stockorderpayment.service.branch.BranchInventoryService;
import com.denizcan.stockorderpayment.service.stock.StockService;
import com.denizcan.stockorderpayment.web.inventory.dto.StockMovementRequest;
import com.denizcan.stockorderpayment.web.stock.dto.StockQuantityRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StockMovementService {
    private final UserRepository users;
    private final ProductRepository products;
    private final StockRepository stock;
    private final StockService central;
    private final BranchInventoryService branchInventory;
    private final BranchRepository branches;
    private final BranchStockMovementRepository branchMovements;
    private final InventoryTransactionRepository centralMovements;

    public enum Direction { IN, OUT }

    @Transactional(readOnly = true)
    public List<ProductChoice> products(String username) {
        user(username);
        return products.findAll().stream().filter(Product::isActive)
                .map(p -> new ProductChoice(p.getId(), p.getName(), p.getSku())).toList();
    }

    @Transactional
    public void create(String username, Long productId, Long branchId, Direction direction,
                       int quantity, String reason, String note) {
        User user = user(username);
        Set<Permission> permissions = Permission.forUser(user);
        if (user.getRole() == Role.BRANCH_EMPLOYEE) {
            if (!permissions.contains(Permission.BRANCH_STOCK_MANAGE) || user.getBranch() == null
                    || !user.getBranch().getId().equals(branchId)) throw denied();
        } else if (!permissions.contains(Permission.STOCK_MANAGE)) throw denied();
        Product product = products.findById(productId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı."));
        if (!product.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Arşivlenmiş ürün kullanılamaz.");
        if (branchId != null) {
            branchInventory.move(branchId, productId, quantity, direction == Direction.IN, blank(reason), blank(note), username);
        } else {
            if (!stock.existsByProductId(productId)) {
                if (direction == Direction.OUT) throw new ResponseStatusException(HttpStatus.CONFLICT, "Merkez stoğu yetersiz.");
                central.create(productId, new StockQuantityRequest(0));
            }
            var request = new StockMovementRequest(quantity, blank(note), blank(reason));
            if (direction == Direction.IN) central.stockIn(productId, request);
            else central.stockOut(productId, request);
        }
    }

    @Transactional(readOnly = true)
    public List<Location> locations(String username) {
        User user = user(username);
        if (user.getRole() == Role.BRANCH_EMPLOYEE) {
            if (user.getBranch() == null) throw denied();
            return List.of(new Location(user.getBranch().getId(), user.getBranch().getName()));
        }
        List<Location> locations = new ArrayList<>();
        locations.add(new Location(null, "Merkez"));
        branches.findAllByOrderByNameAsc().forEach(b -> locations.add(new Location(b.getId(), b.getName())));
        return locations;
    }

    @Transactional(readOnly = true)
    public List<Movement> list(String username) {
        User user = user(username);
        List<Movement> result = new ArrayList<>();
        if (user.getRole() != Role.BRANCH_EMPLOYEE) {
            var centralLines = centralMovements.findTop100ByOrderByCreatedAtDescIdDesc();
            Map<Long, Product> catalog = new HashMap<>();
            products.findAllById(centralLines.stream().map(InventoryTransaction::getProductId).toList())
                    .forEach(p -> catalog.put(p.getId(), p));
            for (var m : centralLines) {
                if (m.getType() == InventoryTransactionType.RESERVATION || m.getType() == InventoryTransactionType.RELEASE
                        || m.getQuantityChange() == 0) continue;
                Product p = catalog.get(m.getProductId());
                result.add(new Movement("central-" + m.getId(), m.getProductId(), p == null ? "—" : p.getName(),
                        p == null ? "—" : p.getSku(), null, "Merkez", m.getQuantityChange() > 0 ? Direction.IN : Direction.OUT,
                        Math.abs((long)m.getQuantityChange()), m.getNewQuantity(),
                        m.getReason() != null ? m.getReason() : m.getType() == InventoryTransactionType.CONSUME ? "Satış" : null,
                        m.getNote(), m.getActor() == null ? "Sistem" : m.getActor(), m.getCreatedAt()));
            }
        }
        var branchLines = user.getRole() == Role.BRANCH_EMPLOYEE
                ? branchMovements.findTop100ByStockBranchIdOrderByCreatedAtDescIdDesc(user.getBranch().getId())
                : branchMovements.findTop100ByOrderByCreatedAtDescIdDesc();
        for (BranchStockMovement m : branchLines) {
            if (m.getChange() == 0) continue;
            var s = m.getStock();
            result.add(new Movement("branch-" + m.getId(), s.getProduct().getId(), s.getProduct().getName(),
                    s.getProduct().getSku(), s.getBranch().getId(), s.getBranch().getName(),
                    m.getChange() > 0 ? Direction.IN : Direction.OUT, Math.abs((long)m.getChange()), m.getNewQuantity(),
                    m.getSaleId() != null ? "Satış" : m.getReason(), m.getNote(), m.getActor().getUsername(), m.getCreatedAt()));
        }
        return result.stream().sorted(Comparator.comparing(Movement::createdAt).reversed().thenComparing(Movement::id))
                .limit(100).toList();
    }

    private User user(String username) {
        return users.findByUsername(username).filter(User::isEnabled).orElseThrow(StockMovementService::denied);
    }
    private static String blank(String text) { return text == null || text.isBlank() ? null : text.strip(); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Bu stok için işlem yetkiniz yok."); }

    public record Location(Long branchId, String name) {}
    public record ProductChoice(Long id, String name, String sku) {}
    public record Movement(String id, Long productId, String productName, String sku, Long branchId,
                           String locationName, Direction direction, long quantity, int remainingQuantity,
                           String reason, String note, String username, Instant createdAt) {}
}
