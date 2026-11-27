-- Пережатие видео (VideoTranscodeJob): телефоны пишут 1080p–4K до 20 Мбит/с и HEVC в .mov — в ленте видео
-- не успевает грузиться по мобильному интернету. Сервер пережимает каждое видео в H.264 MP4 720p ~2,5 Мбит/с
-- с индексом в начале файла и подменяет file_key; исходник остаётся в original_key.
-- transcode_state: PENDING — ждёт, DONE — пережато (или исходник и так лёгкий), FAILED — ffmpeg не справился.
ALTER TABLE media ADD COLUMN original_key VARCHAR(300);
ALTER TABLE media ADD COLUMN transcode_state VARCHAR(8) CHECK (transcode_state IN ('PENDING', 'DONE', 'FAILED'));
ALTER TABLE media ADD COLUMN transcode_attempts SMALLINT NOT NULL DEFAULT 0;
-- уже загруженные видео тоже пережимаются
UPDATE media SET transcode_state = 'PENDING' WHERE kind = 'VIDEO';
CREATE INDEX idx_media_transcode_pending ON media (id DESC) WHERE transcode_state = 'PENDING';
