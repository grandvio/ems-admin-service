CREATE TABLE ems_price_location (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    market VARCHAR(8) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    iso_code VARCHAR(16) NOT NULL,
    external_ref VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_price_location_code UNIQUE (code)
);

CREATE TABLE ems_market_price (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    location_id BIGINT NOT NULL,
    product VARCHAR(16) NOT NULL,
    interval_start DATETIME NOT NULL,
    interval_minutes INT NOT NULL,
    price_per_mwh DECIMAL(18, 6) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_market_price UNIQUE (location_id, product, interval_start)
);

CREATE INDEX idx_market_price_location ON ems_market_price (location_id, interval_start);

CREATE TABLE ems_station_tariff (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    buy_mode VARCHAR(16) NOT NULL,
    buy_fixed_price DECIMAL(18, 6) NULL,
    buy_location_id BIGINT NULL,
    buy_adder DECIMAL(18, 6) NOT NULL DEFAULT 0,
    sell_mode VARCHAR(16) NOT NULL,
    sell_fixed_price DECIMAL(18, 6) NULL,
    sell_location_id BIGINT NULL,
    sell_adder DECIMAL(18, 6) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_station_tariff_station UNIQUE (station_id)
);

INSERT INTO ems_price_location (tenant_id, market, code, name, currency, timezone, iso_code, external_ref) VALUES
    (0, 'EU', 'EU_DE_LU', '德国-卢森堡', 'EUR', 'Europe/Berlin', 'ENTSOE', '10Y1001A1001A82H'),
    (0, 'EU', 'EU_FR', '法国', 'EUR', 'Europe/Paris', 'ENTSOE', '10YFR-RTE------C'),
    (0, 'EU', 'EU_NL', '荷兰', 'EUR', 'Europe/Amsterdam', 'ENTSOE', '10YNL----------L'),
    (0, 'EU', 'EU_BE', '比利时', 'EUR', 'Europe/Brussels', 'ENTSOE', '10YBE----------2'),
    (0, 'EU', 'EU_ES', '西班牙', 'EUR', 'Europe/Madrid', 'ENTSOE', '10YES-REE------0'),
    (0, 'US', 'US_CAISO_NP15', 'CAISO NP15', 'USD', 'America/Los_Angeles', 'CAISO', 'TH_NP15_GEN-APND'),
    (0, 'US', 'US_ERCOT_HB_HOUSTON', 'ERCOT Houston Hub', 'USD', 'America/Chicago', 'ERCOT', 'HB_HOUSTON'),
    (0, 'US', 'US_PJM_WESTERN_HUB', 'PJM Western Hub', 'USD', 'America/New_York', 'PJM', 'WESTERN HUB'),
    (0, 'US', 'US_MISO_ILLINOIS_HUB', 'MISO Illinois Hub', 'USD', 'America/Chicago', 'MISO', 'ILLINOIS.HUB'),
    (0, 'US', 'US_NYISO_NYC', 'NYISO New York City', 'USD', 'America/New_York', 'NYISO', 'N.Y.C.'),
    (0, 'US', 'US_ISONE_MASS_HUB', 'ISO-NE Mass Hub', 'USD', 'America/New_York', 'ISONE', '.H.INTERNAL_HUB'),
    (0, 'US', 'US_SPP_NORTH_HUB', 'SPP North Hub', 'USD', 'America/Chicago', 'SPP', 'SPPNORTH_HUB');
