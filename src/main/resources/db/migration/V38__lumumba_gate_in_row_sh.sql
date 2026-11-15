-- Вход с ул. Лумумбы — выше, чем в V37: на изгибе ряда Ш, где между контейнерами 33 и 34 пустое место.
-- Ворота на границе рынка — точка схемы (1125.2, 555.9), GPS ≈ 42.88149, 74.52460.
-- От ворот — короткий проход внутрь до начала прохода вдоль ряда Ш (1104.4, 639.4): маршрут идёт через ворота.
-- Ряд Ш на схеме — двумя кусками (geometry.parts) с разрывом под ворота: рисуется кусками, но это один ряд —
-- контейнеры, поиск «Ш 42» и места магазинов не меняются (axis/polygon для расчётов остаются прежними).
-- Новая версия схемы (ETag) — приложения скачают её заново. Только поверх v8 из V37.
DO $$
DECLARE
    cur map_versions%ROWTYPE;
BEGIN
    SELECT * INTO cur FROM map_versions
    WHERE is_current AND version = 8 AND entrances @> '[{"label": "Вход с ул. Лумумбы", "x": 1181.8}]';
    IF NOT FOUND THEN
        RETURN;
    END IF;
    UPDATE map_versions SET is_current = FALSE WHERE id = cur.id;
    INSERT INTO map_versions (version, is_current, boundary, blocks, passages, entrances, pois, streets, labels,
                              meters_per_px, geo_affine, published_by)
    VALUES ((SELECT max(version) + 1 FROM map_versions), TRUE, cur.boundary, cur.blocks,
            cur.passages || '[{"points": [[1125.2, 555.9], [1104.4, 639.4]], "width": 14}]'::jsonb,
            (SELECT jsonb_agg(CASE WHEN e->>'label' = 'Вход с ул. Лумумбы'
                                   THEN jsonb_build_object('x', 1125.2, 'y', 555.9, 'label', e->>'label')
                                   ELSE e END ORDER BY ord)
             FROM jsonb_array_elements(cur.entrances) WITH ORDINALITY AS t(e, ord)),
            cur.pois, cur.streets, cur.labels, cur.meters_per_px, cur.geo_affine, cur.published_by);

    UPDATE market_rows
    SET geometry = geometry || '{"parts": [
          [[768.4, 105.6], [913.7, 333.3], [1022.0, 525.0], [1081.0, 490.7], [972.8, 299.1], [827.5, 71.4]],
          [[1074.8, 653.6], [1114.2, 825.1], [1173.3, 790.8], [1133.9, 619.3]]
        ]}'::jsonb
    WHERE code = 'Ш' AND geometry ? 'polygon';
END $$;
