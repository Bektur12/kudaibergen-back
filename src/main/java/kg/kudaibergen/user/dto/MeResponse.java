package kg.kudaibergen.user.dto;

import java.time.Instant;

import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.UserRole;

public record MeResponse(Long id, String phone, String name, UserRole role, String city, Instant createdAt) {

   public static MeResponse of(User user) {
      return new MeResponse(user.getId(), user.getPhone(), user.getName(), user.getRole(),
            user.getCity(), user.getCreatedAt());
   }
}
