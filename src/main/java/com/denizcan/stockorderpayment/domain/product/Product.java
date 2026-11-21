package com.denizcan.stockorderpayment.domain.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinColumn;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @org.hibernate.annotations.ColumnDefault("0")
    @Column(nullable = false)
    private long quantity;

    @org.hibernate.annotations.ColumnDefault("true")
    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "product_id", nullable = false)
    private List<ProductTag> tags = new ArrayList<>();

    public void revise(String name, String sku, BigDecimal price) {
        this.name = name.strip();
        this.sku = sku.strip();
        this.price = price;
    }

    public void replaceTags(List<ProductTag> replacement) {
        tags.removeIf(old -> replacement.stream().noneMatch(t ->
                t.getName().equals(old.getName()) && t.getValue().equals(old.getValue())));
        for (ProductTag next : replacement) {
            if (tags.stream().noneMatch(t -> t.getName().equals(next.getName()) && t.getValue().equals(next.getValue())))
                tags.add(next);
        }
    }

    public Product(String name, String sku, BigDecimal price) {
        this.name = name;
        this.sku = sku;
        this.price = price;
    }
}
