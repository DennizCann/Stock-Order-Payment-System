package com.denizcan.stockorderpayment.web.order.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validRequestPasses() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                List.of(new CreateOrderItemRequest(10L, 2))
        );

        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsMissingProductId() {
        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                List.of(new CreateOrderItemRequest(null, 2))
        );

        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("Product id"));
    }

    @Test
    void rejectsQuantityZeroOrNegative() {
        CreateOrderRequest zero = new CreateOrderRequest(
                1L,
                List.of(new CreateOrderItemRequest(10L, 0))
        );
        CreateOrderRequest negative = new CreateOrderRequest(
                1L,
                List.of(new CreateOrderItemRequest(10L, -1))
        );

        assertThat(validator.validate(zero)).isNotEmpty();
        assertThat(validator.validate(negative)).isNotEmpty();
    }

    @Test
    void rejectsEmptyItems() {
        CreateOrderRequest request = new CreateOrderRequest(1L, List.of());

        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getMessage().contains("at least one item"));
    }
}
