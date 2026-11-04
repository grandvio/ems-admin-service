ALTER TABLE ems_market_price ADD COLUMN direction VARCHAR(16) NOT NULL DEFAULT 'SINGLE';

ALTER TABLE ems_market_price DROP INDEX uk_market_price;

ALTER TABLE ems_market_price ADD UNIQUE INDEX uk_market_price (location_id, product, interval_start, direction);

ALTER TABLE ems_station_tariff ADD COLUMN buy_product VARCHAR(16) NOT NULL DEFAULT 'DAY_AHEAD';

ALTER TABLE ems_station_tariff ADD COLUMN sell_product VARCHAR(16) NOT NULL DEFAULT 'DAY_AHEAD';
