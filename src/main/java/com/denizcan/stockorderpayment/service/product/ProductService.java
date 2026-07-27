package com.denizcan.stockorderpayment.service.product;

import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.exception.DuplicateSkuException;
import com.denizcan.stockorderpayment.exception.ProductNotFoundException;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.web.product.dto.CreateProductRequest;
import com.denizcan.stockorderpayment.web.product.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }

        Product product = new Product(request.name(), request.sku(), request.price());
        Product saved = productRepository.save(product);
        log.info("Created product sku={}", saved.getSku());
        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }
}
