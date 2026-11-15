-- Категории продавца копятся сами: ответил «Есть» на запрос категории — значит, продаёт её (Shop.learnCategory).
-- Разово — из ответов, данных до этого: каждому магазину все категории запросов, на которые он ответил «Есть».
INSERT INTO shop_categories (shop_id, category_id)
SELECT DISTINCT r.shop_id, pr.category_id
FROM request_replies r
JOIN part_requests pr ON pr.id = r.request_id
WHERE r.answer = 'HAVE' AND pr.category_id IS NOT NULL
ON CONFLICT DO NOTHING;
