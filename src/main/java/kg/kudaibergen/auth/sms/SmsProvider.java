package kg.kudaibergen.auth.sms;

/** Шлюз SMS. Реализация выбирается app.sms.provider: log (заглушка) или nikita (nikita.kg). */
public interface SmsProvider {

   void send(String phone, String text);
}
