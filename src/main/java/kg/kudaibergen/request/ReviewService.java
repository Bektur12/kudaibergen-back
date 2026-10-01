package kg.kudaibergen.request;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.request.dto.ReviewDto;
import kg.kudaibergen.request.dto.ReviewTagDto;
import kg.kudaibergen.request.entity.Review;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Отзывы магазина: список для покупателя (30) и продавца (21), один ответ продавца на отзыв (ТЗ 8). */
@Service
public class ReviewService {

   private final ReviewRepository reviews;
   private final ShopRepository shops;
   private final ShopAccess access;
   private final UserRepository users;
   private final Clock clock;

   public ReviewService(ReviewRepository reviews, ShopRepository shops, ShopAccess access, UserRepository users,
                        Clock clock) {
      this.reviews = reviews;
      this.shops = shops;
      this.access = access;
      this.users = users;
      this.clock = clock;
   }

   @Transactional(readOnly = true)
   public CursorPage<ReviewDto> ofShop(Long shopId, String cursor, Integer limit, Lang lang) {
      shops.findById(shopId).filter(Shop::isActive)
            .orElseThrow(() -> new NotFoundException("SHOP_NOT_FOUND", "Магазин не найден"));
      return page(shopId, cursor, limit, lang);
   }

   /** Отзывы своего бокса — владелец и сотрудники, в том числе пока бокс на проверке. */
   @Transactional(readOnly = true)
   public CursorPage<ReviewDto> mine(Long userId, String cursor, Integer limit, Lang lang) {
      return page(access.requireMember(userId).shop().getId(), cursor, limit, lang);
   }

   /** Ответ на отзыв: один раз, только владелец (он же ведёт профиль магазина). */
   @Transactional
   public ReviewDto reply(Long userId, Long reviewId, String text, Lang lang) {
      Shop shop = access.requireOwner(userId).shop();
      Review review = reviews.findById(reviewId).filter(found -> found.getShopId().equals(shop.getId()))
            .orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "Отзыв не найден"));
      if (review.getReplyText() != null) {
         throw new ConflictException("ALREADY_REPLIED", "На этот отзыв уже есть ответ");
      }
      review.reply(text.trim(), clock.instant());
      return dtos(List.of(review), lang).get(0);
   }

   private CursorPage<ReviewDto> page(Long shopId, String cursor, Integer limit, Lang lang) {
      int size = CursorPage.limit(limit);
      long beforeId = cursor == null || cursor.isBlank() ? Long.MAX_VALUE : CursorPage.afterId(cursor);
      List<Review> rows = reviews.findPage(shopId, beforeId, PageRequest.of(0, size + 1));
      boolean more = rows.size() > size;
      List<Review> page = more ? rows.subList(0, size) : rows;
      return new CursorPage<>(dtos(page, lang),
            more ? CursorPage.encode(String.valueOf(page.get(page.size() - 1).getId())) : null);
   }

   private List<ReviewDto> dtos(List<Review> page, Lang lang) {
      Map<Long, String> names = new HashMap<>();
      users.findAllById(page.stream().map(Review::getBuyerId).filter(Objects::nonNull).distinct().toList())
            .forEach(user -> names.put(user.getId(), user.getName()));
      return page.stream().map(review -> new ReviewDto(review.getId(), review.getStars(),
                  review.getTags().stream().map(tag -> new ReviewTagDto(tag, tag.label(lang))).toList(),
                  review.getBuyerId() == null ? null : names.get(review.getBuyerId()), review.getReplyText(),
                  review.getRepliedAt(), review.getCreatedAt()))
            .toList();
   }
}
