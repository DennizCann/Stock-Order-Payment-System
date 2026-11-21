package com.denizcan.stockorderpayment.service.product;

import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.exception.DuplicateSkuException;
import com.denizcan.stockorderpayment.exception.NotFoundException;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.web.product.dto.CreateProductRequest;
import com.denizcan.stockorderpayment.web.product.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.denizcan.stockorderpayment.domain.product.ProductTag;
import com.denizcan.stockorderpayment.service.stock.StockService;
import com.denizcan.stockorderpayment.web.stock.dto.StockQuantityRequest;
import com.denizcan.stockorderpayment.web.product.dto.ProductTagRequest;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final StockService stockService;
    private final EntityManager entityManager;

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }

        Product product = new Product(request.name().strip(), request.sku().strip(), request.price());
        product.replaceTags(tags(request.tags()));
        Product saved = productRepository.save(product);
        if (request.quantity() != null) {
            productRepository.flush();
            stockService.create(saved.getId(), new StockQuantityRequest(request.quantity()));
            entityManager.refresh(saved);
        }
        log.info("Created product sku={}", saved.getSku());
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> NotFoundException.product(id));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse update(Long id, CreateProductRequest request) {
        Product product = productRepository.findById(id).orElseThrow(() -> NotFoundException.product(id));
        if (!product.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Arşivlenmiş ürün düzenlenemez.");
        if (productRepository.existsBySkuAndIdNot(request.sku().strip(), id)) throw new DuplicateSkuException(request.sku());
        if (request.quantity() != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Adedi değiştirmek için stok hareketi oluştur.");
        product.revise(request.name(), request.sku(), request.price());
        product.replaceTags(tags(request.tags()));
        return ProductResponse.from(product);
    }

    @Transactional
    public void archive(Long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> NotFoundException.product(id));
        if (!product.isActive()) return;
        if (productRepository.archiveEmpty(id) != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ürünü kaldırmadan önce merkez ve şubelerdeki stoğunu sıfırla. Geçmiş hareketler korunur.");
    }

    @Transactional(readOnly = true)
    public List<ProductTagRequest> tagSuggestions() {
        return productRepository.tagSuggestions().stream()
                .map(t -> new ProductTagRequest((String) t[0], (String) t[1])).toList();
    }

    private List<ProductTag> tags(List<ProductTagRequest> tags) {
        if (tags == null) return List.of();
        var unique = new java.util.LinkedHashMap<List<String>, ProductTag>();
        for (var t : tags) {
            var tag = new ProductTag(t.name(), t.value());
            unique.put(List.of(tag.getName(), tag.getValue()), tag);
        }
        return List.copyOf(unique.values());
    }
}
