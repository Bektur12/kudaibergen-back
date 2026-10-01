-- ─────────────────────────── ЗАПРОС: СРОК, АДРЕСАТЫ, СТАТИСТИКА, ФОТО ───────────────────────────
-- Спецификация по дизайну, экраны 06б, 31, 32 (docs/SPEC_GAPS.md, пункты 1–4).
-- Запрос живёт выбранное время («Сколько ждать ответы»), потом EXPIRED; покупатель продлевает до 3 раз.
-- Адресаты — весь рынок, 1–10 рядов или 1–30 контейнеров. У каждого получателя свой статус для экрана 32.

-- ── статусы: OPEN → ACTIVE; EXPIRED теперь «время вышло», а не «7 дней без действий»
ALTER TABLE part_requests DROP CONSTRAINT part_requests_status_check;
UPDATE part_requests SET status = 'ACTIVE' WHERE status = 'OPEN';
ALTER TABLE part_requests ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE part_requests ADD CONSTRAINT part_requests_status_check CHECK (status IN ('ACTIVE', 'CLOSED', 'EXPIRED'));

-- ── срок: duration выбран на 06б; sent_at — начало текущего окна (создание, расширение, продление после истечения)
ALTER TABLE part_requests
    ADD COLUMN duration       VARCHAR(10) NOT NULL DEFAULT 'MIN_30'
        CHECK (duration IN ('MIN_30', 'HOUR_1', 'HOUR_3', 'END_OF_DAY')),
    ADD COLUMN expires_at     TIMESTAMPTZ,
    ADD COLUMN extended_times SMALLINT    NOT NULL DEFAULT 0 CHECK (extended_times BETWEEN 0 AND 3);
UPDATE part_requests SET expires_at = sent_at + interval '30 minutes';
ALTER TABLE part_requests ALTER COLUMN expires_at SET NOT NULL;
ALTER TABLE part_requests ALTER COLUMN duration DROP DEFAULT;

-- ── адресаты: ROW / SHOP → ROWS / CONTAINERS (массивы)
ALTER TABLE part_requests
    ADD COLUMN target_row_ids       BIGINT[] NOT NULL DEFAULT '{}',
    ADD COLUMN target_container_ids BIGINT[] NOT NULL DEFAULT '{}';
UPDATE part_requests SET target_row_ids = ARRAY[target_row_id] WHERE target = 'ROW';
UPDATE part_requests r SET target_container_ids = ARRAY[s.container_id]
FROM shops s WHERE r.target = 'SHOP' AND s.id = r.target_shop_id;
ALTER TABLE part_requests DROP CONSTRAINT part_requests_target_check;
ALTER TABLE part_requests DROP CONSTRAINT part_requests_check;
ALTER TABLE part_requests ALTER COLUMN target TYPE VARCHAR(10);
UPDATE part_requests SET target = CASE target WHEN 'ROW' THEN 'ROWS' WHEN 'SHOP' THEN 'CONTAINERS' ELSE target END;
-- бокс мог уйти с рынка: такой запрос считаем адресованным всему рынку
UPDATE part_requests SET target = 'MARKET' WHERE target = 'CONTAINERS' AND cardinality(target_container_ids) = 0;
ALTER TABLE part_requests ADD CONSTRAINT part_requests_target_check CHECK (
    (target = 'MARKET' AND cardinality(target_row_ids) = 0 AND cardinality(target_container_ids) = 0)
    OR (target = 'ROWS' AND cardinality(target_row_ids) BETWEEN 1 AND 10 AND cardinality(target_container_ids) = 0)
    OR (target = 'CONTAINERS' AND cardinality(target_container_ids) BETWEEN 1 AND 30
        AND cardinality(target_row_ids) = 0));
ALTER TABLE part_requests
    DROP COLUMN target_row_id,
    DROP COLUMN target_shop_id,
    DROP COLUMN no_reply_at,
    DROP COLUMN last_activity_at;

-- таймер истечения
DROP INDEX idx_part_requests_open;
CREATE INDEX idx_part_requests_expiring ON part_requests (expires_at) WHERE status = 'ACTIVE';

-- ── получатель: статус и место на момент рассылки (для списка «Нет» — ряд и контейнер без названия)
ALTER TABLE request_recipients
    ADD COLUMN status       VARCHAR(9) NOT NULL DEFAULT 'DELIVERED'
        CHECK (status IN ('DELIVERED', 'SEEN', 'HAVE', 'NOT_HAVE', 'EXPIRED')),
    ADD COLUMN row_id       BIGINT REFERENCES market_rows(id),
    ADD COLUMN container_id BIGINT REFERENCES containers(id);
UPDATE request_recipients rr SET container_id = s.container_id, row_id = c.row_id
FROM shops s JOIN containers c ON c.id = s.container_id
WHERE s.id = rr.shop_id;
UPDATE request_recipients rr SET status = rep.answer
FROM request_replies rep WHERE rep.request_id = rr.request_id AND rep.shop_id = rr.shop_id;
UPDATE request_recipients SET status = 'SEEN' WHERE status = 'DELIVERED' AND seen_at IS NOT NULL;
UPDATE request_recipients rr SET status = 'EXPIRED'
FROM part_requests r
WHERE r.id = rr.request_id AND r.status = 'EXPIRED' AND rr.status IN ('DELIVERED', 'SEEN');
-- «Истёкшие» в ленте продавца
CREATE INDEX idx_request_recipients_status ON request_recipients (shop_id, status, notified_at DESC);

-- ── фото к запросу (до 3, экран 06) и к ответу «Есть» (до 3, экран 12)
ALTER TABLE media DROP CONSTRAINT media_purpose_check;
ALTER TABLE media ADD CONSTRAINT media_purpose_check
    CHECK (purpose IN ('PART', 'SHOP', 'AVATAR', 'REQUEST', 'REPLY'));

CREATE TABLE request_photos (
    request_id  BIGINT NOT NULL REFERENCES part_requests(id) ON DELETE CASCADE,
    sort        INT    NOT NULL CHECK (sort BETWEEN 0 AND 2),
    media_id    BIGINT NOT NULL REFERENCES media(id),
    PRIMARY KEY (request_id, sort)
);
CREATE INDEX idx_request_photos_media ON request_photos (media_id);

CREATE TABLE reply_photos (
    reply_id  BIGINT NOT NULL REFERENCES request_replies(id) ON DELETE CASCADE,
    sort      INT    NOT NULL CHECK (sort BETWEEN 0 AND 2),
    media_id  BIGINT NOT NULL REFERENCES media(id),
    PRIMARY KEY (reply_id, sort)
);
CREATE INDEX idx_reply_photos_media ON reply_photos (media_id);
