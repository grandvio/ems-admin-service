CREATE TABLE ems_device_meter (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    swap_direction TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_device_meter_device UNIQUE (device_id)
);

CREATE TABLE ems_energy_checkpoint (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    metric_name VARCHAR(128) NOT NULL,
    last_epoch_ms BIGINT NOT NULL,
    register_value DECIMAL(20, 6) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_energy_checkpoint UNIQUE (device_id, metric_name)
);

CREATE TABLE ems_meter_energy_hour (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    hour_start DATETIME NOT NULL,
    epi_kwh DECIMAL(18, 6) NOT NULL DEFAULT 0,
    epe_kwh DECIMAL(18, 6) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_meter_energy_hour UNIQUE (device_id, hour_start)
);

CREATE INDEX idx_meter_energy_station ON ems_meter_energy_hour (station_id, hour_start);
