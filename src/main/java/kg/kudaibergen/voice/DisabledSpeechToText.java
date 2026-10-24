package kg.kudaibergen.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Распознавание не настроено: голосовое сохраняется без текста, цену и состояние продавец выбирает сам. */
@Component
@ConditionalOnProperty(name = "app.voice.provider", havingValue = "none", matchIfMissing = true)
public class DisabledSpeechToText implements SpeechToText {

   @Override
   public String transcribe(byte[] audio, String mimeType, String fileName) {
      return null;
   }
}
