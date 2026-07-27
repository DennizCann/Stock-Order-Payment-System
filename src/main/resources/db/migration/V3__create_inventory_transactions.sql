-- Inventory transaction history for stock movements

CREATE TABLE inventory_transactions (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT       NOT NULL,
    type              VARCHAR(32)  NOT NULL,
    quantity_change   INTEGER      NOT NULL,
    previous_quantity INTEGER      NOT NULL,
    new_quantity      INTEGER      NOT NULL,
    note              VARCHAR(255),
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_tx_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_inventory_tx_product_id ON inventory_transactions (product_id);
CREATE INDEX idx_inventory_tx_created_at ON inventory_transactions (created_at);
