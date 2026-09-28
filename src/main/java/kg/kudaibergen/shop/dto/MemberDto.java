package kg.kudaibergen.shop.dto;

import kg.kudaibergen.shop.entity.MemberRole;

/** «Продавцы в боксе» (экран 21). */
public record MemberDto(Long userId, String name, String phone, MemberRole role) {
}
