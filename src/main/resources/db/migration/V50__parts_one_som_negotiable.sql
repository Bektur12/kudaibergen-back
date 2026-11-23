-- Цена «1 сом» — продавцы ставили её вместо «Договорная», когда такой галочки не было. Теперь это договорная цена.
UPDATE parts SET price = NULL, price_negotiable = TRUE WHERE price = 1;
