-- Категории «Что продаёте»: всё, что идёт в машину, а не только узлы. Аккумуляторы — отдельно от электрики:
-- их продают отдельные точки, и покупатель ищет их отдельно.
INSERT INTO categories (slug, name_ru, name_kg, sort_order) VALUES
    ('batteries',  'Аккумуляторы',           'Аккумуляторлор',             11),
    ('tires',      'Шины и диски',           'Дөңгөлөктөр жана дисктер',   12),
    ('oils',       'Масла и автохимия',      'Майлар жана автохимия',      13),
    ('steering',   'Рулевое управление',     'Рулдук башкаруу',            14),
    ('exhaust',    'Выхлопная система',      'Чыгаруу системасы',          15),
    ('fuel',       'Топливная система',      'Күйүүчү май системасы',      16),
    ('climate',    'Кондиционер и печка',    'Кондиционер жана меш',       17),
    ('glass',      'Стёкла и зеркала',       'Айнектер жана күзгүлөр',     18),
    ('audio',      'Автозвук и электроника', 'Автоүн жана электроника',    19),
    ('accessories','Аксессуары и тюнинг',    'Аксессуарлар жана тюнинг',   20),
    ('tools',      'Инструменты',            'Аспаптар',                   21)
ON CONFLICT (slug) DO NOTHING;

-- «Аккумулятор» в подсказках — теперь в своей категории
UPDATE part_hints SET category_id = (SELECT id FROM categories WHERE slug = 'batteries')
WHERE text_ru = 'Аккумулятор';

INSERT INTO part_hints (text_ru, text_kg, category_id, popularity)
SELECT h.text_ru, h.text_kg, c.id, h.popularity
FROM (VALUES
    ('Шины',          'Дөңгөлөктөр',   'tires', 22),
    ('Моторное масло','Мотор майы',    'oils',  18),
    ('Лобовое стекло','Алдыңкы айнек', 'glass', 15)
) AS h(text_ru, text_kg, slug, popularity)
JOIN categories c ON c.slug = h.slug;

UPDATE dictionary_version SET version = version + 1, updated_at = now() WHERE id = 1;
