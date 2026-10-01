package kg.kudaibergen.shop.dto;

import kg.kudaibergen.shop.entity.MemberRole;
import org.springframework.lang.Nullable;

/** «Продавцы в боксе» (экран 21). */
public record MemberDto(Long userId, @Nullable String name, String phone, MemberRole role) {
}
