package kg.kudaibergen.auth;

import java.util.Optional;

import kg.kudaibergen.auth.entity.SmsCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmsCodeRepository extends JpaRepository<SmsCode, Long> {

   Optional<SmsCode> findTopByPhoneOrderByCreatedAtDesc(String phone);
}
