-- Фото детали к запросу покупателя. Хранит ключ (не URL) — как messages.media_url,
-- резолвится в клиентский URL тем же ChatMediaStorage.
ALTER TABLE requests ADD COLUMN photo_url TEXT;
