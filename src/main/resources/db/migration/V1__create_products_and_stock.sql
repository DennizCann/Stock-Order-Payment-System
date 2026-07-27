-- Products and stock tables (initial schema)

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    sku         VARCHAR(64)  NOT NULL,
    price       NUMERIC(19, 2) NOT NULL,
    CONSTRAINT uk_products_sku UNIQUE (sku)
);

CREATE TABLE stock (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL,
    quantity    INTEGER NOT NULL,
    CONSTRAINT uk_stock_product_id UNIQUE (product_id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_stock_product_id ON stock (product_id);
