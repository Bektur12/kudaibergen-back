-- Видео во вложениях заявки на услугу (спецификация 12.4: до 5 фото/видео, видео до 30 с).
-- Фото — как было: key_1080 / key_320 / width / height. Видео — файл в video_key, длительность и тип;
-- обложка необязательна и хранится как у фото (key_1080 / key_320 / width / height).
ALTER TABLE media ADD COLUMN kind VARCHAR(5) NOT NULL DEFAULT 'PHOTO' CHECK (kind IN ('PHOTO', 'VIDEO'));
ALTER TABLE media ADD COLUMN video_key VARCHAR(300);
ALTER TABLE media ADD COLUMN mime_type VARCHAR(60);
ALTER TABLE media ADD COLUMN duration_sec SMALLINT CHECK (duration_sec BETWEEN 1 AND 600);
ALTER TABLE media ALTER COLUMN key_1080 DROP NOT NULL;
ALTER TABLE media ALTER COLUMN key_320 DROP NOT NULL;
ALTER TABLE media ALTER COLUMN width DROP NOT NULL;
ALTER TABLE media ALTER COLUMN height DROP NOT NULL;
ALTER TABLE media ADD CONSTRAINT media_kind_files CHECK (
    (kind = 'PHOTO' AND key_1080 IS NOT NULL AND key_320 IS NOT NULL AND width IS NOT NULL AND height IS NOT NULL)
    OR (kind = 'VIDEO' AND video_key IS NOT NULL AND duration_sec IS NOT NULL));
