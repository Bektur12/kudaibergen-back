package kg.kudaibergen.auth.sms;

/** Шлюз SMS. Реализация выбирается app.sms.provider: log (заглушка), nikita (nikita.kg) или whatsapp (Green-API). */
public interface SmsProvider {

   void send(String phone, String text);
}
