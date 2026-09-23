-- Медиа-сообщения в чате: фото, голосовые, видео
ALTER TABLE messages
    ADD COLUMN media_url        TEXT,
    ADD COLUMN mime_type        VARCHAR(100),
    ADD COLUMN duration_seconds INT;

COMMENT ON COLUMN messages.type IS 'TEXT / PHOTO / VOICE / VIDEO';
