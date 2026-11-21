package com.denizcan.stockorderpayment.web.product;

import com.denizcan.stockorderpayment.service.product.ProductService;
import com.denizcan.stockorderpayment.web.product.dto.CreateProductRequest;
import com.denizcan.stockorderpayment.web.product.dto.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.denizcan.stockorderpayment.web.product.dto.ProductTagRequest;
import com.denizcan.stockorderpayment.web.product.dto.UpdateProductRequest;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @org.springframework.security.access.prepost.PreAuthorize("#request.quantity() == null or #request.quantity() == 0 or hasAnyAuthority('ROLE_ADMIN','STOCK_MANAGE')")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @GetMapping
    public List<ProductResponse> findAll(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return productService.findAll().stream().filter(p -> includeArchived || p.active()).toList();
    }

    @GetMapping("/tags")
    public List<ProductTagRequest> tags() { return productService.tagSuggestions(); }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @PostMapping("/{id}/restore")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority('ROLE_ADMIN','PRODUCTS_EDIT')")
    public ProductResponse restore(@PathVariable Long id) { return productService.restore(id); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable Long id) { productService.archive(id); }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }
}
