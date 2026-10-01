package kg.kudaibergen.admin.access;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.admin.access.StaffDtos.UpdateStaffRequest;
import kg.kudaibergen.auth.token.RefreshTokenService;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.security.AdminRole;
import kg.kudaibergen.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffRulesTest {

   private final AdminMemberRepository members = mock(AdminMemberRepository.class);
   private final StaffService staff = new StaffService(members, mock(UserService.class), mock(AdminPermissions.class),
         mock(RefreshTokenService.class), mock(NamedParameterJdbcTemplate.class));

   @Test
   void последнегоСуперадминаНельзяОтключитьИлиПонизить() {
      AdminMember only = new AdminMember(1L, AdminRole.SUPER_ADMIN, "Владелец", "Суперадмин", null);
      when(members.findById(1L)).thenReturn(Optional.of(only));
      when(members.findAll()).thenReturn(List.of(only));

      assertThatThrownBy(() -> staff.update(1L, new UpdateStaffRequest(null, null, null, false), 2L))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("LAST_SUPER_ADMIN");
      assertThatThrownBy(() -> staff.update(1L, new UpdateStaffRequest(AdminRole.MARKET_ADMIN, null, null, null), 2L))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("LAST_SUPER_ADMIN");
   }

   @Test
   void себяНельзяОтключитьИлиСменитьРоль() {
      AdminMember me = new AdminMember(2L, AdminRole.SUPER_ADMIN, "Я", "Суперадмин", null);
      when(members.findById(2L)).thenReturn(Optional.of(me));

      assertThatThrownBy(() -> staff.update(2L, new UpdateStaffRequest(null, null, null, false), 2L))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("STAFF_SELF");
   }

   @Test
   void праваСуперадминаНеМеняются() {
      assertThatThrownBy(() -> staff.setRolePermissions(AdminRole.SUPER_ADMIN, java.util.Set.of()))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("ROLE_FIXED");
   }
}
