-- Справочник машин: новые поколения тех моделей, что уже разбиты по поколениям (раньше год машины упирался в
-- последнее поколение — Outback 2015+ было не выбрать), и ходовые модели, которых не было.

-- ─────────────────────────── Новые поколения ───────────────────────────
-- Только моделям, у которых уже есть поколения: рядом с «Hilux» без поколения не появится «Hilux AN120».
INSERT INTO models (brand_id, name, generation, year_from, year_to, sort_order, aliases)
SELECT b.id, m.name, m.generation, m.year_from, m.year_to, m.sort_order, m.aliases
FROM (VALUES
    ('toyota', 'Camry',              '80',    2024, NULL, 5,  '{камри,xv80}'::text[]),
    ('toyota', 'Corolla',            'E170',  2012, 2019, 12, '{королла}'),
    ('toyota', 'Corolla',            'E210',  2018, NULL, 13, '{королла}'),
    ('toyota', 'Prius',              '40',    2015, 2022, 22, '{приус,zvw50}'),
    ('toyota', 'Prius',              '60',    2022, NULL, 23, '{приус}'),
    ('toyota', 'RAV4',               'XA50',  2018, NULL, 32, '{рав4,рав}'),
    ('toyota', 'Land Cruiser Prado', '250',   2023, NULL, 52, '{прадо}'),
    ('toyota', 'Highlander',         'XU50',  2013, 2019, 62, '{хайлендер}'),
    ('toyota', 'Highlander',         'XU70',  2019, NULL, 63, '{хайлендер}'),
    ('toyota', 'Alphard',            'H30',   2015, 2023, 73, '{альфард}'),
    ('toyota', 'Alphard',            'H40',   2023, NULL, 74, '{альфард}'),

    ('lexus', 'RX', 'AL20', 2015, 2022, 3,  '{рх,rx350,rx450h}'),
    ('lexus', 'RX', 'ALA10', 2022, NULL, 4, '{рх,rx350,rx500h}'),
    ('lexus', 'ES', 'XZ10', 2018, NULL, 12, '{ес,es250,es350}'),
    ('lexus', 'GX', '550',  2023, NULL, 22, '{гх,gx550}'),
    ('lexus', 'LX', '600',  2021, NULL, 32, '{лх,lx600}'),
    ('lexus', 'IS', 'XE30', 2013, NULL, 41, '{ис}'),

    ('mercedes-benz', 'C-класс', 'W205', 2014, 2021, 3,  '{цешка,c-class,c класс}'),
    ('mercedes-benz', 'C-класс', 'W206', 2021, NULL, 4,  '{цешка,c-class,c класс}'),
    ('mercedes-benz', 'E-класс', 'W213', 2016, 2023, 13, '{ешка,e-class,e класс}'),
    ('mercedes-benz', 'E-класс', 'W214', 2023, NULL, 14, '{ешка,e-class,e класс}'),
    ('mercedes-benz', 'S-класс', 'W222', 2013, 2020, 22, '{эска,s-class,s класс}'),
    ('mercedes-benz', 'S-класс', 'W223', 2020, NULL, 23, '{эска,s-class,s класс}'),
    ('mercedes-benz', 'ML',      'W166', 2011, 2015, 32, '{мл,m-class}'),
    ('mercedes-benz', 'G-класс', 'W463A', 2018, NULL, 41, '{гелик,гелендваген,g-class}'),

    ('bmw', '3',  'G20', 2018, NULL, 4,  '{тройка,3 series}'),
    ('bmw', '5',  'G30', 2016, 2023, 13, '{пятёрка,пятерка,5 series}'),
    ('bmw', '5',  'G60', 2023, NULL, 14, '{пятёрка,пятерка,5 series}'),
    ('bmw', 'X5', 'F15', 2013, 2018, 22, '{икс5,х5}'),
    ('bmw', 'X5', 'G05', 2018, NULL, 23, '{икс5,х5}'),
    ('bmw', 'X3', 'F25', 2010, 2017, 31, '{икс3,х3}'),
    ('bmw', 'X3', 'G01', 2017, 2024, 32, '{икс3,х3}'),

    ('honda', 'Fit',    'GK', 2013, 2020, 3,  '{фит,jazz}'),
    ('honda', 'Fit',    'GR', 2020, NULL, 4,  '{фит,jazz}'),
    ('honda', 'CR-V',   'RW', 2016, 2022, 12, '{црв,срв}'),
    ('honda', 'CR-V',   'RS', 2022, NULL, 13, '{црв,срв}'),
    ('honda', 'Accord', 'CR', 2012, 2017, 22, '{аккорд}'),
    ('honda', 'Accord', 'CV', 2017, 2022, 23, '{аккорд}'),
    ('honda', 'Odyssey', 'RB3', 2008, 2013, 33, '{одиссей}'),
    ('honda', 'Odyssey', 'RC',  2013, NULL, 34, '{одиссей}'),
    ('honda', 'Stepwgn', 'RK', 2009, 2015, 35, '{степвагон,степ}'),
    ('honda', 'Stepwgn', 'RP', 2015, NULL, 36, '{степвагон,степ}'),
    ('honda', 'Civic',  'FB', 2011, 2015, 41, '{цивик,сивик}'),
    ('honda', 'Civic',  'FC', 2015, 2021, 42, '{цивик,сивик}'),
    ('honda', 'Civic',  'FE', 2021, NULL, 43, '{цивик,сивик}'),

    ('nissan', 'X-Trail', 'T32', 2013, 2022, 3,  '{икстрейл,х-трейл,трейл}'),
    ('nissan', 'X-Trail', 'T33', 2021, NULL, 4,  '{икстрейл,х-трейл,трейл}'),
    ('nissan', 'Teana',   'L33', 2013, 2020, 12, '{теана}'),
    ('nissan', 'Murano',  'Z51', 2007, 2016, 32, '{мурано}'),
    ('nissan', 'Murano',  'Z52', 2014, NULL, 33, '{мурано}'),
    ('nissan', 'Qashqai', 'J11', 2013, 2021, 34, '{кашкай}'),
    ('nissan', 'Qashqai', 'J12', 2021, NULL, 35, '{кашкай}'),
    ('nissan', 'Almera',  'G15', 2012, 2018, 42, '{альмера}'),
    ('nissan', 'Tiida',   'C12', 2011, 2016, 43, '{тиида}'),

    ('hyundai', 'Sonata',   'DN8', 2019, NULL, 4,  '{соната}'),
    ('hyundai', 'Elantra',  'AD',  2015, 2020, 12, '{элантра,аванте}'),
    ('hyundai', 'Elantra',  'CN7', 2020, NULL, 13, '{элантра,аванте}'),
    ('hyundai', 'Tucson',   'TL',  2015, 2021, 22, '{туссан,тусон}'),
    ('hyundai', 'Tucson',   'NX4', 2020, NULL, 23, '{туссан,тусон}'),
    ('hyundai', 'Santa Fe', 'TM',  2018, 2023, 32, '{санта фе,сантафе}'),
    ('hyundai', 'Santa Fe', 'MX5', 2023, NULL, 33, '{санта фе,сантафе}'),
    ('hyundai', 'Accent',   'RB',  2010, 2017, 52, '{акцент}'),
    ('hyundai', 'Accent',   'HC',  2017, 2022, 53, '{акцент}'),
    ('hyundai', 'Solaris',  'HC',  2017, 2022, 54, '{солярис}'),

    ('kia', 'Sportage', 'QL',  2015, 2021, 3,  '{спортейдж,спортаж}'),
    ('kia', 'Sportage', 'NQ5', 2021, NULL, 4,  '{спортейдж,спортаж}'),
    ('kia', 'Sorento',  'UM',  2014, 2020, 12, '{соренто}'),
    ('kia', 'Sorento',  'MQ4', 2020, NULL, 13, '{соренто}'),
    ('kia', 'Rio',      'QB',  2011, 2017, 21, '{рио}'),
    ('kia', 'Rio',      'FB',  2017, NULL, 22, '{рио}'),
    ('kia', 'Optima',   'JF',  2015, 2020, 32, '{оптима,k5}'),
    ('kia', 'Cerato',   'YD',  2013, 2018, 33, '{церато,серато}'),
    ('kia', 'Cerato',   'BD',  2018, NULL, 34, '{церато,серато}'),
    ('kia', 'Carnival', 'YP',  2014, 2020, 41, '{карнивал}'),
    ('kia', 'Carnival', 'KA4', 2020, NULL, 42, '{карнивал}'),

    ('mitsubishi', 'Pajero Sport', 'III', 2015, NULL, 4,  '{паджеро спорт}'),
    ('mitsubishi', 'Outlander',    'GF',  2012, 2021, 12, '{аутлендер}'),
    ('mitsubishi', 'Outlander',    'GN',  2021, NULL, 13, '{аутлендер}'),

    ('subaru', 'Forester', 'SJ',    2012, 2018, 3,  '{форестер}'),
    ('subaru', 'Forester', 'SK',    2018, 2024, 4,  '{форестер}'),
    ('subaru', 'Forester', 'SL',    2024, NULL, 5,  '{форестер}'),
    ('subaru', 'Legacy',   'BM/BR', 2009, 2014, 11, '{легаси}'),
    ('subaru', 'Legacy',   'BN/BS', 2014, 2019, 12, '{легаси}'),
    ('subaru', 'Legacy',   'BW',    2019, 2025, 13, '{легаси}'),
    ('subaru', 'Outback',  'BS',    2014, 2019, 22, '{аутбек,аутбэк}'),
    ('subaru', 'Outback',  'BT',    2019, NULL, 23, '{аутбек,аутбэк}'),
    ('subaru', 'Impreza',  'GJ/GP', 2011, 2016, 31, '{импреза}'),
    ('subaru', 'Impreza',  'GT/GK', 2016, 2023, 32, '{импреза}'),

    ('volkswagen', 'Passat',   'B7',  2010, 2015, 3,  '{пассат}'),
    ('volkswagen', 'Passat',   'B8',  2014, 2023, 4,  '{пассат}'),
    ('volkswagen', 'Golf',     'VI',  2008, 2012, 12, '{гольф}'),
    ('volkswagen', 'Golf',     'VII', 2012, 2020, 13, '{гольф}'),
    ('volkswagen', 'Golf',     'VIII', 2019, NULL, 14, '{гольф}'),
    ('volkswagen', 'Polo',     'VI',  2017, NULL, 21, '{поло}'),
    ('volkswagen', 'Touareg',  '7P',  2010, 2018, 31, '{туарег}'),
    ('volkswagen', 'Touareg',  'CR',  2018, NULL, 32, '{туарег}'),
    ('volkswagen', 'Transporter', 'T6', 2015, NULL, 41, '{транспортер,т6}'),

    ('audi', 'A4', 'B8', 2007, 2016, 3,  '{а4}'),
    ('audi', 'A4', 'B9', 2015, NULL, 4,  '{а4}'),
    ('audi', 'A6', 'C7', 2011, 2018, 12, '{а6}'),
    ('audi', 'A6', 'C8', 2018, NULL, 13, '{а6}'),
    ('audi', 'Q7', '4M', 2015, NULL, 21, '{ку7}'),

    ('mazda', '6', 'GJ', 2012, NULL, 3,  '{мазда 6,атенза}'),
    ('mazda', '3', 'BL', 2009, 2013, 11, '{мазда 3,аксела}'),
    ('mazda', '3', 'BM', 2013, 2019, 12, '{мазда 3,аксела}'),
    ('mazda', '3', 'BP', 2019, NULL, 13, '{мазда 3}'),

    ('opel', 'Astra', 'J', 2009, 2015, 3, '{астра}'),
    ('opel', 'Astra', 'K', 2015, 2021, 4, '{астра}'),

    ('ford', 'Focus',  'IV', 2018, 2025, 3,  '{фокус}'),
    ('ford', 'Mondeo', 'V',  2014, 2022, 12, '{мондео}')
) AS m(slug, name, generation, year_from, year_to, sort_order, aliases)
JOIN brands b ON b.slug = m.slug
WHERE EXISTS (SELECT 1 FROM models x WHERE x.brand_id = b.id AND x.name = m.name AND x.generation IS NOT NULL)
ON CONFLICT DO NOTHING;

