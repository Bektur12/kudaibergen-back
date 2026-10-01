-- ─────────────────────────── АДМИНКА: РЫНОК И КАРТА (A3) ───────────────────────────
-- Опубликованные версии схемы — map_versions (клиент получает текущую). Черновик — один на рынок:
-- правится сколько угодно, приложения его не видят, публикация делает из него новую версию.
ALTER TABLE map_versions ADD COLUMN published_by BIGINT REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE map_drafts (
    id                SMALLINT     PRIMARY KEY CHECK (id = 1),
    -- схема в формате загрузки: market-map.json + ряды с кодами
    data              JSONB        NOT NULL,
    based_on_version  INT          NOT NULL,
    updated_by        BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
