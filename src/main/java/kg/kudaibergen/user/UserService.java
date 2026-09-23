package kg.kudaibergen.user;

import java.time.Instant;

import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.user.dto.UpdateMeRequest;
import kg.kudaibergen.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

   private final UserRepository users;

   public UserService(UserRepository users) {
      this.users = users;
   }

   @Transactional(readOnly = true)
   public User getRequired(Long id) {
      return users.findById(id)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Пользователь не найден"));
   }

   @Transactional
   public User updateProfile(Long userId, UpdateMeRequest request) {
      User user = getRequired(userId);
      if (request.name() != null && !request.name().isBlank()) {
         user.setName(request.name().trim());
      }
      if (request.city() != null && !request.city().isBlank()) {
         user.setCity(request.city().trim());
      }
      return user;
   }

   @Transactional
   public void touchLastSeen(Long userId, Instant at) {
      users.touchLastSeen(userId, at);
   }

   @Transactional(readOnly = true)
   public Instant lastSeenAt(Long userId) {
      return users.findLastSeenAt(userId);
   }
}
