-- Deleting an organisation removes everything owned by it: signing keys,
-- roles (+ their permissions), org users (+ their roles and refresh tokens).
-- The FKs were originally created without ON DELETE, so drop and re-add them.

ALTER TABLE organisation_signing_keys DROP CONSTRAINT fk_org_signing_keys_org;
ALTER TABLE organisation_signing_keys
    ADD CONSTRAINT fk_org_signing_keys_org FOREIGN KEY (organisation_id)
        REFERENCES organisations (id) ON DELETE CASCADE;

ALTER TABLE organisation_roles DROP CONSTRAINT fk_organisation_roles_org;
ALTER TABLE organisation_roles
    ADD CONSTRAINT fk_organisation_roles_org FOREIGN KEY (organisation_id)
        REFERENCES organisations (id) ON DELETE CASCADE;

ALTER TABLE organisation_role_permissions DROP CONSTRAINT fk_org_role_permissions_role;
ALTER TABLE organisation_role_permissions
    ADD CONSTRAINT fk_org_role_permissions_role FOREIGN KEY (organisation_role_id)
        REFERENCES organisation_roles (id) ON DELETE CASCADE;

ALTER TABLE organisation_users DROP CONSTRAINT fk_organisation_users_org;
ALTER TABLE organisation_users
    ADD CONSTRAINT fk_organisation_users_org FOREIGN KEY (organisation_id)
        REFERENCES organisations (id) ON DELETE CASCADE;

ALTER TABLE organisation_user_roles DROP CONSTRAINT fk_org_user_roles_user;
ALTER TABLE organisation_user_roles
    ADD CONSTRAINT fk_org_user_roles_user FOREIGN KEY (organisation_user_id)
        REFERENCES organisation_users (id) ON DELETE CASCADE;

ALTER TABLE organisation_user_roles DROP CONSTRAINT fk_org_user_roles_role;
ALTER TABLE organisation_user_roles
    ADD CONSTRAINT fk_org_user_roles_role FOREIGN KEY (organisation_role_id)
        REFERENCES organisation_roles (id) ON DELETE CASCADE;

ALTER TABLE organisation_refresh_tokens DROP CONSTRAINT fk_org_refresh_tokens_user;
ALTER TABLE organisation_refresh_tokens
    ADD CONSTRAINT fk_org_refresh_tokens_user FOREIGN KEY (organisation_user_id)
        REFERENCES organisation_users (id) ON DELETE CASCADE;
