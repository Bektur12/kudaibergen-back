-- Когда пользователь последний раз был на связи по WebSocket (для «был в сети N назад»).
ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMPTZ;
