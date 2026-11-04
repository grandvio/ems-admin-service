CREATE TABLE ems_energy_source (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    series_code VARCHAR(32) NOT NULL,
    device_id BIGINT NOT NULL,
    metric_name VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_energy_source_metric UNIQUE (device_id, metric_name)
);

CREATE INDEX idx_energy_source_station ON ems_energy_source (station_id, series_code);

CREATE TABLE ems_metric_energy_hour (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    metric_name VARCHAR(128) NOT NULL,
    hour_start DATETIME NOT NULL,
    kwh DECIMAL(18, 6) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_metric_energy_hour UNIQUE (device_id, metric_name, hour_start)
);

CREATE INDEX idx_metric_energy_station ON ems_metric_energy_hour (station_id, hour_start);

INSERT INTO ems_energy_source (tenant_id, station_id, series_code, device_id, metric_name, created_at, updated_at, deleted)
SELECT m.tenant_id, d.station_id,
       CASE m.role
           WHEN 'GRID' THEN 'importEnergy'
           WHEN 'PV' THEN 'generation'
           WHEN 'LOAD' THEN 'consumption'
           WHEN 'STORAGE' THEN 'charge'
       END,
       m.device_id,
       CASE WHEN m.swap_direction = 1 THEN 'Meter/Epe' ELSE 'Meter/Epi' END,
       m.created_at, m.updated_at, 0
FROM ems_device_meter m
         JOIN ems_device d ON d.id = m.device_id AND d.deleted = 0
WHERE m.deleted = 0
  AND m.role IN ('GRID', 'PV', 'LOAD', 'STORAGE');

INSERT INTO ems_energy_source (tenant_id, station_id, series_code, device_id, metric_name, created_at, updated_at, deleted)
SELECT m.tenant_id, d.station_id,
       CASE m.role
           WHEN 'GRID' THEN 'exportEnergy'
           WHEN 'STORAGE' THEN 'discharge'
       END,
       m.device_id,
       CASE WHEN m.swap_direction = 1 THEN 'Meter/Epi' ELSE 'Meter/Epe' END,
       m.created_at, m.updated_at, 0
FROM ems_device_meter m
         JOIN ems_device d ON d.id = m.device_id AND d.deleted = 0
WHERE m.deleted = 0
  AND m.role IN ('GRID', 'STORAGE');

INSERT INTO ems_metric_energy_hour (tenant_id, station_id, device_id, metric_name, hour_start, kwh, created_at, updated_at, deleted)
SELECT tenant_id, station_id, device_id, 'Meter/Epi', hour_start, epi_kwh, created_at, updated_at, 0
FROM ems_meter_energy_hour
WHERE deleted = 0;

INSERT INTO ems_metric_energy_hour (tenant_id, station_id, device_id, metric_name, hour_start, kwh, created_at, updated_at, deleted)
SELECT tenant_id, station_id, device_id, 'Meter/Epe', hour_start, epe_kwh, created_at, updated_at, 0
FROM ems_meter_energy_hour
WHERE deleted = 0;

DROP TABLE ems_meter_energy_hour;
DROP TABLE ems_device_meter;
