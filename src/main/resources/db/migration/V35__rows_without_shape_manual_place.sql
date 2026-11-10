-- ─────────────────────────── РЯДЫ БЕЗ ФОРМЫ НА СХЕМЕ ───────────────────────────
-- Ряд может быть в списке рядов и с контейнерами, но ещё не нарисован на карте (geometry = NULL):
-- его контейнеры выбираются в сетке, но координат и маршрута к ним нет.
ALTER TABLE market_rows ALTER COLUMN geometry DROP NOT NULL;

-- Ряд К: на схеме его пока нет, 15 контейнеров по северной стороне.
INSERT INTO market_rows (code, label, type, geometry, sort_order, qr_token, is_active)
SELECT 'К', 'Ряд К', 'ROW', NULL, COALESCE((SELECT MAX(sort_order) FROM market_rows), 0) + 1,
       md5(random()::text || clock_timestamp()::text), TRUE
 WHERE NOT EXISTS (SELECT 1 FROM market_rows WHERE code = 'К');

INSERT INTO containers (row_id, side, number, pos_in_row, qr_token, is_active)
SELECT r.id, 'NORTH', g.n, g.n, md5(random()::text || clock_timestamp()::text || g.n::text), TRUE
  FROM market_rows r
 CROSS JOIN generate_series(1, 15) AS g(n)
 WHERE r.code = 'К'
   AND NOT EXISTS (SELECT 1 FROM containers c WHERE c.row_id = r.id AND c.side = 'NORTH' AND c.number = g.n);

-- ─────────────────────────── МЕСТО «НЕТ В СПИСКЕ» ───────────────────────────
-- Продавец не нашёл свой контейнер: магазин на рынке без контейнера, место — текстом («Ряд К, конт. 5»).
ALTER TABLE shops ADD COLUMN manual_place VARCHAR(60);
ALTER TABLE shops DROP CONSTRAINT shops_place_check;
ALTER TABLE shops ADD CONSTRAINT shops_place_check
    CHECK (container_id IS NOT NULL OR manual_place IS NOT NULL
        OR (address IS NOT NULL AND lat IS NOT NULL AND lng IS NOT NULL));
