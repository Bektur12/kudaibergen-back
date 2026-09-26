package kg.kudaibergen.chat.dto;

/**
 * Единый конверт для канала {@code chat:{id}#...} — клиент различает событие по {@code type}.
 * TYPING в этот конверт не попадает: это единственное событие, которое участники чата шлют друг
 * другу напрямую через Centrifugo (channel-опция allow_publish_for_subscriber), без бэкенда —
 * оно и раньше было чисто ephemeral, без истории и без бизнес-логики.
 */
public record ChatEvent(String type, Object payload) {

   public static ChatEvent message(MessageResponse message) {
      return new ChatEvent("MESSAGE", message);
   }

   public static ChatEvent read(ReadEvent event) {
      return new ChatEvent("READ", event);
   }
}
