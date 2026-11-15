-- Помощь на дороге: эвакуатор и выездной мастер — до 200 км (трасса Бишкек–Ош), телефон мастера обязателен
-- (без интернета остаются звонок и SMS), точка заявки, снятая без сети, — со временем.

-- предел радиуса по услуге: заявки и мастера этой услуги
ALTER TABLE service_types ADD COLUMN max_radius_km SMALLINT NOT NULL DEFAULT 50
    CHECK (max_radius_km BETWEEN 1 AND 200);
UPDATE service_types SET max_radius_km = 200 WHERE code IN ('TOW_TRUCK', 'MOBILE_MASTER');

ALTER TABLE service_types DROP CONSTRAINT IF EXISTS service_types_default_radius_km_check;
ALTER TABLE service_types ADD CONSTRAINT service_types_default_radius_km_check
    CHECK (default_radius_km BETWEEN 1 AND 200);

-- радиус мастера: обычные услуги до 30 км, эвакуатор / выездной — до 200 (проверяет приложение)
ALTER TABLE masters DROP CONSTRAINT IF EXISTS masters_radius_km_check;
ALTER TABLE masters ADD CONSTRAINT masters_radius_km_check CHECK (radius_km BETWEEN 1 AND 200);

-- телефон: у кого не указан — номер аккаунта
UPDATE masters m SET phone = u.phone FROM users u WHERE u.id = m.owner_id AND (m.phone IS NULL OR m.phone = '');
ALTER TABLE masters ALTER COLUMN phone SET NOT NULL;

-- заявка: предел радиуса снимком услуги на момент отправки, время снятия точки (заявка из очереди без сети)
ALTER TABLE service_requests DROP CONSTRAINT IF EXISTS service_requests_radius_km_check;
ALTER TABLE service_requests ADD CONSTRAINT service_requests_radius_km_check CHECK (radius_km BETWEEN 1 AND 200);
ALTER TABLE service_requests ADD COLUMN max_radius_km SMALLINT NOT NULL DEFAULT 50;
UPDATE service_requests SET max_radius_km = 200 WHERE service IN ('TOW_TRUCK', 'MOBILE_MASTER');
ALTER TABLE service_requests ADD COLUMN located_at TIMESTAMPTZ;
