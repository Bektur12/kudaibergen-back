-- Ответ «Есть» с ценой «Договорная»: price = NULL, покупатель договаривается в чате или по телефону.
ALTER TABLE request_replies ADD COLUMN price_negotiable BOOLEAN NOT NULL DEFAULT FALSE;
