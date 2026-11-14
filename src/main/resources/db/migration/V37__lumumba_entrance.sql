-- Вход с улицы Патриса Лумумбы: ворота в ряду Ш после контейнера 40 (между 37 и 42), на северо-восточной
-- границе рынка — точка схемы (1181.8, 686.2), GPS ≈ 42.881195, 74.524775.
-- Новая версия схемы — копия текущей с ещё одним входом, как при публикации из админки: приложения увидят
-- новую версию (ETag) и скачают схему; маршрут сам выберет этот вход, если через него ближе.
-- Только поверх схемы v7 (на ней размечены ворота) и только если такого входа ещё нет — на других базах ничего не меняет.
DO $$
DECLARE
    cur map_versions%ROWTYPE;
BEGIN
    SELECT * INTO cur FROM map_versions
    WHERE is_current AND version = 7 AND NOT entrances @> '[{"label": "Вход с ул. Лумумбы"}]';
    IF NOT FOUND THEN
        RETURN;
    END IF;
    -- сначала снять «текущую» (уникальный индекс: текущая — одна), потом добавить новую
    UPDATE map_versions SET is_current = FALSE WHERE id = cur.id;
    INSERT INTO map_versions (version, is_current, boundary, blocks, passages, entrances, pois, streets, labels,
                              meters_per_px, geo_affine, published_by)
    VALUES ((SELECT max(version) + 1 FROM map_versions), TRUE, cur.boundary, cur.blocks, cur.passages,
            cur.entrances || '[{"x": 1181.8, "y": 686.2, "label": "Вход с ул. Лумумбы"}]'::jsonb,
            cur.pois, cur.streets, cur.labels, cur.meters_per_px, cur.geo_affine, cur.published_by);
END $$;
