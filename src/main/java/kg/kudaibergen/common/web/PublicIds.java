package kg.kudaibergen.common.web;

import java.security.SecureRandom;

/**
 * Публичный id для ссылок «Поделиться»: 10 символов base62 (~59 бит) — не перебрать и не угадать
 * соседний, в отличие от числового id. Уникальность подстрахована UNIQUE-индексом.
 */
public final class PublicIds {

   private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
   private static final int LENGTH = 10;
   private static final SecureRandom RANDOM = new SecureRandom();

   private PublicIds() {
   }

   public static String next() {
      StringBuilder id = new StringBuilder(LENGTH);
      for (int i = 0; i < LENGTH; i++) {
         id.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
      }
      return id.toString();
   }
}
