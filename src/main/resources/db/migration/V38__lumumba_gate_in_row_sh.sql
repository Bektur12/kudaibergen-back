-- Ряд Ш у входа с ул. Лумумбы — как на месте:
--  • контейнеры 34–37 стояли на схеме внизу, среди 42–49, а на деле идут сразу после 33, на изгибе ряда;
--  • контейнеров 38–40 в базе не было — они там же, после 37;
--  • ворота — между 40 и 42: точка схемы (1162.0, 640.6) на границе рынка, GPS ≈ 42.881298, 74.524714,
--    от ворот — короткий проход внутрь до прохода вдоль ряда Ш, маршрут идёт через ворота;
--  • ряд Ш на схеме — двумя кусками (geometry.parts) с разрывом под ворота: рисуется кусками, но это один ряд —
--    поиск «Ш 42» и места магазинов не меняются (axis/polygon для расчётов прежние).
-- Шаг между контейнерами и отступ от оси — как у соседних 28–33 (15 и 35 единиц схемы).
-- Новая версия схемы (ETag) — приложения скачают её заново. Только поверх v8 из V37.
DO $$
DECLARE
    cur map_versions%ROWTYPE;
    sh  BIGINT;
BEGIN
    SELECT * INTO cur FROM map_versions
    WHERE is_current AND version = 8 AND entrances @> '[{"label": "Вход с ул. Лумумбы", "x": 1181.8}]';
    SELECT id INTO sh FROM market_rows WHERE code = 'Ш' AND geometry ? 'polygon';
    IF cur.id IS NULL OR sh IS NULL THEN
        RETURN;
    END IF;

    UPDATE map_versions SET is_current = FALSE WHERE id = cur.id;
    INSERT INTO map_versions (version, is_current, boundary, blocks, passages, entrances, pois, streets, labels,
                              meters_per_px, geo_affine, published_by)
    VALUES ((SELECT max(version) + 1 FROM map_versions), TRUE, cur.boundary, cur.blocks,
            cur.passages || '[{"points": [[1162.0, 640.6], [1104.4, 639.4]], "width": 14}]'::jsonb,
            (SELECT jsonb_agg(CASE WHEN e->>'label' = 'Вход с ул. Лумумбы'
                                   THEN jsonb_build_object('x', 1162.0, 'y', 640.6, 'label', e->>'label')
                                   ELSE e END ORDER BY ord)
             FROM jsonb_array_elements(cur.entrances) WITH ORDINALITY AS t(e, ord)),
            cur.pois, cur.streets, cur.labels, cur.meters_per_px, cur.geo_affine, cur.published_by);

    UPDATE market_rows
    SET geometry = geometry || '{"parts": [
          [[768.4, 105.6], [913.7, 333.3], [1060.9, 593.1], [1067.8, 623.0], [1126.9, 588.8], [1120, 558.9], [972.8, 299.1], [827.5, 71.4]],
          [[1085.8, 701.3], [1114.2, 825.1], [1173.3, 790.8], [1144.9, 667.1]]
        ]}'::jsonb
    WHERE id = sh;

    -- 34–40 на изгибе после 33 (есть — переставить и включить, нет — добавить)
    INSERT INTO containers (row_id, side, number, pos_in_row, qr_token, is_active, x, y)
    SELECT sh, 'EAST', p.n, p.n, md5(random()::text || clock_timestamp()::text || p.n::text), TRUE, p.x, p.y
    FROM (VALUES (34, 1053.8, 510.5), (35, 1061.2, 523.6), (36, 1068.6, 536.6), (37, 1076.0, 549.7),
                 (38, 1083.4, 562.7), (39, 1090.8, 575.8), (40, 1097.9, 599.8)) AS p(n, x, y)
    ON CONFLICT (row_id, side, number) DO UPDATE SET x = EXCLUDED.x, y = EXCLUDED.y, is_active = TRUE;
END $$;
