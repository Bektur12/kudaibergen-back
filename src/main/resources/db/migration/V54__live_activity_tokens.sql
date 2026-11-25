-- Live Activity на iPhone (iOS 17.2+): карточка запроса на экране блокировки с кнопками «Есть / Нет».
-- live_activity_token — push-to-start токен устройства: по нему APNs сам запускает карточку, приложение может быть закрыто.
ALTER TABLE device_tokens ADD COLUMN live_activity_token VARCHAR(255);
