package kg.kudaibergen.user;

import kg.kudaibergen.user.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

   @Modifying
   @Query(nativeQuery = true, value = "insert into user_settings (user_id) values (:userId) on conflict do nothing")
   void insertDefaults(@Param("userId") Long userId);
}
