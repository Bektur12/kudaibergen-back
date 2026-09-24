-- Пики громкости голосового сообщения: JSON-массив чисел 0..1; у старых сообщений NULL
ALTER TABLE messages
    ADD COLUMN waveform TEXT;
