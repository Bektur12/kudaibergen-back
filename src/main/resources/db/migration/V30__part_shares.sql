-- Счётчик «Поделиться» у видео в ленте: сколько раз товаром поделились.
ALTER TABLE parts ADD COLUMN shares_count INT NOT NULL DEFAULT 0;
