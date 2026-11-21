ALTER TABLE users DROP CONSTRAINT ck_users_branch_role;
ALTER TABLE users ADD CONSTRAINT ck_users_branch_role CHECK (
    (role = 'ADMIN' AND branch_id IS NULL)
    OR role = 'USER'
    OR (role = 'BRANCH_EMPLOYEE' AND branch_id IS NOT NULL)
);