-- ─────────────────────────── Модели, которых не было ───────────────────────────
-- Без поколений, как в V31. Уже есть такое название у марки — пропускаем.
INSERT INTO models (brand_id, name, sort_order, aliases)
SELECT b.id, m.name, m.sort_order, m.aliases
FROM (VALUES
    ('subaru', 'Levorg', 53, '{леворг}'::text[]), ('subaru', 'WRX', 54, '{врх,врикс}'), ('subaru', 'BRZ', 55, '{}'),
    ('subaru', 'Exiga', 56, '{эксига}'), ('subaru', 'Crosstrek', 57, '{кросстрек}'), ('subaru', 'Solterra', 58, '{}'),
    ('subaru', 'Traviq', 59, '{}'), ('subaru', 'Pleo', 60, '{плео}'), ('subaru', 'Stella', 61, '{стелла}'),

    ('toyota', 'Aqua', 72, '{аква}'), ('toyota', 'Corolla Axio', 73, '{аксио}'), ('toyota', 'Premio', 74, '{премио}'),
    ('toyota', 'Allion', 75, '{аллион}'), ('toyota', 'Caldina', 76, '{калдина}'), ('toyota', 'Mark X', 77, '{марк икс}'),
    ('toyota', 'Land Cruiser 70', 78, '{ленд крузер,семидесятка}'), ('toyota', 'FJ Cruiser', 79, '{фж}'),
    ('toyota', 'Tacoma', 80, '{такома}'), ('toyota', 'Probox', 81, '{пробокс}'), ('toyota', 'Funcargo', 82, '{фанкарго}'),
    ('toyota', 'Passo', 83, '{пассо}'), ('toyota', 'Raum', 84, '{раум}'), ('toyota', 'Sai', 85, '{}'),
    ('toyota', 'Corolla Cross', 86, '{королла кросс}'), ('toyota', 'bZ4X', 87, '{}'), ('toyota', 'Granvia', 88, '{гранвия}'),
    ('toyota', 'Vellfire', 89, '{вельфаер}'), ('toyota', 'Sienta', 90, '{сиента}'), ('toyota', 'Isis', 91, '{исис}'),
    ('toyota', 'Rush', 92, '{раш}'), ('toyota', 'Hilux Surf', 93, '{сурф}'), ('toyota', 'Chaser', 94, '{чайзер}'),
    ('toyota', 'Cresta', 95, '{креста}'), ('toyota', 'Windom', 96, '{виндом}'), ('toyota', 'Kluger', 97, '{клюгер}'),

    ('lexus', 'LM', 56, '{}'), ('lexus', 'RC', 57, '{}'), ('lexus', 'LC', 58, '{}'), ('lexus', 'TX', 59, '{}'),
    ('lexus', 'HS', 60, '{}'), ('lexus', 'SC', 61, '{}'),

    ('honda', 'Stream', 58, '{стрим}'), ('honda', 'Airwave', 59, '{эйрвейв}'), ('honda', 'Crossroad', 60, '{кроссроуд}'),
    ('honda', 'Shuttle', 61, '{шаттл}'), ('honda', 'Legend', 62, '{легенд}'), ('honda', 'Ridgeline', 63, '{}'),
    ('honda', 'Partner', 64, '{партнер}'), ('honda', 'Mobilio', 65, '{мобилио}'), ('honda', 'N-Box', 66, '{}'),
    ('honda', 'ZR-V', 67, '{}'), ('honda', 'e:NS1', 68, '{}'),

    ('nissan', 'Wingroad', 60, '{вингроуд}'), ('nissan', 'AD', 61, '{}'), ('nissan', 'Bluebird Sylphy', 62, '{блюберд,сильфи}'),
    ('nissan', 'Sentra', 63, '{сентра}'), ('nissan', 'Elgrand', 64, '{эльгранд}'), ('nissan', 'Terrano', 65, '{террано}'),
    ('nissan', 'Armada', 66, '{армада}'), ('nissan', 'Skyline', 67, '{скайлайн}'), ('nissan', 'Fuga', 68, '{фуга}'),
    ('nissan', 'Cefiro', 69, '{цефиро}'), ('nissan', 'Presage', 70, '{присаж}'), ('nissan', 'Dualis', 71, '{дуалис}'),
    ('nissan', 'Kicks', 72, '{кикс}'), ('nissan', 'March', 73, '{марч}'), ('nissan', 'Micra', 74, '{микра}'),
    ('nissan', 'Ariya', 75, '{}'), ('nissan', 'Safari', 76, '{сафари}'), ('nissan', 'Caravan', 77, '{караван}'),

    ('hyundai', 'Venue', 59, '{}'), ('hyundai', 'Ioniq', 60, '{ионик}'), ('hyundai', 'Ioniq 5', 61, '{ионик}'),
    ('hyundai', 'i40', 62, '{}'), ('hyundai', 'ix55', 63, '{}'), ('hyundai', 'Veracruz', 64, '{веракруз}'),
    ('hyundai', 'Terracan', 65, '{терракан}'), ('hyundai', 'Galloper', 66, '{галлопер}'), ('hyundai', 'Matrix', 67, '{матрикс}'),
    ('hyundai', 'Trajet', 68, '{траджет}'), ('hyundai', 'County', 69, '{каунти}'), ('hyundai', 'Equus', 70, '{экус}'),
    ('hyundai', 'Genesis', 71, '{генезис}'), ('hyundai', 'Casper', 72, '{каспер}'), ('hyundai', 'Mufasa', 73, '{муфаса}'),

    ('kia', 'K8', 60, '{}'), ('kia', 'K9', 61, '{}'), ('kia', 'EV6', 62, '{}'), ('kia', 'EV9', 63, '{}'),
    ('kia', 'Niro', 64, '{ниро}'), ('kia', 'Telluride', 65, '{теллурайд}'), ('kia', 'Morning', 66, '{морнинг}'),
    ('kia', 'Spectra', 67, '{спектра}'), ('kia', 'Magentis', 68, '{маджентис}'), ('kia', 'Opirus', 69, '{опирус}'),
    ('kia', 'Venga', 70, '{венга}'), ('kia', 'Sonet', 71, '{}'), ('kia', 'Carens', 72, '{каренс}'), ('kia', 'Quoris', 73, '{}'),

    ('mercedes-benz', 'GLA', 56, '{}'), ('mercedes-benz', 'GLB', 57, '{}'), ('mercedes-benz', 'GLK', 58, '{}'),
    ('mercedes-benz', 'CLA', 59, '{}'), ('mercedes-benz', 'B-класс', 60, '{}'), ('mercedes-benz', 'V-класс', 61, '{виано}'),
    ('mercedes-benz', 'Viano', 62, '{виано}'), ('mercedes-benz', 'R-класс', 63, '{}'), ('mercedes-benz', 'CLK', 64, '{}'),
    ('mercedes-benz', 'SL', 65, '{}'), ('mercedes-benz', 'EQE', 66, '{}'), ('mercedes-benz', 'EQS', 67, '{}'),
    ('mercedes-benz', 'Maybach S', 68, '{майбах}'), ('mercedes-benz', 'Actros', 69, '{актрос}'),

    ('bmw', '4', 56, '{}'), ('bmw', '6', 57, '{}'), ('bmw', '2', 58, '{}'), ('bmw', 'X2', 59, '{}'),
    ('bmw', 'iX', 60, '{}'), ('bmw', 'i3', 61, '{}'), ('bmw', 'XM', 62, '{}'),

    ('audi', 'A5', 55, '{}'), ('audi', 'A7', 56, '{}'), ('audi', 'Q2', 57, '{}'), ('audi', 'e-tron', 58, '{етрон}'),
    ('audi', 'Allroad', 59, '{олроуд}'), ('audi', 'TT', 60, '{}'),

    ('volkswagen', 'Touran', 57, '{туран}'), ('volkswagen', 'Multivan', 58, '{мультивен}'), ('volkswagen', 'Amarok', 59, '{амарок}'),
    ('volkswagen', 'Passat CC', 60, '{пассат сс}'), ('volkswagen', 'Vento', 61, '{венто}'), ('volkswagen', 'Bora', 62, '{бора}'),
    ('volkswagen', 'Crafter', 63, '{крафтер}'), ('volkswagen', 'Phaeton', 64, '{фаэтон}'), ('volkswagen', 'Arteon', 65, '{}'),
    ('volkswagen', 'T-Roc', 66, '{}'), ('volkswagen', 'Atlas', 67, '{атлас}'), ('volkswagen', 'Polo Sedan', 68, '{поло седан}'),

    ('mazda', 'CX-3', 56, '{}'), ('mazda', 'CX-30', 57, '{}'), ('mazda', 'CX-60', 58, '{}'), ('mazda', 'CX-90', 59, '{}'),
    ('mazda', 'MX-5', 60, '{}'), ('mazda', 'Familia', 61, '{фамилия}'), ('mazda', 'Capella', 62, '{капелла}'),
    ('mazda', 'Bongo', 63, '{бонго}'), ('mazda', 'BT-50', 64, '{}'), ('mazda', '626', 65, '{}'), ('mazda', '323', 66, '{}'),

    ('mitsubishi', 'Eclipse', 56, '{эклипс}'), ('mitsubishi', 'Space Star', 57, '{спейс стар}'), ('mitsubishi', 'Mirage', 58, '{мираж}'),
    ('mitsubishi', 'RVR', 59, '{}'), ('mitsubishi', 'Chariot', 60, '{шариот}'), ('mitsubishi', 'Airtrek', 61, '{аиртрек}'),
    ('mitsubishi', 'Carisma', 62, '{каризма}'), ('mitsubishi', 'Xpander', 63, '{}'), ('mitsubishi', 'Canter', 64, '{кантер}'),

    ('chevrolet', 'Gentra', 58, '{джентра}'), ('chevrolet', 'Nexia', 59, '{нексия}'), ('chevrolet', 'Epica', 60, '{эпика}'),
    ('chevrolet', 'Orlando', 61, '{орландо}'), ('chevrolet', 'Trailblazer', 62, '{трейлблейзер}'), ('chevrolet', 'Traverse', 63, '{траверс}'),
    ('chevrolet', 'Camaro', 64, '{камаро}'), ('chevrolet', 'Silverado', 65, '{сильверадо}'), ('chevrolet', 'Suburban', 66, '{субурбан}'),
    ('chevrolet', 'Labo', 67, '{лабо}'), ('chevrolet', 'Bolt', 68, '{болт}'), ('chevrolet', 'Volt', 69, '{вольт}'),
    ('chevrolet', 'Monza', 70, '{монза}'), ('chevrolet', 'Lanos', 71, '{ланос}'),

    ('ford', 'Expedition', 56, '{экспедишн}'), ('ford', 'Edge', 57, '{эдж}'), ('ford', 'EcoSport', 58, '{экоспорт}'),
    ('ford', 'Fiesta', 59, '{фиеста}'), ('ford', 'C-Max', 60, '{си макс}'), ('ford', 'S-Max', 61, '{эс макс}'),
    ('ford', 'Galaxy', 62, '{гэлакси}'), ('ford', 'Bronco', 63, '{бронко}'), ('ford', 'Maverick', 64, '{маверик}'),
    ('ford', 'Taurus', 65, '{таурус}'), ('ford', 'Sierra', 66, '{сиерра}'), ('ford', 'Scorpio', 67, '{скорпио}'),

    ('lada', '2105', 56, '{пятерка}'), ('lada', '2104', 57, '{четверка}'), ('lada', '2101', 58, '{копейка}'),
    ('lada', '2112', 59, '{двенашка}'), ('lada', '2115', 60, '{пятнашка}'), ('lada', '2113', 61, '{}'),
    ('lada', 'Niva Travel', 62, '{нива тревел}'), ('lada', 'Iskra', 63, '{искра}'), ('lada', 'Oka', 64, '{ока}'),

    ('opel', 'Antara', 53, '{антара}'), ('opel', 'Mokka', 54, '{мокка}'), ('opel', 'Frontera', 55, '{фронтера}'),
    ('opel', 'Combo', 56, '{комбо}'), ('opel', 'Vivaro', 57, '{виваро}'), ('opel', 'Calibra', 58, '{калибра}'),
    ('opel', 'Kadett', 59, '{кадет}'), ('opel', 'Sintra', 60, '{синтра}'), ('opel', 'Tigra', 61, '{тигра}'),

    ('daewoo', 'Leganza', 53, '{леганза}'), ('daewoo', 'Nubira', 54, '{нубира}'), ('daewoo', 'Magnus', 55, '{магнус}'),
    ('daewoo', 'Labo', 56, '{лабо}'), ('daewoo', 'Winstorm', 57, '{винсторм}'),

    ('infiniti', 'QX50', 8, '{}'), ('infiniti', 'QX55', 9, '{}'), ('infiniti', 'Q50', 10, '{}'), ('infiniti', 'Q70', 11, '{}'),
    ('infiniti', 'EX', 12, '{}'), ('infiniti', 'JX', 13, '{}'), ('infiniti', 'QX4', 14, '{}'),

    ('acura', 'ZDX', 5, '{}'), ('acura', 'ILX', 6, '{}'), ('acura', 'TLX', 7, '{}'), ('acura', 'RL', 8, '{}'),

    ('suzuki', 'Vitara', 7, '{витара}'), ('suzuki', 'Wagon R', 8, '{вагон р}'), ('suzuki', 'Alto', 9, '{альто}'),
    ('suzuki', 'Liana', 10, '{лиана}'), ('suzuki', 'Splash', 11, '{сплэш}'), ('suzuki', 'Ignis', 12, '{игнис}'),
    ('suzuki', 'XL7', 13, '{}'), ('suzuki', 'Kizashi', 14, '{кизаши}'),

    ('daihatsu', 'Mira', 4, '{мира}'), ('daihatsu', 'Rocky', 5, '{роки}'), ('daihatsu', 'Boon', 6, '{бун}'),
    ('daihatsu', 'Hijet', 7, '{хайджет}'), ('daihatsu', 'Sirion', 8, '{сирион}'),

    ('isuzu', 'MU-X', 5, '{}'), ('isuzu', 'Bighorn', 6, '{бигхорн}'), ('isuzu', 'Forward', 7, '{форвард}'),

    ('ssangyong', 'Tivoli', 6, '{тиволи}'), ('ssangyong', 'Rodius', 7, '{родиус}'), ('ssangyong', 'Torres', 8, '{торрес}'),

    ('porsche', 'Cayman', 6, '{кайман}'), ('porsche', 'Boxster', 7, '{бокстер}'),
    ('volvo', 'XC40', 6, '{}'), ('volvo', 'S40', 7, '{}'), ('volvo', 'S90', 8, '{}'), ('volvo', 'V40', 9, '{}'), ('volvo', 'V70', 10, '{}'),
    ('skoda', 'Karoq', 6, '{карок}'), ('skoda', 'Yeti', 7, '{йети}'), ('skoda', 'Kamiq', 8, '{}'),
    ('renault', 'Fluence', 7, '{флюенс}'), ('renault', 'Koleos', 8, '{колеос}'), ('renault', 'Symbol', 9, '{симбол}'),
    ('renault', 'Clio', 10, '{клио}'), ('renault', 'Kangoo', 11, '{кангу}'), ('renault', 'Laguna', 12, '{лагуна}'),
    ('peugeot', '406', 7, '{}'), ('peugeot', '408', 8, '{}'), ('peugeot', '508', 9, '{}'), ('peugeot', '2008', 10, '{}'),
    ('peugeot', '5008', 11, '{}'), ('peugeot', 'Boxer', 12, '{боксер}'),
    ('land-rover', 'Range Rover Velar', 7, '{велар}'), ('land-rover', 'Discovery Sport', 8, '{дискавери спорт}'),
    ('jeep', 'Commander', 6, '{командер}'), ('jeep', 'Renegade', 7, '{ренегат}'), ('jeep', 'Patriot', 8, '{патриот}'),
    ('dodge', 'Journey', 6, '{джорни}'), ('dodge', 'Nitro', 7, '{нитро}'), ('dodge', 'Caravan', 8, '{караван}'),
    ('cadillac', 'XT5', 4, '{}'), ('cadillac', 'XT6', 5, '{}'), ('cadillac', 'CT6', 6, '{}'),
    ('gmc', 'Terrain', 4, '{}'), ('gmc', 'Savana', 5, '{}'),
    ('lincoln', 'Aviator', 4, '{авиатор}'), ('lincoln', 'Nautilus', 5, '{наутилус}'), ('lincoln', 'MKZ', 6, '{}'),
    ('tesla', 'Cybertruck', 5, '{кибертрак}'),

    ('chery', 'Tiggo 2', 6, '{тигго}'), ('chery', 'Tiggo 3', 7, '{тигго}'), ('chery', 'Tiggo 7 Pro', 8, '{тигго}'),
    ('chery', 'Tiggo 8 Pro', 9, '{тигго}'), ('chery', 'Tiggo 9', 10, '{тигго}'), ('chery', 'Bonus', 11, '{бонус}'),
    ('chery', 'QQ', 12, '{}'), ('chery', 'Fora', 13, '{фора}'),
    ('geely', 'Preface', 7, '{префейс}'), ('geely', 'Starray', 8, '{}'), ('geely', 'Cityray', 9, '{}'),
    ('geely', 'Galaxy L7', 10, '{гэлакси}'), ('geely', 'Geometry C', 11, '{}'), ('geely', 'MK', 12, '{}'), ('geely', 'CK', 13, '{}'),
    ('haval', 'M6', 6, '{}'), ('haval', 'H2', 7, '{}'), ('haval', 'F7x', 8, '{}'), ('haval', 'H5', 9, '{}'),
    ('tank', '400', 3, '{}'), ('tank', '700', 4, '{}'),
    ('byd', 'Song Pro', 9, '{сонг}'), ('byd', 'Song L', 10, '{сонг}'), ('byd', 'Seal U', 11, '{}'), ('byd', 'Dolphin', 12, '{дельфин}'),
    ('byd', 'Seagull', 13, '{}'), ('byd', 'Destroyer 05', 14, '{}'), ('byd', 'Frigate 07', 15, '{}'), ('byd', 'Leopard 5', 16, '{}'),
    ('byd', 'Sealion 07', 17, '{}'), ('byd', 'e2', 18, '{}'),
    ('changan', 'CS85', 7, '{}'), ('changan', 'CS95', 8, '{}'), ('changan', 'Alsvin', 9, '{}'), ('changan', 'Lamore', 10, '{}'),
    ('changan', 'Hunter', 11, '{}'), ('changan', 'Deepal S7', 12, '{дипал}'), ('changan', 'Qiyuan A05', 13, '{}'),
    ('jac', 'JS6', 4, '{}'), ('jac', 'S3', 5, '{}'), ('jac', 'N-series', 6, '{}'),
    ('exeed', 'RX', 4, '{}'), ('exeed', 'Yaoguang', 5, '{}'),
    ('omoda', 'C7', 3, '{}'), ('omoda', 'E5', 4, '{}'),
    ('jetour', 'X50', 5, '{}'), ('jetour', 'T2', 6, '{}'),
    ('li-auto', 'L6', 5, '{}'), ('li-auto', 'MEGA', 6, '{}'),
    ('zeekr', 'X', 4, '{}'), ('zeekr', '007', 5, '{}'),
    ('hongqi', 'H9', 4, '{}'), ('hongqi', 'HS3', 5, '{}'),
    ('dongfeng', 'Aeolus', 4, '{}'), ('dongfeng', 'Forthing T5', 5, '{}'),
    ('voyah', 'Passion', 3, '{}'), ('voyah', 'Courage', 4, '{}'),
    ('lynk-co', '06', 4, '{}'), ('lynk-co', '08', 5, '{}')
) AS m(slug, name, sort_order, aliases)
JOIN brands b ON b.slug = m.slug
WHERE NOT EXISTS (SELECT 1 FROM models x WHERE x.brand_id = b.id AND x.name = m.name);

-- Справочник поменялся — клиенты перечитают марки.
UPDATE dictionary_version SET version = version + 1, updated_at = now() WHERE id = 1;
