-- Не только легковые: грузовики, автобусы, тракторы и спецтехника, что ездят и работают в Кыргызстане.
-- Логотипы — в приложении (assets/brands по slug), у кого файла нет — буква на цветном круге.
INSERT INTO brands (slug, name, short_name, logo_url, placeholder, color, popular, sort_order, aliases) VALUES
    -- грузовики
    ('kamaz',        'КАМАЗ',              'КАМАЗ',       NULL, 'К',   '#1E3A8A', FALSE, 100, '{камаз,kamaz}'),
    ('maz',          'МАЗ',                'МАЗ',         NULL, 'М',   '#1E40AF', FALSE, 100, '{маз,maz}'),
    ('ural',         'Урал',               'Урал',        NULL, 'У',   '#B91C1C', FALSE, 100, '{урал,ural}'),
    ('zil',          'ЗИЛ',                'ЗИЛ',         NULL, 'ЗИЛ', '#1F2937', FALSE, 100, '{зил,zil,бычок}'),
    ('shacman',      'Shacman',            'Shacman',     NULL, 'S',   '#B91C1C', FALSE, 100, '{шакман,шахман,shaanxi}'),
    ('sinotruk',     'Sinotruk (HOWO)',    'HOWO',        NULL, 'H',   '#B91C1C', FALSE, 100, '{хово,хова,синотрук,howo}'),
    ('foton',        'Foton',              'Foton',       NULL, 'F',   '#1E40AF', FALSE, 100, '{фотон,auman,ауман}'),
    ('hino',         'Hino',               'Hino',        NULL, 'H',   '#B91C1C', FALSE, 100, '{хино}'),
    ('fuso',         'Mitsubishi Fuso',    'Fuso',        NULL, 'F',   '#B91C1C', FALSE, 100, '{фусо,кантер,canter}'),
    ('man',          'MAN',                'MAN',         NULL, 'MAN', '#1F2937', FALSE, 100, '{ман}'),
    ('scania',       'Scania',             'Scania',      NULL, 'S',   '#1E3A8A', FALSE, 100, '{скания}'),
    ('daf',          'DAF',                'DAF',         NULL, 'DAF', '#1E3A8A', FALSE, 100, '{даф}'),
    ('iveco',        'Iveco',              'Iveco',       NULL, 'I',   '#1E40AF', FALSE, 100, '{ивеко}'),
    -- автобусы
    ('paz',          'ПАЗ',                'ПАЗ',         NULL, 'ПАЗ', '#CA8A04', FALSE, 100, '{паз,paz}'),
    ('yutong',       'Yutong',             'Yutong',      NULL, 'Y',   '#1E40AF', FALSE, 100, '{ютонг}'),
    -- тракторы и сельхозтехника
    ('mtz',          'МТЗ «Беларус»',      'МТЗ',         NULL, 'МТЗ', '#B91C1C', FALSE, 100, '{мтз,беларус,беларусь,belarus}'),
    ('yumz',         'ЮМЗ',                'ЮМЗ',         NULL, 'ЮМЗ', '#1E40AF', FALSE, 100, '{юмз}'),
    ('vgtz',         'ВгТЗ',               'ВгТЗ',        NULL, 'ДТ',  '#3F6212', FALSE, 100, '{дт-75,дт75,вгтз}'),
    ('john-deere',   'John Deere',         'John Deere',  NULL, 'JD',  '#3F6212', FALSE, 100, '{джон дир,джондир}'),
    ('case',         'Case IH',            'Case',        NULL, 'C',   '#B91C1C', FALSE, 100, '{кейс}'),
    ('new-holland',  'New Holland',        'New Holland', NULL, 'NH',  '#1E40AF', FALSE, 100, '{нью холланд}'),
    ('kubota',       'Kubota',             'Kubota',      NULL, 'K',   '#EA580C', FALSE, 100, '{кубота}'),
    ('claas',        'Claas',              'Claas',       NULL, 'C',   '#3F6212', FALSE, 100, '{клаас,клас}'),
    -- спецтехника
    ('jcb',          'JCB',                'JCB',         NULL, 'JCB', '#CA8A04', FALSE, 100, '{джисиби,жсб}'),
    ('caterpillar',  'Caterpillar',        'CAT',         NULL, 'CAT', '#CA8A04', FALSE, 100, '{катерпиллер,кат,cat}'),
    ('komatsu',      'Komatsu',            'Komatsu',     NULL, 'K',   '#1E3A8A', FALSE, 100, '{комацу}'),
    ('xcmg',         'XCMG',               'XCMG',        NULL, 'X',   '#CA8A04', FALSE, 100, '{хцмг,иксиэмджи}'),
    ('sdlg',         'SDLG',               'SDLG',        NULL, 'S',   '#CA8A04', FALSE, 100, '{сдлг}'),
    ('shantui',      'Shantui',            'Shantui',     NULL, 'S',   '#CA8A04', FALSE, 100, '{шантуй}')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO models (brand_id, name, sort_order, aliases)
