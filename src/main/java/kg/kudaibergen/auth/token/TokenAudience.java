package kg.kudaibergen.auth.token;

/** Чья сессия: мобильного приложения или веб-админки. Refresh-токен одной не обменивается в другой. */
public enum TokenAudience {
   APP,
   ADMIN
}
