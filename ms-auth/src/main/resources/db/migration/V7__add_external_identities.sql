-- Identidades externas vinculadas a los usuarios internos de ClassFlow.
-- El users.id se conserva como identificador de negocio para mantener sus relaciones.
CREATE TABLE IF NOT EXISTS user_identities (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    external_subject VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_identities_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_identities_provider_subject
        UNIQUE (provider, tenant_id, external_subject)
);

CREATE INDEX idx_user_identities_user_id ON user_identities(user_id);
CREATE INDEX idx_user_identities_tenant_subject ON user_identities(tenant_id, external_subject);