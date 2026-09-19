ALTER TABLE users ADD COLUMN keycloak_subject VARCHAR(255);
UPDATE users SET keycloak_subject = id::text WHERE keycloak_subject IS NULL;
ALTER TABLE users ALTER COLUMN keycloak_subject SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uk_users_keycloak_subject UNIQUE (keycloak_subject);
ALTER TABLE users DROP COLUMN password_hash;
