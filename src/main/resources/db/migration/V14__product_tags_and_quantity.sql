ALTER TABLE products ADD COLUMN quantity BIGINT NOT NULL DEFAULT 0 CHECK (quantity >= 0);
ALTER TABLE products ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE products SET quantity = COALESCE((SELECT SUM(s.quantity) FROM stock s WHERE s.product_id = products.id), 0)
    + COALESCE((SELECT SUM(b.quantity) FROM branch_stock b WHERE b.product_id = products.id), 0);
CREATE TABLE product_tags (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    name VARCHAR(100) NOT NULL,
    tag_value VARCHAR(255) NOT NULL,
    CONSTRAINT uk_product_tag UNIQUE (product_id, name, tag_value)
);
CREATE INDEX idx_product_tags_filter ON product_tags(name, tag_value, product_id);
ALTER TABLE inventory_transactions ADD COLUMN actor VARCHAR(64);
ALTER TABLE inventory_transactions ADD COLUMN reason VARCHAR(100);
ALTER TABLE branch_stock_movements ADD COLUMN reason VARCHAR(100);
ALTER TABLE branch_stock_movements ADD COLUMN note VARCHAR(255);
