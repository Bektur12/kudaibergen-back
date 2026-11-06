package kg.kudaibergen.auth.sms;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Номер для Green-API: только цифры с кодом страны и «@c.us». */
class GreenApiWhatsAppProviderTest {

   @Test
   void номерВChatId() {
      assertThat(GreenApiWhatsAppProvider.chatId("+996555123456")).isEqualTo("996555123456@c.us");
      assertThat(GreenApiWhatsAppProvider.chatId("+996 555 12-34-56")).isEqualTo("996555123456@c.us");
   }
}
