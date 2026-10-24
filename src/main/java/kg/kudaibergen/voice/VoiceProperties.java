package kg.kudaibergen.voice;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Распознавание голосовых (ответ голосом 14, 19 и запрос голосом 04, 16).
 * provider: none — выключено (голосовое сохраняется, поля продавец заполняет сам), openai — сервис с API
 * OpenAI /v1/audio/transcriptions (OpenAI Whisper или совместимый: свой faster-whisper, Groq и т. п.).
 * perUserHourly — сколько распознаваний в час на человека (платный сервис); сверх лимита голосовое
 * сохраняется без текста.
 */
@ConfigurationProperties(prefix = "app.voice")
public record VoiceProperties(String provider, OpenAi openai, int perUserHourly) {

   /** prompt — подсказка распознавателю: слова рынка, чтобы «б/у», «KYB» и «три двести» писались как надо. */
   public record OpenAi(String apiKey, String url, String model, String prompt, Duration timeout) {
   }
}
