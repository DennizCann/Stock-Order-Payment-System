package com.denizcan.stockorderpayment.service.branch;

import com.denizcan.stockorderpayment.domain.branch.Branch;
import com.denizcan.stockorderpayment.domain.branch.BranchStock;
import com.denizcan.stockorderpayment.domain.branch.BranchStockMovement;
import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.domain.user.Role;
import com.denizcan.stockorderpayment.domain.user.User;
import com.denizcan.stockorderpayment.repository.branch.BranchRepository;
import com.denizcan.stockorderpayment.repository.branch.BranchStockMovementRepository;
import com.denizcan.stockorderpayment.repository.branch.BranchStockRepository;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchInventoryService {

    private final BranchRepository branches;
    private final ProductRepository products;
    private final UserRepository users;
    private final BranchStockRepository stock;
    private final BranchStockMovementRepository movements;

    @Transactional(readOnly = true)
    public List<StockLine> stockAt(Long branchId) {
        if (!branches.existsById(branchId)) {
            throw branchNotFound();
        }
        return stockLines(branchId);
    }

    @Transactional(readOnly = true)
    public List<StockLine> employeeStock(String username) {
        return stockLines(employee(username).getBranch().getId());
    }

    @Transactional
    public StockLine setQuantity(Long branchId, Long productId, int quantity, String username) {
        User actor = activeUser(username);
        // Lock the branch even when this product has no stock row yet.
        Branch branch = branches.findForUpdate(branchId).orElseThrow(BranchInventoryService::branchNotFound);
        BranchStock line = stock.findForUpdate(branchId, productId)
                .orElseGet(() -> new BranchStock(branch, products.findById(productId).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı.")), 0));

        int previousQuantity = line.getQuantity();
        if (line.getId() == null || previousQuantity != quantity) {
            line.setQuantity(quantity);
            stock.save(line);
            movements.save(new BranchStockMovement(line, actor, previousQuantity, null));
        }
        return StockLine.from(line);
    }

    @Transactional
    public Movement recordSale(String username, Long productId, int quantity, UUID saleId) {
        User actor = employee(username);
        BranchStock line = stock.findForUpdate(actor.getBranch().getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bu ürün şubenizin stok listesinde yok."));
        BranchStockMovement existing = movements.findBySaleId(saleId).orElse(null);
        if (existing != null) {
            if (!existing.getStock().getId().equals(line.getId())
                    || !existing.getActor().getId().equals(actor.getId()) || existing.getChange() != -quantity) {
                throw saleConflict();
            }
            return Movement.from(existing);
        }

        int previousQuantity = line.getQuantity();
        if (quantity > previousQuantity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Şube stoğu yetersiz. Mevcut adet: " + previousQuantity + ".");
        }
        line.setQuantity(previousQuantity - quantity);
        try {
            return Movement.from(movements.saveAndFlush(new BranchStockMovement(line, actor, previousQuantity, saleId)));
        } catch (DataIntegrityViolationException exception) {
            throw saleConflict();
        }
    }

    @Transactional(readOnly = true)
    public List<Movement> recentMovements() {
        return movements.findTop100ByOrderByCreatedAtDescIdDesc().stream().map(Movement::from).toList();
    }

    private List<StockLine> stockLines(Long branchId) {
        return stock.findAllByBranchIdOrderByProductNameAsc(branchId).stream().map(StockLine::from).toList();
    }

    private User activeUser(String username) {
        return users.findByUsername(username).filter(User::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Hesap kullanılamıyor."));
    }

    private User employee(String username) {
        User actor = activeUser(username);
        if (actor.getRole() != Role.BRANCH_EMPLOYEE || actor.getBranch() == null) {
            throw new AccessDeniedException("Şubeye atanmış bir çalışan hesabı gerekiyor.");
        }
        return actor;
    }

    private static ResponseStatusException branchNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Şube bulunamadı.");
    }

    private static ResponseStatusException saleConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Bu satış numarası başka bir işlemde kullanılmış.");
    }

    public record StockLine(Long productId, String productName, String sku, int quantity) {
        static StockLine from(BranchStock stock) {
            Product product = stock.getProduct();
            return new StockLine(product.getId(), product.getName(), product.getSku(), stock.getQuantity());
        }
    }

    public record Movement(Long id, String branchName, String productName, String sku, String username,
                           String type, int change, int previousQuantity, int newQuantity, Instant createdAt) {
        static Movement from(BranchStockMovement movement) {
            BranchStock stock = movement.getStock();
            Product product = stock.getProduct();
            return new Movement(movement.getId(), stock.getBranch().getName(), product.getName(),
                    product.getSku(), movement.getActor().getUsername(), movement.getSaleId() == null ? "ADJUSTMENT" : "SALE",
                    movement.getChange(), movement.getPreviousQuantity(), movement.getNewQuantity(), movement.getCreatedAt());
        }
    }
}
