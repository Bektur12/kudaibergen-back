-- «Обновите приложение»: последняя версия в сторе и минимальная, ниже которой приложение не пускает дальше.
-- IOS latest_version подтягивается из App Store сам (AppReleaseService), ANDROID — вручную после выкладки в Google Play:
--   UPDATE app_releases SET latest_version = '1.0.6' WHERE platform = 'ANDROID';
CREATE TABLE app_releases (
    platform       VARCHAR(7)   PRIMARY KEY CHECK (platform IN ('IOS', 'ANDROID')),
    latest_version VARCHAR(20)  NOT NULL,
    min_version    VARCHAR(20)  NOT NULL DEFAULT '0.0.0',
    store_url      VARCHAR(300) NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
INSERT INTO app_releases (platform, latest_version, store_url) VALUES
    ('IOS', '1.0.4', 'https://apps.apple.com/app/id6830052168'),
    ('ANDROID', '0.0.0', 'https://play.google.com/store/apps/details?id=kg.kudaiberdi.app');
