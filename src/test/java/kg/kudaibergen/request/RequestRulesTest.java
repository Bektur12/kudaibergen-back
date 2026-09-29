package kg.kudaibergen.request;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import kg.kudaibergen.common.config.ClockConfig;
import kg.kudaibergen.request.dto.RequestState;
import kg.kudaibergen.request.entity.PartRequest;
import kg.kudaibergen.request.entity.RecipientStatus;
import kg.kudaibergen.request.entity.ReplyAnswer;
import kg.kudaibergen.request.entity.RequestDuration;
import kg.kudaibergen.request.entity.RequestRecipient;
import kg.kudaibergen.request.entity.RequestStatus;
import kg.kudaibergen.request.entity.RequestTarget;
import kg.kudaibergen.user.entity.Lang;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestRulesTest {

   /** Вторник, 29.09.2026, Бишкек. */
   private static Instant at(int hour, int minute) {
      return ZonedDateTime.of(2026, 9, 29, hour, minute, 0, 0, ClockConfig.MARKET_ZONE).toInstant();
   }

   private static PartRequest request(RequestDuration duration, Instant now) {
      return new PartRequest(1L, 2L, 3L, 4L, (short) 2012, "Стойки передние", null, RequestTarget.MARKET,
            List.of(), List.of(), List.of(), duration, now);
   }

   @Test
   void срокПоВыборуПокупателя() {
      Instant now = at(10, 0);
      assertThat(RequestDuration.MIN_30.expiresAt(now)).isEqualTo(at(10, 30));
      assertThat(RequestDuration.HOUR_1.expiresAt(now)).isEqualTo(at(11, 0));
      assertThat(RequestDuration.HOUR_3.expiresAt(now)).isEqualTo(at(13, 0));
   }

   @Test
   void доКонцаДняДо17АПозжеТриЧаса() {
      assertThat(RequestDuration.END_OF_DAY.expiresAt(at(9, 15))).isEqualTo(at(17, 0));
      assertThat(RequestDuration.END_OF_DAY.expiresAt(at(17, 0))).isEqualTo(at(20, 0));
      assertThat(RequestDuration.END_OF_DAY.expiresAt(at(18, 30))).isEqualTo(at(21, 30));
   }

   @Test
   void продлениеАктивногоСдвигаетСрок() {
      PartRequest request = request(RequestDuration.MIN_30, at(10, 0));
      request.extend(Duration.ofMinutes(60), at(10, 10));
      assertThat(request.getExpiresAt()).isEqualTo(at(11, 30));
      assertThat(request.getSentAt()).isEqualTo(at(10, 0));
      assertThat(request.getExtendedTimes()).isEqualTo(1);
   }

   @Test
   void продлениеИстёкшегоСноваАктивноОтСейчас() {
      PartRequest request = request(RequestDuration.MIN_30, at(10, 0));
      request.expire(at(10, 30));
      assertThat(RequestMapper.state(request)).isEqualTo(RequestState.NO_ANSWERS);
      request.extend(Duration.ofMinutes(30), at(12, 0));
      assertThat(request.getStatus()).isEqualTo(RequestStatus.ACTIVE);
      assertThat(request.getSentAt()).isEqualTo(at(12, 0));
      assertThat(request.getExpiresAt()).isEqualTo(at(12, 30));
      assertThat(request.getClosedAt()).isNull();
   }

   @Test
   void продлитьМожноТриРазаИНеЗакрытый() {
      PartRequest request = request(RequestDuration.MIN_30, at(10, 0));
      for (int i = 0; i < PartRequest.MAX_EXTENSIONS; i++) {
         assertThat(request.canExtend()).isTrue();
         request.extend(Duration.ofMinutes(30), at(10, 5));
      }
      assertThat(request.canExtend()).isFalse();

      PartRequest closed = request(RequestDuration.MIN_30, at(10, 0));
      closed.close(null, at(10, 5));
      assertThat(closed.canExtend()).isFalse();
   }

   @Test
   void расширениеНаРынокОтсчитываетСрокЗаново() {
      PartRequest request = new PartRequest(1L, 2L, 3L, 4L, (short) 2012, "Фара", null, RequestTarget.ROWS,
            List.of(10L, 11L), List.of(), List.of(), RequestDuration.HOUR_1, at(10, 0));
      request.expire(at(11, 0));
      request.widenToMarket(at(13, 0));
      assertThat(request.getTarget()).isEqualTo(RequestTarget.MARKET);
      assertThat(request.getTargetRowIds()).isEmpty();
      assertThat(request.isActive()).isTrue();
      assertThat(request.getExpiresAt()).isEqualTo(at(14, 0));
   }

   @Test
   void состояниеДляПокупателя() {
      PartRequest request = request(RequestDuration.MIN_30, at(10, 0));
      assertThat(RequestMapper.state(request)).isEqualTo(RequestState.WAITING);
      request.haveCountChanged(1);
      assertThat(RequestMapper.state(request)).isEqualTo(RequestState.HAS_ANSWERS);
      request.expire(at(10, 30));
      assertThat(RequestMapper.state(request)).isEqualTo(RequestState.EXPIRED);
      assertThat(request.isOpen()).isTrue();
      request.close(5L, at(11, 0));
      assertThat(RequestMapper.state(request)).isEqualTo(RequestState.CLOSED);
   }

   @Test
   void статусПолучателя() {
      RequestRecipient recipient = new RequestRecipient(1L, 2L, 3L, 4L, at(10, 0));
      assertThat(recipient.getStatus()).isEqualTo(RecipientStatus.DELIVERED);
      assertThat(recipient.seen(at(10, 1))).isTrue();
      assertThat(recipient.seen(at(10, 2))).isFalse();
      assertThat(recipient.getStatus()).isEqualTo(RecipientStatus.SEEN);
      recipient.answered(ReplyAnswer.NOT_HAVE, at(10, 3));
      assertThat(recipient.getStatus()).isEqualTo(RecipientStatus.NOT_HAVE);
      recipient.answered(ReplyAnswer.HAVE, at(10, 5));
      assertThat(recipient.getStatus()).isEqualTo(RecipientStatus.HAVE);
      assertThat(recipient.getRepliedAt()).isEqualTo(at(10, 3));
      assertThat(recipient.getSeenAt()).isEqualTo(at(10, 1));
   }

   @Test
   void склонениеОтветов() {
      assertThat(RequestTexts.expiredTitle(1, Lang.RU)).isEqualTo("Время вышло: 1 ответ");
      assertThat(RequestTexts.answers(3)).isEqualTo("ответа");
      assertThat(RequestTexts.answers(5)).isEqualTo("ответов");
      assertThat(RequestTexts.answers(11)).isEqualTo("ответов");
      assertThat(RequestTexts.answers(21)).isEqualTo("ответ");
      assertThat(RequestTexts.answers(24)).isEqualTo("ответа");
   }
}
