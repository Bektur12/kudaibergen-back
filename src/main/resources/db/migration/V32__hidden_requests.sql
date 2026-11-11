-- «Удалить» в «Моих запросах»: запрос скрыт у покупателя, у продавцов и мастеров история остаётся.
ALTER TABLE part_requests ADD COLUMN hidden_at TIMESTAMPTZ;
ALTER TABLE service_requests ADD COLUMN hidden_at TIMESTAMPTZ;
