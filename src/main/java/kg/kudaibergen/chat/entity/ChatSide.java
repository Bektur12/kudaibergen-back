package kg.kudaibergen.chat.entity;

/** Сторона чата. SHOP — любой человек бокса (владелец или сотрудник), SYSTEM — плашки приложения. */
public enum ChatSide {
   BUYER,
   SHOP,
   SYSTEM;

   public ChatSide other() {
      return this == BUYER ? SHOP : BUYER;
   }
}
