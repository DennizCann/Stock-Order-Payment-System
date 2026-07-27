-- Application users for JWT authentication

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(32)  NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_users_username UNIQUE (username)
);
