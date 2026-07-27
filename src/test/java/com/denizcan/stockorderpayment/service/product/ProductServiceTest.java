package com.denizcan.stockorderpayment.service.product;

import com.denizcan.stockorderpayment.domain.product.Product;
import com.denizcan.stockorderpayment.exception.DuplicateSkuException;
import com.denizcan.stockorderpayment.exception.ProductNotFoundException;
import com.denizcan.stockorderpayment.repository.product.ProductRepository;
import com.denizcan.stockorderpayment.web.product.dto.CreateProductRequest;
import com.denizcan.stockorderpayment.web.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void create_shouldPersistProduct_whenSkuIsUnique() {
        CreateProductRequest request = new CreateProductRequest("Laptop", "LAP-001", new BigDecimal("1000.00"));
        when(productRepository.existsBySku("LAP-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            return product;
        });

        ProductResponse response = productService.create(request);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSku()).isEqualTo("LAP-001");
        assertThat(response.name()).isEqualTo("Laptop");
        assertThat(response.sku()).isEqualTo("LAP-001");
    }

    @Test
    void create_shouldThrow_whenSkuAlreadyExists() {
        CreateProductRequest request = new CreateProductRequest("Laptop", "LAP-001", new BigDecimal("1000.00"));
        when(productRepository.existsBySku("LAP-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateSkuException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void findById_shouldReturnProduct() {
        Product product = new Product("Laptop", "LAP-001", new BigDecimal("1000.00"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.findById(1L);

        assertThat(response.sku()).isEqualTo("LAP-001");
    }

    @Test
    void findById_shouldThrow_whenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void findAll_shouldMapEntitiesToResponses() {
        when(productRepository.findAll()).thenReturn(List.of(
                new Product("A", "SKU-A", new BigDecimal("10.00")),
                new Product("B", "SKU-B", new BigDecimal("20.00"))
        ));

        List<ProductResponse> responses = productService.findAll();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).sku()).isEqualTo("SKU-A");
    }
}
