CREATE TABLE branches (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_branches_name UNIQUE (name)
);

ALTER TABLE users ADD COLUMN branch_id BIGINT;
ALTER TABLE users ADD CONSTRAINT fk_users_branch FOREIGN KEY (branch_id) REFERENCES branches (id);
ALTER TABLE users ADD CONSTRAINT ck_users_branch_role CHECK (
    (role = 'BRANCH_EMPLOYEE' AND branch_id IS NOT NULL)
    OR (role <> 'BRANCH_EMPLOYEE' AND branch_id IS NULL)
);
