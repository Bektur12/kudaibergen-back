package kg.kudaibergen.ocr;

import java.util.List;

/**
 * Номер детали с фото (камера 25 «Номер детали», камера в строке поиска 27). candidates — от самого
 * вероятного; partsFound — сколько опубликованных запчастей с таким номером уже есть на рынке.
 * Пустой список — номер не найден, клиент просит переснять или ввести вручную.
 */
public record OemRecognitionDto(List<CandidateDto> candidates) {

   public record CandidateDto(String oem, String normalized, long partsFound) {
   }
}
