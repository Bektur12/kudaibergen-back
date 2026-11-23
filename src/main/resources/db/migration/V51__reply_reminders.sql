-- «Ответьте покупателю»: не ответил — до трёх напоминаний, раз в 10 минут (не ночью).
-- reminder_count — сколько уже ушло, reminded_at — когда последнее.
ALTER TABLE request_recipients ADD COLUMN reminder_count SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE request_recipients ADD COLUMN reminded_at TIMESTAMPTZ;
ALTER TABLE service_recipients ADD COLUMN reminder_count SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE service_recipients ADD COLUMN reminded_at TIMESTAMPTZ;
