package kg.kudaibergen.shop;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Авто-активация при регистрации — флажки админки (таблица app_settings): включено — новый магазин / мастер
 * действует сразу, выключено — ждёт подтверждения администрации. Флажок ещё не трогали — по
 * SHOP_VERIFICATION_REQUIRED ({@link ShopProperties}).
 */
@Component
public class ApprovalSettings {

   public static final String SELLERS = "sellers.auto_approve";
   public static final String MASTERS = "masters.auto_approve";

   private final JdbcTemplate jdbc;
   private final boolean autoByDefault;

   public ApprovalSettings(JdbcTemplate jdbc, ShopProperties properties) {
      this.jdbc = jdbc;
      this.autoByDefault = !properties.verificationRequired();
   }

   /** Для тестов: флажки не читаются из базы. */
   public static ApprovalSettings constant(boolean auto) {
      return new ApprovalSettings(null, new ShopProperties(!auto)) {
         @Override
         boolean flag(String key) {
            return auto;
         }
      };
   }

   public boolean sellersAutoApprove() {
      return flag(SELLERS);
   }

   public boolean mastersAutoApprove() {
      return flag(MASTERS);
   }

   public void set(String key, boolean auto, Long adminId) {
      jdbc.update("""
            insert into app_settings (key, value, updated_at, updated_by) values (?, ?, now(), ?)
            on conflict (key) do update set value = excluded.value, updated_at = now(), updated_by = excluded.updated_by
            """, key, String.valueOf(auto), adminId);
   }

   boolean flag(String key) {
      List<String> value = jdbc.queryForList("select value from app_settings where key = ?", String.class, key);
      return value.isEmpty() ? autoByDefault : Boolean.parseBoolean(value.get(0));
   }
}
