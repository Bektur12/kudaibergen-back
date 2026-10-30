-- Без минимальной длины: текст запроса и описание заявки — любой непустой («шина», «фара»).
ALTER TABLE part_requests DROP CONSTRAINT part_requests_text_check;
ALTER TABLE part_requests ADD CONSTRAINT part_requests_text_check CHECK (char_length(btrim(text)) >= 1);
ALTER TABLE service_requests DROP CONSTRAINT service_requests_description_check;
ALTER TABLE service_requests ADD CONSTRAINT service_requests_description_check CHECK (char_length(btrim(description)) >= 1);
