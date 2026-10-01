-- Непредсказуемый публичный id для ссылок «Поделиться» (профиль магазина, карточка запчасти):
-- числовые id идут по порядку, их легко перебрать. Новым строкам id выдаёт приложение (PublicIds).
ALTER TABLE shops ADD COLUMN public_id VARCHAR(16);
ALTER TABLE parts ADD COLUMN public_id VARCHAR(16);
UPDATE shops SET public_id = substr(md5(random()::text || clock_timestamp()::text || id), 1, 12);
UPDATE parts SET public_id = substr(md5(random()::text || clock_timestamp()::text || id), 1, 12);
ALTER TABLE shops ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE parts ALTER COLUMN public_id SET NOT NULL;
CREATE UNIQUE INDEX uq_shops_public_id ON shops (public_id);
CREATE UNIQUE INDEX uq_parts_public_id ON parts (public_id);
