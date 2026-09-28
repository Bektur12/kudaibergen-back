package kg.kudaibergen.auth.otp;

/** Для чего выпущен код: коды разных назначений хранятся раздельно и не подменяют друг друга. */
public enum OtpPurpose {
   LOGIN("login"),
   DELETE_ACCOUNT("delete");

   private final String key;

   OtpPurpose(String key) {
      this.key = key;
   }

   public String key() {
      return key;
   }
}
