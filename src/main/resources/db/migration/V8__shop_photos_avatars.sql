-- ─────────────────────────── АВАТАРЫ И ФОТО МЕСТА ───────────────────────────
-- Аватар магазина (22, 30, карточки) и пользователя (19). Без аватара клиент рисует первую букву.
ALTER TABLE shops ADD COLUMN avatar_media_id BIGINT REFERENCES media(id) ON DELETE SET NULL;
ALTER TABLE users ADD COLUMN avatar_media_id BIGINT REFERENCES media(id) ON DELETE SET NULL;

-- Фото места: до 8, первое (sort = 0) — «Обложка» (22, 30, блок продавца на 29)
CREATE TABLE shop_photos (
    shop_id   BIGINT NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    sort      INT    NOT NULL CHECK (sort BETWEEN 0 AND 7),
    media_id  BIGINT NOT NULL REFERENCES media(id),
    PRIMARY KEY (shop_id, sort)
);
CREATE INDEX idx_shop_photos_media ON shop_photos (media_id);
CREATE INDEX idx_part_photos_media ON part_photos (media_id);
