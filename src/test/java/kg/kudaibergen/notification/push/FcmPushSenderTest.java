package kg.kudaibergen.notification.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class FcmPushSenderTest {

   @Test
   void pushWithButtonsGoesDataOnlyToTheRequestsChannel() {
      Map<String, String> data = FcmPushSender.androidData(
            new PushMessage("Camry 50", "Фара левая", Map.of("type", "NEW_REQUEST"), "NEW_REQUEST", true));

      assertThat(data).containsEntry("title", "Camry 50")
            .containsEntry("message", "Фара левая")
            .containsEntry("categoryId", "NEW_REQUEST")
            .containsEntry("channelId", FcmPushSender.CHANNEL_REQUESTS);
   }

   @Test
   void quietPushUsesTheSilentChannelAndSkipsAMissingBody() {
      Map<String, String> data = FcmPushSender.androidData(
            new PushMessage("Camry 50", null, Map.of(), "NEW_REQUEST", false));

      assertThat(data).containsEntry("channelId", FcmPushSender.CHANNEL_REQUESTS_SILENT).doesNotContainKey("message");
   }
}
