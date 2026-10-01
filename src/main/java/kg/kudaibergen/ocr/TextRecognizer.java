package kg.kudaibergen.ocr;

/** Текст с фото. Реализация выбирается app.ocr.provider. */
public interface TextRecognizer {

   /** Весь распознанный текст (строки через перевод строки); пустая строка — текста нет. */
   String recognize(byte[] image);
}
