CREATE TABLE branch_stock (
    id BIGSERIAL PRIMARY KEY,
    branch_id BIGINT NOT NULL REFERENCES branches (id),
    product_id BIGINT NOT NULL REFERENCES products (id),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    CONSTRAINT uk_branch_stock_product UNIQUE (branch_id, product_id)
);

CREATE TABLE branch_stock_movements (
    id BIGSERIAL PRIMARY KEY,
    stock_id BIGINT NOT NULL REFERENCES branch_stock (id),
    actor_id BIGINT NOT NULL REFERENCES users (id),
    previous_quantity INTEGER NOT NULL CHECK (previous_quantity >= 0),
    new_quantity INTEGER NOT NULL CHECK (new_quantity >= 0),
    sale_id UUID UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_branch_stock_movements_recent ON branch_stock_movements (created_at DESC, id DESC);
