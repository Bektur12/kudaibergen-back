-- Прямой чат с магазином (без запроса/предложения): request_id = NULL.
-- Обычный UNIQUE(buyer_id, store_id, request_id) не спасает от дублей на NULL —
-- в Postgres NULL != NULL в уникальных ограничениях, поэтому нужен частичный индекс.
CREATE UNIQUE INDEX idx_chats_direct_unique ON chats (buyer_id, store_id) WHERE request_id IS NULL;
