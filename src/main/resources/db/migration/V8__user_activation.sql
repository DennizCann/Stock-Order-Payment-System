ALTER TABLE users ADD COLUMN activation_token_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN activation_expires_at TIMESTAMP;
ALTER TABLE users ADD CONSTRAINT uk_users_activation_token_hash UNIQUE (activation_token_hash);
