-- ─────────────────────────── ПОЛЬЗОВАТЕЛИ ───────────────────────────
CREATE TYPE user_role AS ENUM ('BUYER', 'SELLER');

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    phone         VARCHAR(20)  NOT NULL UNIQUE,   -- +996XXXXXXXXX
    name          VARCHAR(120),
    role          user_role    NOT NULL,
    city          VARCHAR(80)  NOT NULL DEFAULT 'Бишкек',
    is_blocked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Одноразовые коды для входа
CREATE TABLE sms_codes (
    id          BIGSERIAL PRIMARY KEY,
    phone       VARCHAR(20) NOT NULL,
    code_hash   VARCHAR(80) NOT NULL,   -- хранить хэш, не сам код
    attempts    SMALLINT    NOT NULL DEFAULT 0,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sms_codes_phone ON sms_codes (phone, created_at DESC);

-- ─────────────────────────── АВТОМОБИЛИ ───────────────────────────
CREATE TABLE vehicles (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    brand       VARCHAR(60) NOT NULL,          -- Toyota
    model       VARCHAR(60) NOT NULL,          -- Camry
    generation  VARCHAR(60),                   -- XV50
    year        SMALLINT,
    engine      VARCHAR(40),                   -- 2.5 бензин
    body_type   VARCHAR(40),
    vin         VARCHAR(17),
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vehicles_user ON vehicles (user_id);
-- у пользователя не больше одной машины по умолчанию
CREATE UNIQUE INDEX uq_vehicle_default ON vehicles (user_id) WHERE is_default;

-- ─────────────────────────── МАГАЗИНЫ ───────────────────────────
CREATE TYPE verification_status AS ENUM ('NEW', 'VERIFIED', 'TRUSTED', 'BLOCKED');

CREATE TABLE stores (
    id                  BIGSERIAL PRIMARY KEY,
    owner_user_id       BIGINT      NOT NULL REFERENCES users(id),
    name                VARCHAR(120) NOT NULL,
    business_type       VARCHAR(40)  NOT NULL,   -- parts / tires / oils / accessories
    description         TEXT,
    verification_status verification_status NOT NULL DEFAULT 'NEW',
    rating              NUMERIC(2,1) NOT NULL DEFAULT 0,
    review_count        INT          NOT NULL DEFAULT 0,
    total_deals         INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_stores_owner ON stores (owner_user_id);

CREATE TABLE store_branches (
    id          BIGSERIAL PRIMARY KEY,
    store_id    BIGINT      NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    address     VARCHAR(200) NOT NULL,
    city        VARCHAR(80)  NOT NULL,
    phone       VARCHAR(20),
    latitude    NUMERIC(9,6),
    longitude   NUMERIC(9,6),
    work_hours  JSONB,       -- {"open":"09:00","close":"19:00","days":["Пн",...]}
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_branches_city ON store_branches (city);

-- Сердце матчинга: какими категориями торгует магазин.
CREATE TABLE store_categories (
    store_id  BIGINT      NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    category  VARCHAR(40) NOT NULL,
    PRIMARY KEY (store_id, category)
);
CREATE INDEX idx_store_categories_category ON store_categories (category);

-- Шаблоны быстрых ответов продавца
CREATE TABLE reply_templates (
    id          BIGSERIAL PRIMARY KEY,
    store_id    BIGINT      NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    title       VARCHAR(60) NOT NULL,       -- "Есть в наличии"
    body        TEXT        NOT NULL,
    sort_order  SMALLINT    NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_templates_store ON reply_templates (store_id, sort_order);

-- ─────────────────────────── ЗАПРОСЫ ───────────────────────────
CREATE TYPE request_status AS ENUM ('ACTIVE', 'COMPLETED', 'EXPIRED', 'CANCELLED');

CREATE TABLE requests (
    id           BIGSERIAL PRIMARY KEY,
    buyer_id     BIGINT      NOT NULL REFERENCES users(id),
    vehicle_id   BIGINT      REFERENCES vehicles(id) ON DELETE SET NULL,
    car_text     VARCHAR(160),               -- если машину указали текстом
    category     VARCHAR(40) NOT NULL,
    description  TEXT        NOT NULL,
    budget_min   INT,
    budget_max   INT,
    currency     CHAR(3)     NOT NULL DEFAULT 'KGS',
    city         VARCHAR(80) NOT NULL,
    is_urgent    BOOLEAN     NOT NULL DEFAULT FALSE,
    status       request_status NOT NULL DEFAULT 'ACTIVE',
    offer_count  INT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_requests_buyer  ON requests (buyer_id, created_at DESC);
CREATE INDEX idx_requests_expiry ON requests (status, expires_at);

-- Кому улетел запрос + состояние по каждому продавцу.
CREATE TABLE request_recipients (
    request_id  BIGINT      NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
    store_id    BIGINT      NOT NULL REFERENCES stores(id)   ON DELETE CASCADE,
    notified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    seen_at     TIMESTAMPTZ,
    replied_at  TIMESTAMPTZ,
    PRIMARY KEY (request_id, store_id)
);
CREATE INDEX idx_recipients_store ON request_recipients (store_id, notified_at DESC);

-- ─────────────────────────── ПРЕДЛОЖЕНИЯ ───────────────────────────
CREATE TYPE offer_status AS ENUM ('ACTIVE', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED');

CREATE TABLE offers (
    id             BIGSERIAL PRIMARY KEY,
    request_id     BIGINT      NOT NULL REFERENCES requests(id) ON DELETE CASCADE,
    store_id       BIGINT      NOT NULL REFERENCES stores(id),
    price          INT,                        -- в сомах; NULL для шаблона "нет в наличии"
    currency       CHAR(3)     NOT NULL DEFAULT 'KGS',
    comment        TEXT,
    delivery_days  SMALLINT,
    status         offer_status NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- один магазин — одно активное предложение на запрос
CREATE UNIQUE INDEX uq_offer_per_store ON offers (request_id, store_id)
    WHERE status IN ('ACTIVE', 'ACCEPTED');
CREATE INDEX idx_offers_store ON offers (store_id, created_at DESC);

-- ─────────────────────────── ЧАТЫ ───────────────────────────
CREATE TABLE chats (
    id              BIGSERIAL PRIMARY KEY,
    request_id      BIGINT REFERENCES requests(id) ON DELETE SET NULL,
    buyer_id        BIGINT NOT NULL REFERENCES users(id),
    store_id        BIGINT NOT NULL REFERENCES stores(id),
    last_message    TEXT,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (buyer_id, store_id, request_id)
);
CREATE INDEX idx_chats_buyer ON chats (buyer_id, last_message_at DESC);
CREATE INDEX idx_chats_store ON chats (store_id, last_message_at DESC);

CREATE TABLE messages (
    id         BIGSERIAL PRIMARY KEY,
    chat_id    BIGINT      NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id  BIGINT      NOT NULL REFERENCES users(id),
    body       TEXT        NOT NULL,
    type       VARCHAR(10) NOT NULL DEFAULT 'TEXT',   -- TEXT / PHOTO
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_chat ON messages (chat_id, created_at DESC);

-- ─────────────────────────── ОТЗЫВЫ ───────────────────────────
CREATE TABLE reviews (
    id         BIGSERIAL PRIMARY KEY,
    author_id  BIGINT      NOT NULL REFERENCES users(id),
    store_id   BIGINT      NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    offer_id   BIGINT      REFERENCES offers(id) ON DELETE SET NULL,
    rating     SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    text       TEXT,
    status     VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reviews_store ON reviews (store_id, created_at DESC);

-- ─────────────────────────── ТЕХНИЧЕСКОЕ ───────────────────────────
-- Защита от дублей при обрыве связи
CREATE TABLE idempotency_keys (
    key          VARCHAR(80) PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    response     JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Исходящие уведомления: пуши не шлём в HTTP-потоке
CREATE TABLE notification_outbox (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    title        VARCHAR(120) NOT NULL,
    body         VARCHAR(400) NOT NULL,
    payload      JSONB,
    sent_at      TIMESTAMPTZ,
    attempts     SMALLINT    NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_unsent ON notification_outbox (created_at) WHERE sent_at IS NULL;

CREATE TABLE device_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    platform   VARCHAR(10)  NOT NULL,   -- IOS / ANDROID
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_device_tokens_user ON device_tokens (user_id);
