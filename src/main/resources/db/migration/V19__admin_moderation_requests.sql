-- ─────────────────────────── АДМИНКА: МОДЕРАЦИЯ (A4) И ЗАПРОСЫ (A8) ───────────────────────────

-- Скрытое администрацией не отдаётся в приложение: запчасть не ищется, отзыв не показывается и не входит
-- в рейтинг, сообщение показывается заглушкой, запрос и заявка пропадают из лент продавцов и мастеров.
ALTER TABLE parts            ADD COLUMN hidden_by_admin BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN hidden_reason VARCHAR(300);
ALTER TABLE reviews          ADD COLUMN hidden_by_admin BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN hidden_reason VARCHAR(300);
ALTER TABLE master_reviews   ADD COLUMN hidden_by_admin BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN hidden_reason VARCHAR(300);
ALTER TABLE messages         ADD COLUMN hidden_by_admin BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN hidden_reason VARCHAR(300);
ALTER TABLE part_requests    ADD COLUMN hidden_by_admin BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN hidden_reason VARCHAR(300);
ALTER TABLE service_requests ADD COLUMN hidden_by_admin BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN hidden_reason VARCHAR(300);
ALTER TABLE service_offers   ADD COLUMN hidden_by_admin BOOLEAN NOT NULL DEFAULT FALSE, ADD COLUMN hidden_reason VARCHAR(300);

-- Жалобы: на что (тип объекта), почему (причина), кто подал и в какой роли, связанный запрос, исход решения.
-- PHOTO становится SHOP_PHOTO; добавлены MASTER_REVIEW, CHAT_MESSAGE, SERVICE_OFFER.
ALTER TABLE complaints DROP CONSTRAINT complaints_type_check;
UPDATE complaints SET type = 'SHOP_PHOTO' WHERE type = 'PHOTO';
ALTER TABLE complaints ADD CONSTRAINT complaints_type_check CHECK (type IN (
    'CONTAINER_CLAIM', 'SHOP', 'PART', 'SHOP_PHOTO', 'REVIEW', 'MASTER_REVIEW', 'CHAT', 'CHAT_MESSAGE', 'MASTER',
    'SERVICE_OFFER'));
ALTER TABLE complaints
    ADD COLUMN reason VARCHAR(23) NOT NULL DEFAULT 'OTHER'
        CHECK (reason IN ('FAKE_ORIGINAL', 'REVIEW_WITHOUT_PURCHASE', 'SPAM_FRAUD', 'WRONG_PLACE', 'RUDE', 'OTHER')),
    ADD COLUMN reporter_role VARCHAR(6) CHECK (reporter_role IN ('BUYER', 'SELLER', 'MASTER')),
    ADD COLUMN related_request_id BIGINT REFERENCES part_requests(id) ON DELETE SET NULL,
    ADD COLUMN related_service_request_id BIGINT REFERENCES service_requests(id) ON DELETE SET NULL,
    ADD COLUMN outcome VARCHAR(9) CHECK (outcome IN ('REMOVED', 'WARNED', 'BLOCKED', 'UNFOUNDED'));
ALTER TABLE complaints ALTER COLUMN resolution TYPE VARCHAR(1000);
CREATE INDEX idx_complaints_target ON complaints (type, target_id);
CREATE INDEX idx_complaints_feed ON complaints (status, created_at DESC, id DESC);

-- Блокировка пользователя целиком: не входит, его магазин / профиль мастера не получает запросов
ALTER TABLE users ADD COLUMN blocked_at TIMESTAMPTZ, ADD COLUMN blocked_reason VARCHAR(300);

-- Мониторинг: списки по дате создания
CREATE INDEX idx_part_requests_created ON part_requests (created_at DESC, id DESC);
CREATE INDEX idx_service_requests_created ON service_requests (created_at DESC, id DESC);
