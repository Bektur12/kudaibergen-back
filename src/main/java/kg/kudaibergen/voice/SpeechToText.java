package kg.kudaibergen.voice;

/** Речь в текст. Реализация выбирается app.voice.provider. */
public interface SpeechToText {

   /** Что сказали; null — распознавание выключено или не удалось (голосовое всё равно сохраняется). */
   String transcribe(byte[] audio, String mimeType, String fileName);
}
