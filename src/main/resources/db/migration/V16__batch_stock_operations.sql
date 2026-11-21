CREATE TABLE stock_operation_types (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    name_key VARCHAR(200) NOT NULL UNIQUE,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('IN', 'OUT', 'TRANSFER')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE stock_operations (
    id UUID PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    actor_id BIGINT NOT NULL REFERENCES users(id),
    username VARCHAR(64) NOT NULL,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('IN', 'OUT', 'TRANSFER')),
    source_branch_id BIGINT REFERENCES branches(id),
    target_branch_id BIGINT REFERENCES branches(id),
    source_name VARCHAR(100),
    target_name VARCHAR(100),
    type_id BIGINT REFERENCES stock_operation_types(id),
    label VARCHAR(100),
    note VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_stock_operations_recent ON stock_operations(created_at DESC, id DESC);
CREATE INDEX idx_stock_operations_source ON stock_operations(source_branch_id, created_at DESC);
CREATE INDEX idx_stock_operations_target ON stock_operations(target_branch_id, created_at DESC);
CREATE TABLE stock_operation_lines (
    id BIGSERIAL PRIMARY KEY,
    operation_id UUID NOT NULL REFERENCES stock_operations(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    product_name VARCHAR(255) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    source_remaining INTEGER CHECK (source_remaining >= 0),
    target_remaining INTEGER CHECK (target_remaining >= 0),
    CONSTRAINT uk_stock_operation_product UNIQUE(operation_id, product_id)
);
CREATE INDEX idx_stock_operation_lines_parent ON stock_operation_lines(operation_id);
ALTER TABLE inventory_transactions ADD COLUMN operation_id UUID REFERENCES stock_operations(id);
ALTER TABLE branch_stock_movements ADD COLUMN operation_id UUID REFERENCES stock_operations(id);
