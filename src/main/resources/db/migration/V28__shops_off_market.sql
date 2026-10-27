-- ─────────────────────────── МАГАЗИНЫ ВНЕ РЫНКА ───────────────────────────
-- Продавец — не только контейнер на рынке Кудайберген: отдельный магазин или центр (Kia, Toyota…) со своим
-- адресом и точкой. Такой магазин без контейнера: адрес и координаты обязательны, проверки места нет.
ALTER TABLE shops ALTER COLUMN container_id DROP NOT NULL;
ALTER TABLE shops ADD COLUMN address VARCHAR(160);
ALTER TABLE shops ADD COLUMN lat DOUBLE PRECISION;
ALTER TABLE shops ADD COLUMN lng DOUBLE PRECISION;
ALTER TABLE shops ADD CONSTRAINT shops_place_check
    CHECK (container_id IS NOT NULL OR (address IS NOT NULL AND lat IS NOT NULL AND lng IS NOT NULL));
