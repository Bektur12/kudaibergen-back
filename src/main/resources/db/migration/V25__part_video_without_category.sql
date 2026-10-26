-- Товар из «Выложить видео» публикуется без категории: категорию требуем только у товара без видео.
ALTER TABLE parts DROP CONSTRAINT parts_check;
ALTER TABLE parts ADD CONSTRAINT parts_check CHECK (status = 'DRAFT' OR (title IS NOT NULL
    AND (category_id IS NOT NULL OR video_media_id IS NOT NULL) AND condition IS NOT NULL AND price IS NOT NULL));
