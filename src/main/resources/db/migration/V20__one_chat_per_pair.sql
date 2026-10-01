-- ─────────────────────────── ОДИН ЧАТ НА ДВОИХ ───────────────────────────
-- Раньше чат заводился на каждую пару «покупатель + магазин + запрос» (и отдельный «прямой»), то же у мастеров —
-- у двух людей получалось несколько чатов. Теперь чат один на пару «покупатель + магазин» и «покупатель + мастер»;
-- запрос или заявка — только контекст: request_id / service_request_id хранят последний, карточки ответов
-- остаются в ленте сообщений. Дубли сливаются в самый старый чат пары.

-- старые ограничения «по запросу» мешают слиянию — снимаем сразу
DROP INDEX uq_chats_request;
DROP INDEX uq_chats_direct;
DROP INDEX uq_chats_service_request;
DROP INDEX uq_chats_master_direct;

CREATE TEMP TABLE chat_merge ON COMMIT DROP AS
SELECT c.id AS dup_id, k.keep_id
FROM chats c
JOIN (SELECT buyer_id, shop_id, master_id, min(id) AS keep_id
      FROM chats GROUP BY buyer_id, shop_id, master_id HAVING count(*) > 1) k
  ON k.buyer_id = c.buyer_id
 AND k.shop_id IS NOT DISTINCT FROM c.shop_id
 AND k.master_id IS NOT DISTINCT FROM c.master_id
WHERE c.id <> k.keep_id;

-- плашка «Оплата в боксе при осмотре» уже есть в оставшемся чате
DELETE FROM messages m USING chat_merge d
WHERE m.chat_id = d.dup_id AND m.side = 'SYSTEM' AND m.code = 'PAY_AT_BOX';

-- clientId уникален в пределах чата; для перенесённых он больше не нужен (повторы давно отправлены)
UPDATE messages m SET client_id = NULL FROM chat_merge d WHERE m.chat_id = d.dup_id;
UPDATE messages m SET chat_id = d.keep_id FROM chat_merge d WHERE m.chat_id = d.dup_id;

-- сводка оставшегося чата: последнее сообщение, прочитанное, контекст — последний запрос / заявка
UPDATE chats k SET
    last_message_id        = g.last_message_id,
    last_message_at        = g.last_message_at,
    buyer_read_message_id  = g.buyer_read,
    shop_read_message_id   = g.shop_read,
    buyer_first_message_at = g.first_at,
    blocked_by_buyer       = g.blocked_buyer,
    blocked_by_shop        = g.blocked_shop,
    request_id             = g.request_id,
    service_request_id     = g.service_request_id
FROM (
    SELECT m.keep_id,
           (SELECT max(id) FROM messages WHERE chat_id = m.keep_id AND code IS DISTINCT FROM 'PAY_AT_BOX') AS last_message_id,
           (SELECT max(created_at) FROM messages WHERE chat_id = m.keep_id AND code IS DISTINCT FROM 'PAY_AT_BOX') AS last_message_at,
           max(c.buyer_read_message_id) AS buyer_read,
           max(c.shop_read_message_id) AS shop_read,
           min(c.buyer_first_message_at) AS first_at,
           bool_or(c.blocked_by_buyer) AS blocked_buyer,
           bool_or(c.blocked_by_shop) AS blocked_shop,
           (array_agg(c.request_id ORDER BY c.id DESC) FILTER (WHERE c.request_id IS NOT NULL))[1] AS request_id,
           (array_agg(c.service_request_id ORDER BY c.id DESC)
               FILTER (WHERE c.service_request_id IS NOT NULL))[1] AS service_request_id
    FROM (SELECT DISTINCT keep_id FROM chat_merge) m
    JOIN chats c ON c.id = m.keep_id OR c.id IN (SELECT dup_id FROM chat_merge WHERE keep_id = m.keep_id)
    GROUP BY m.keep_id
) g
WHERE k.id = g.keep_id;

UPDATE complaints c SET target_id = d.keep_id FROM chat_merge d WHERE c.type = 'CHAT' AND c.target_id = d.dup_id;
DELETE FROM chats c USING chat_merge d WHERE c.id = d.dup_id;

CREATE UNIQUE INDEX uq_chats_buyer_shop ON chats (buyer_id, shop_id) WHERE shop_id IS NOT NULL;
CREATE UNIQUE INDEX uq_chats_buyer_master ON chats (buyer_id, master_id) WHERE master_id IS NOT NULL;