SELECT b.id, m.name, m.sort_order, m.aliases::text[]
FROM (VALUES
    ('kamaz', '5320', 1, '{}'), ('kamaz', '5511', 2, '{}'), ('kamaz', '53212', 3, '{}'), ('kamaz', '55111', 4, '{самосвал}'),
    ('kamaz', '65115', 5, '{}'), ('kamaz', '6520', 6, '{}'), ('kamaz', '43118', 7, '{вездеход}'), ('kamaz', '4310', 8, '{}'),
    ('kamaz', '54115', 9, '{тягач}'),
    ('maz', '5440', 1, '{тягач}'), ('maz', '6430', 2, '{}'), ('maz', '5551', 3, '{самосвал}'), ('maz', '5516', 4, '{}'),
    ('maz', '4370 «Зубрёнок»', 5, '{зубренок}'), ('maz', '6501', 6, '{}'),
    ('ural', '4320', 1, '{}'), ('ural', '375', 2, '{}'), ('ural', 'Next', 3, '{некст}'),
    ('zil', '130', 1, '{}'), ('zil', '131', 2, '{}'), ('zil', '5301 «Бычок»', 3, '{бычок}'), ('zil', '4331', 4, '{}'),
    ('shacman', 'F2000', 1, '{}'), ('shacman', 'F3000', 2, '{}'), ('shacman', 'X3000', 3, '{}'), ('shacman', 'X5000', 4, '{}'),
    ('sinotruk', 'HOWO A7', 1, '{}'), ('sinotruk', 'HOWO T5G', 2, '{}'), ('sinotruk', 'HOWO T7H', 3, '{}'),
    ('sinotruk', 'HOWO ZZ3257 самосвал', 4, '{самосвал}'),
    ('foton', 'Auman', 1, '{ауман}'), ('foton', 'Aumark', 2, '{аумарк}'), ('foton', 'Ollin', 3, '{оллин}'),
    ('hino', '300 (Dutro)', 1, '{дутро}'), ('hino', '500 Ranger', 2, '{рейнджер}'), ('hino', '700 Profia', 3, '{профия}'),
    ('fuso', 'Canter', 1, '{кантер}'), ('fuso', 'Fighter', 2, '{файтер}'), ('fuso', 'Super Great', 3, '{}'),
    ('man', 'TGA', 1, '{}'), ('man', 'TGS', 2, '{}'), ('man', 'TGX', 3, '{}'), ('man', 'TGL', 4, '{}'), ('man', 'TGM', 5, '{}'),
    ('scania', 'R-серия', 1, '{r}'), ('scania', 'G-серия', 2, '{g}'), ('scania', 'P-серия', 3, '{p}'), ('scania', 'S-серия', 4, '{s}'),
    ('daf', 'XF', 1, '{}'), ('daf', 'CF', 2, '{}'), ('daf', 'LF', 3, '{}'),
    ('iveco', 'Daily', 1, '{дейли}'), ('iveco', 'Eurocargo', 2, '{}'), ('iveco', 'Stralis', 3, '{стралис}'), ('iveco', 'S-Way', 4, '{}'),
    ('paz', '3205', 1, '{}'), ('paz', '4234', 2, '{}'), ('paz', 'Вектор Next', 3, '{вектор}'),
    ('yutong', 'ZK6122', 1, '{}'), ('yutong', 'ZK6938', 2, '{}'),
    ('mtz', '80', 1, '{}'), ('mtz', '82.1', 2, '{82}'), ('mtz', '892', 3, '{}'), ('mtz', '1221', 4, '{}'), ('mtz', '1523', 5, '{}'),
    ('yumz', '6', 1, '{юмз-6}'),
    ('vgtz', 'ДТ-75', 1, '{дт75}'),
    ('john-deere', '6M', 1, '{}'), ('john-deere', '7R', 2, '{}'), ('john-deere', '8R', 3, '{}'), ('john-deere', '9R', 4, '{}'),
    ('case', 'Puma', 1, '{пума}'), ('case', 'Magnum', 2, '{магнум}'), ('case', 'Farmall', 3, '{фармол}'),
    ('new-holland', 'T6', 1, '{}'), ('new-holland', 'T7', 2, '{}'), ('new-holland', 'T8', 3, '{}'),
    ('kubota', 'M-серия', 1, '{}'), ('kubota', 'L-серия', 2, '{}'),
    ('claas', 'Axion', 1, '{аксион}'), ('claas', 'Arion', 2, '{арион}'), ('claas', 'Lexion (комбайн)', 3, '{лексион,комбайн}'),
    ('jcb', '3CX', 1, '{}'), ('jcb', '4CX', 2, '{}'), ('jcb', 'JS220', 3, '{}'),
    ('caterpillar', '320', 1, '{}'), ('caterpillar', '330', 2, '{}'), ('caterpillar', '428', 3, '{}'),
    ('komatsu', 'PC200', 1, '{}'), ('komatsu', 'PC300', 2, '{}'), ('komatsu', 'D65', 3, '{}'),
    ('xcmg', 'XE215', 1, '{}'), ('xcmg', 'LW300', 2, '{}'),
    ('sdlg', 'LG936', 1, '{}'), ('sdlg', 'LG952', 2, '{}'),
    ('shantui', 'SD16', 1, '{}'), ('shantui', 'SD22', 2, '{}'),
    -- грузовые и коммерческие у марок, что уже есть
    ('mercedes-benz', 'Actros', 80, '{актрос}'), ('mercedes-benz', 'Atego', 81, '{атего}'), ('mercedes-benz', 'Axor', 82, '{аксор}'),
    ('mercedes-benz', 'Arocs', 83, '{}'), ('mercedes-benz', 'Sprinter', 84, '{спринтер}'), ('mercedes-benz', 'Unimog', 85, '{унимог}'),
    ('volvo', 'FH', 80, '{}'), ('volvo', 'FM', 81, '{}'), ('volvo', 'FMX', 82, '{}'),
    ('isuzu', 'Elf', 80, '{эльф}'), ('isuzu', 'Forward', 81, '{форвард}'), ('isuzu', 'Giga', 82, '{гига}'), ('isuzu', 'NQR', 83, '{}'),
    ('hyundai', 'Porter', 80, '{портер}'), ('hyundai', 'HD65', 81, '{}'), ('hyundai', 'HD72', 82, '{}'), ('hyundai', 'HD78', 83, '{}'),
    ('hyundai', 'HD120', 84, '{}'), ('hyundai', 'County', 85, '{каунти}'),
    ('kia', 'Bongo', 80, '{бонго}'),
    ('gaz', 'ГАЗон Next', 80, '{газон}'), ('gaz', 'ГАЗ-3309', 81, '{}'),
    ('faw', 'J6', 80, '{}'), ('faw', 'J7', 81, '{}')
) AS m(slug, name, sort_order, aliases)
JOIN brands b ON b.slug = m.slug
WHERE NOT EXISTS (SELECT 1 FROM models x WHERE x.brand_id = b.id AND x.name = m.name);

-- Справочник поменялся — клиенты перечитают марки.
UPDATE dictionary_version SET version = version + 1, updated_at = now() WHERE id = 1;
