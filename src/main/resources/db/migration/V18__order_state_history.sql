CREATE TABLE order_state_changes (
 id BIGSERIAL PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES orders(id), previous_status VARCHAR(32) NOT NULL,
 new_status VARCHAR(32) NOT NULL, username VARCHAR(64) NOT NULL, note VARCHAR(255), created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_order_state_changes_order ON order_state_changes(order_id,created_at DESC,id DESC);
