package kg.kudaibergen.admin.access;

import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AdminRole;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class PhonesTest {

   private final ObjectMapper json = new ObjectMapper();

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   void маскаКакВМакете() {
      assertThat(Phones.mask("+996555123456")).isEqualTo("+996 *** ** 34 56");
      assertThat(Phones.mask("12")).isEqualTo("***");
   }

   @Test
   void безПраваPiiТелефонМаскируетсяВJson() throws Exception {
      viewer(Set.of(AdminPermission.SELLERS_VIEW));
      assertThat(json.writeValueAsString(new Row("+996700100001"))).contains("+996 *** ** 00 01");

      viewer(Set.of(AdminPermission.SELLERS_VIEW, AdminPermission.PII_VIEW));
      assertThat(json.writeValueAsString(new Row("+996700100001"))).contains("\"+996700100001\"");
   }

   @Test
   void nullОстаётсяNull() throws Exception {
      viewer(Set.of());
      assertThat(json.writeValueAsString(new Row(null))).isEqualTo("{\"phone\":null}");
   }

   private static void viewer(Set<AdminPermission> permissions) {
      AuthPrincipal principal = new AuthPrincipal(1L, "+996555000000", AdminRole.MARKET_ADMIN, permissions);
      SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of()));
   }

   record Row(@MaskedPhone String phone) {
   }
}
