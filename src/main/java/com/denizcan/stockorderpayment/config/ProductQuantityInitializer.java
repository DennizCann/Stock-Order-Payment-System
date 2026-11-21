package com.denizcan.stockorderpayment.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("demo")
public class ProductQuantityInitializer {
    @Bean
    ApplicationRunner reconcileProductQuantities(JdbcTemplate jdbc) {
        return args -> jdbc.update("update products set quantity = coalesce((select sum(s.quantity) from stock s where s.product_id = products.id), 0) + coalesce((select sum(b.quantity) from branch_stock b where b.product_id = products.id), 0)");
    }
}
