ALTER TABLE sys_user ADD COLUMN avatar_key VARCHAR(255) NULL;

CREATE TABLE ems_station_image (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    cover TINYINT NOT NULL DEFAULT 0,
    sort_num INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_station_image_key UNIQUE (object_key)
);

CREATE INDEX idx_station_image_station ON ems_station_image (station_id);

CREATE TABLE oss_upload_ticket (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    biz_type VARCHAR(16) NOT NULL,
    biz_id BIGINT NULL,
    object_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    max_bytes BIGINT NOT NULL,
    expire_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_upload_ticket_key UNIQUE (object_key)
);

CREATE INDEX idx_upload_ticket_user ON oss_upload_ticket (user_id, status);
