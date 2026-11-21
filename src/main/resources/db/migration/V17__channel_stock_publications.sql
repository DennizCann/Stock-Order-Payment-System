CREATE TABLE channel_stock_policies (
 id BIGSERIAL PRIMARY KEY, source VARCHAR(32) NOT NULL, product_id BIGINT NOT NULL REFERENCES products(id),
 external_code VARCHAR(100) NOT NULL, maximum_quantity INTEGER NOT NULL CHECK(maximum_quantity BETWEEN 0 AND 20000),
 safety_stock INTEGER NOT NULL CHECK(safety_stock >= 0), enabled BOOLEAN NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(source,product_id), UNIQUE(source,external_code)
);
CREATE TABLE stock_publications (
 id UUID PRIMARY KEY, connection_key VARCHAR(64) NOT NULL, fingerprint VARCHAR(64) NOT NULL, active_key VARCHAR(32) UNIQUE,
 username VARCHAR(64) NOT NULL, state VARCHAR(16) NOT NULL, batch_id VARCHAR(160), message VARCHAR(255) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, last_checked_at TIMESTAMP WITH TIME ZONE, next_check_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_stock_publications_recent ON stock_publications(created_at DESC);
CREATE INDEX idx_stock_publications_payload ON stock_publications(connection_key,fingerprint,created_at);
CREATE TABLE stock_publication_lines (
 id BIGSERIAL PRIMARY KEY, publication_id UUID NOT NULL REFERENCES stock_publications(id),
 product_id BIGINT NOT NULL REFERENCES products(id), barcode VARCHAR(100) NOT NULL, quantity INTEGER NOT NULL CHECK(quantity >= 0)
);
CREATE INDEX idx_stock_publication_lines_parent ON stock_publication_lines(publication_id);
