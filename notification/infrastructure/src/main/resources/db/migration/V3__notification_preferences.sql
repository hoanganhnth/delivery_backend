CREATE TABLE IF NOT EXISTS notification_preferences (
    principal_id BIGINT PRIMARY KEY,
    marketing_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL
);
