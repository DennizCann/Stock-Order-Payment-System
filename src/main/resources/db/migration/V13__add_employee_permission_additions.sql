CREATE TABLE user_permission_additions (
    user_id BIGINT NOT NULL REFERENCES users (id),
    permission VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, permission)
);
