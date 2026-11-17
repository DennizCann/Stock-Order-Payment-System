CREATE TABLE employee_roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    name_key VARCHAR(128) NOT NULL UNIQUE,
    scope VARCHAR(32) NOT NULL CHECK (scope IN ('USER', 'BRANCH_EMPLOYEE'))
);

CREATE TABLE employee_role_permissions (
    role_id BIGINT NOT NULL REFERENCES employee_roles (id),
    permission VARCHAR(40) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

ALTER TABLE users ADD COLUMN employee_role_id BIGINT REFERENCES employee_roles (id);
