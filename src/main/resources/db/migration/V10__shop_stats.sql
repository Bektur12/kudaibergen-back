-- Статистика бокса (17) считается из рабочих таблиц. «Продажи» — запросы, закрытые «Купил» у бокса.
CREATE INDEX idx_part_requests_sold ON part_requests (closed_with_shop_id, closed_at)
    WHERE closed_with_shop_id IS NOT NULL;
-- «Написали вам в чат»
CREATE INDEX idx_chats_shop_first_message ON chats (shop_id, buyer_first_message_at)
    WHERE buyer_first_message_at IS NOT NULL;
