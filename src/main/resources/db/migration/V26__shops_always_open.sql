-- Тумблера «Открыт / Закрыт» больше нет: запросы приходят каждому действующему боксу.
UPDATE shops SET is_open = true WHERE NOT is_open;
