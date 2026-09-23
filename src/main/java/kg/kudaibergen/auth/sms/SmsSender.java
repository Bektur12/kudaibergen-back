package kg.kudaibergen.auth.sms;

/** Шлюз SMS. Реализация подменяется провайдером (nikita.kg / smsc.kg) без правок сервисов. */
public interface SmsSender {

   void send(String phone, String text);
}
