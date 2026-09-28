package kg.kudaibergen.shop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.media.MediaPurpose;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.shop.dto.ShopPhotoDto;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Аватар и фото места (экран 22, ТЗ 8): до 8 фото, первое — «Обложка», «Сфотать» / «Из галереи»,
 * удалить, перетащить, сделать обложкой. Меняет только владелец (ТЗ 2); фото могут быть сняты любым
 * человеком бокса (режим камеры «Бокс»).
 */
@Service
public class ShopPhotoService {

   static final int MAX_PHOTOS = 8;

   private final ShopAccess access;
   private final ShopMemberRepository members;
   private final ShopRepository shops;
   private final MediaService media;

   public ShopPhotoService(ShopAccess access, ShopMemberRepository members, ShopRepository shops,
                           MediaService media) {
      this.access = access;
      this.members = members;
      this.shops = shops;
      this.media = media;
   }

   @Transactional
   public void setAvatar(Long userId, Long mediaId) {
      Shop shop = access.requireOwner(userId).shop();
      media.requireUsable(List.of(mediaId), staff(shop), Set.of(MediaPurpose.AVATAR, MediaPurpose.SHOP));
      shop.setAvatarMediaId(mediaId);
   }

   @Transactional
   public void removeAvatar(Long userId) {
      access.requireOwner(userId).shop().setAvatarMediaId(null);
   }

   @Transactional(readOnly = true)
   public List<ShopPhotoDto> mine(Long userId) {
      return photos(access.requireMember(userId).shop());
   }

   /** Фото места для покупателя (30): только действующий магазин. */
   @Transactional(readOnly = true)
   public List<PhotoDto> publicPhotos(Long shopId) {
      Shop shop = shops.findById(shopId).filter(Shop::isActive)
            .orElseThrow(() -> new NotFoundException("SHOP_NOT_FOUND", "Магазин не найден"));
      return List.copyOf(media.photos(shop.getPhotoIds()).values());
   }

   /** Добавить в конец; счётчик «3 из 8». */
   @Transactional
   public List<ShopPhotoDto> add(Long userId, Long mediaId) {
      Shop shop = access.requireOwner(userId).shop();
      if (shop.getPhotoIds().contains(mediaId)) {
         return photos(shop);
      }
      if (shop.getPhotoIds().size() >= MAX_PHOTOS) {
         throw new ConflictException("PHOTOS_LIMIT", "Не больше " + MAX_PHOTOS + " фото места");
      }
      media.requireUsable(List.of(mediaId), staff(shop), Set.of(MediaPurpose.SHOP));
      List<Long> ids = new ArrayList<>(shop.getPhotoIds());
      ids.add(mediaId);
      shop.replacePhotos(ids);
      return photos(shop);
   }

   /** Перетаскивание: тот же набор фото в новом порядке. */
   @Transactional
   public List<ShopPhotoDto> reorder(Long userId, List<Long> order) {
      Shop shop = access.requireOwner(userId).shop();
      List<Long> ids = new ArrayList<>(new LinkedHashSet<>(order));
      if (ids.size() != shop.getPhotoIds().size() || !new HashSet<>(shop.getPhotoIds()).containsAll(ids)) {
         throw new BadRequestException("BAD_ORDER", "Передайте все фото места в новом порядке");
      }
      shop.replacePhotos(ids);
      return photos(shop);
   }

   /** «Сделать обложкой» — фото становится первым. */
   @Transactional
   public List<ShopPhotoDto> makeCover(Long userId, Long mediaId) {
      Shop shop = access.requireOwner(userId).shop();
      requirePresent(shop, mediaId);
      List<Long> ids = new ArrayList<>(shop.getPhotoIds());
      ids.remove(mediaId);
      ids.add(0, mediaId);
      shop.replacePhotos(ids);
      return photos(shop);
   }

   @Transactional
   public List<ShopPhotoDto> remove(Long userId, Long mediaId) {
      Shop shop = access.requireOwner(userId).shop();
      requirePresent(shop, mediaId);
      List<Long> ids = new ArrayList<>(shop.getPhotoIds());
      ids.remove(mediaId);
      shop.replacePhotos(ids);
      return photos(shop);
   }

   private static void requirePresent(Shop shop, Long mediaId) {
      if (!shop.getPhotoIds().contains(mediaId)) {
         throw new NotFoundException("PHOTO_NOT_FOUND", "Фото не найдено");
      }
   }

   private List<Long> staff(Shop shop) {
      return members.findByShopIdOrderByCreatedAtAsc(shop.getId()).stream().map(ShopMember::getUserId).toList();
   }

   private List<ShopPhotoDto> photos(Shop shop) {
      Map<Long, PhotoDto> photos = media.photos(shop.getPhotoIds());
      List<ShopPhotoDto> result = new ArrayList<>();
      for (int i = 0; i < shop.getPhotoIds().size(); i++) {
         Long id = shop.getPhotoIds().get(i);
         result.add(new ShopPhotoDto(id, photos.get(id), i == 0, i));
      }
      return result;
   }
}
