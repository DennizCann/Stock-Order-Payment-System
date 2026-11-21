package com.denizcan.stockorderpayment.domain.product;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_tags", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "name", "tag_value"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductTag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(name = "tag_value", nullable = false, length = 255)
    private String value;

    public ProductTag(String name, String value) {
        this.name = name.strip();
        this.value = value.strip();
    }
}
