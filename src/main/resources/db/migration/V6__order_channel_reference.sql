ALTER TABLE orders ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'INTERNAL';
ALTER TABLE orders ADD COLUMN external_order_id VARCHAR(128);
ALTER TABLE orders ADD COLUMN external_payload_hash VARCHAR(64);
ALTER TABLE orders ADD CONSTRAINT uk_order_source_external_id UNIQUE (source, external_order_id);
