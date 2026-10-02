CREATE TABLE web_sessions (
    session_hash VARCHAR(64) PRIMARY KEY,
    access_token_cipher TEXT NOT NULL,
    refresh_token_cipher TEXT NOT NULL,
    encryption_key_version VARCHAR(64) NOT NULL,
    principal_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    csrf_hash VARCHAR(64) NOT NULL,
    generation BIGINT NOT NULL DEFAULT 1,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    refresh_claimed_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_web_sessions_active_expiry ON web_sessions (expires_at) WHERE revoked_at IS NULL;
