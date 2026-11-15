-- Пуши с кнопками («Есть / Нет», «Написать / Как пройти»): на Android их рисует само приложение (data-only),
-- старые версии такой пуш не покажут. Новое приложение при регистрации устройства присылает actions=true;
-- остальным на Android уходит тот же пуш без кнопок.
ALTER TABLE device_tokens ADD COLUMN push_actions BOOLEAN NOT NULL DEFAULT FALSE;
