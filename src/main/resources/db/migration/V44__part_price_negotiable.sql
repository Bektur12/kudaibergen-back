-- «Договорная»: продавец не ставит цену, покупатель договаривается в чате или по телефону. Тогда price = NULL.
ALTER TABLE parts ADD COLUMN price_negotiable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE parts DROP CONSTRAINT parts_check;
ALTER TABLE parts ADD CONSTRAINT parts_check CHECK (status = 'DRAFT' OR (title IS NOT NULL
    AND (category_id IS NOT NULL OR video_media_id IS NOT NULL) AND condition IS NOT NULL
    AND (price IS NOT NULL OR price_negotiable)));
