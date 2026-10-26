package kg.kudaibergen.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import kg.kudaibergen.request.entity.PartCondition;
import org.junit.jupiter.api.Test;

/** «Выложить видео»: что продавец сказал в видео → поля товара. */
class VoicePartParserTest {

   private static final List<VoiceParser.ModelOption> MODELS = List.of(
         new VoiceParser.ModelOption(1L, 10L, "Camry 40", "camry", "40", List.of("камри", "xv40")),
         new VoiceParser.ModelOption(2L, 10L, "Camry 50", "camry", "50", List.of("камри", "xv50", "xv55")),
         new VoiceParser.ModelOption(3L, 10L, "Camry 70", "camry", "70", List.of("камри", "xv70")),
         new VoiceParser.ModelOption(4L, 10L, "Corolla 150", "corolla", "150", List.of("королла", "корола")),
         new VoiceParser.ModelOption(5L, 11L, "Lexus RX 350", "rx", "350", List.of("рх", "эрикс")));

   @Test
   void fromDesign() {
      VoiceParser.PartDraft draft = VoiceParser.part("Стойки KYB на Камри пятьдесят, новые, пара — три двести",
            MODELS);
      assertThat(draft.title()).isEqualTo("Стойки KYB, пара");
      assertThat(draft.models()).extracting(VoiceParser.ModelOption::modelId).containsExactly(2L);
      assertThat(draft.price()).isEqualTo(3200);
      assertThat(draft.condition()).isEqualTo(PartCondition.NEW);
   }

   @Test
   void generationByAliasAndSeveralCars() {
      VoiceParser.PartDraft draft = VoiceParser.part(
            "Вот фара левая для камри пятьдесят пять и короллы, б/у, четыре пятьсот", MODELS);
      assertThat(draft.title()).isEqualTo("Фара левая");
      // «пятьдесят пять» — алиас xv55 у Camry 50; «короллы» по алиасу не совпало целиком — без неё
      assertThat(draft.models()).extracting(VoiceParser.ModelOption::modelId).contains(2L);
      assertThat(draft.condition()).isEqualTo(PartCondition.USED);
      assertThat(draft.price()).isEqualTo(4500);
   }

   @Test
   void singleModelWithoutGenerationAndDigits() {
      VoiceParser.PartDraft draft = VoiceParser.part("Колодки передние на королла 1400 сом", MODELS);
      assertThat(draft.title()).isEqualTo("Колодки передние");
      assertThat(draft.models()).extracting(VoiceParser.ModelOption::modelId).containsExactly(4L);
      assertThat(draft.price()).isEqualTo(1400);
   }

   @Test
   void ambiguousModelWithoutNumberIsSkipped() {
      VoiceParser.PartDraft draft = VoiceParser.part("Бампер на камри, три тысячи", MODELS);
      assertThat(draft.title()).isEqualTo("Бампер");
      assertThat(draft.models()).isEmpty();
      assertThat(draft.price()).isEqualTo(3000);
   }

   @Test
   void nothingRecognized() {
      VoiceParser.PartDraft draft = VoiceParser.part(null, MODELS);
      assertThat(draft.title()).isNull();
      assertThat(draft.price()).isNull();
      assertThat(draft.models()).isEmpty();
   }
}
