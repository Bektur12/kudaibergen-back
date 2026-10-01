package kg.kudaibergen.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.shop.dto.ShopPhotoDto;
import kg.kudaibergen.shop.entity.MemberRole;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopPhotoServiceTest {

   private static final long OWNER = 7L;

   private Shop shop;
   private ShopPhotoService service;

   @BeforeEach
   void setUp() {
      shop = new Shop(OWNER, "Автодеталь Азамат", 10L);
      ShopAccess access = mock(ShopAccess.class);
      ShopMember member = new ShopMember(1L, OWNER, MemberRole.OWNER);
      when(access.requireOwner(OWNER)).thenReturn(new ShopAccess.Membership(shop, member));
      ShopMemberRepository members = mock(ShopMemberRepository.class);
      when(members.findByShopIdOrderByCreatedAtAsc(any())).thenReturn(List.of(member));
      MediaService media = mock(MediaService.class);
      when(media.photos(any())).thenReturn(Map.of());
      service = new ShopPhotoService(access, members, mock(ShopRepository.class), media);
   }

   @Test
   void первоеФотоОбложкаИЛимитВосемь() {
      for (long id = 1; id <= 8; id++) {
         service.add(OWNER, id);
      }
      List<ShopPhotoDto> photos = service.add(OWNER, 3L);
      assertThat(photos).hasSize(8);
      assertThat(photos.get(0).isCover()).isTrue();
      assertThat(photos.get(1).isCover()).isFalse();
      assertThatThrownBy(() -> service.add(OWNER, 9L)).isInstanceOf(ConflictException.class);
   }

   @Test
   void обложкаИПерестановка() {
      service.add(OWNER, 1L);
      service.add(OWNER, 2L);
      service.add(OWNER, 3L);
      assertThat(ids(service.makeCover(OWNER, 3L))).containsExactly(3L, 1L, 2L);
      assertThat(ids(service.reorder(OWNER, List.of(2L, 3L, 1L)))).containsExactly(2L, 3L, 1L);
      assertThatThrownBy(() -> service.reorder(OWNER, List.of(2L, 3L))).isInstanceOf(BadRequestException.class);
      assertThat(ids(service.remove(OWNER, 3L))).containsExactly(2L, 1L);
   }

   private static List<Long> ids(List<ShopPhotoDto> photos) {
      return new ArrayList<>(photos.stream().map(ShopPhotoDto::id).toList());
   }
}
