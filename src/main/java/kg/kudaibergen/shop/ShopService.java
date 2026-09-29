package kg.kudaibergen.shop;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import kg.kudaibergen.auth.sms.SmsProvider;
import kg.kudaibergen.auth.sms.SmsTexts;
import kg.kudaibergen.category.CategoryService;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.shop.dto.MemberDto;
import kg.kudaibergen.shop.dto.MyShopDto;
import kg.kudaibergen.shop.dto.ShopCardDto;
import kg.kudaibergen.shop.dto.ShopPublicDto;
import kg.kudaibergen.shop.dto.ShopRequests;
import kg.kudaibergen.shop.entity.MemberRole;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopMember;
import kg.kudaibergen.shop.entity.ShopStatus;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.UserService;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.UserRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Бокс продавца: регистрация (10а → 10), профиль (10, 21, 23), тумблер «Бокс закрыт»,
 * сотрудники, профиль для покупателя (30), список магазинов и избранное.
 */
@Service
public class ShopService {

   /** Сотрудников в боксе, кроме владельца (ТЗ 7.3). */
   static final int MAX_STAFF = 5;

   private final ShopRepository shops;
   private final ShopMemberRepository members;
   private final FavoriteShopRepository favorites;
   private final ShopAccess access;
   private final ShopMapper mapper;
   private final ShopVerificationService verification;
   private final MarketMapService market;
   private final VehicleDirectory directory;
   private final CategoryService categories;
   private final UserService userService;
   private final UserRepository users;
   private final SmsProvider sms;

   public ShopService(ShopRepository shops, ShopMemberRepository members, FavoriteShopRepository favorites,
                      ShopAccess access, ShopMapper mapper, ShopVerificationService verification,
                      MarketMapService market, VehicleDirectory directory, CategoryService categories,
                      UserService userService, UserRepository users, SmsProvider sms) {
      this.shops = shops;
      this.members = members;
      this.favorites = favorites;
      this.access = access;
      this.mapper = mapper;
      this.verification = verification;
      this.market = market;
      this.directory = directory;
      this.categories = categories;
      this.userService = userService;
      this.users = users;
      this.sms = sms;
   }

   // ─────────────────────── регистрация и профиль ───────────────────────

   /** «Сохранить» на экране 10: магазин «На проверке», пользователь переходит в режим продавца. */
   @Transactional
   public MyShopDto register(Long userId, ShopRequests.CreateShop request, Lang lang) {
      if (members.findByUserId(userId).isPresent()) {
         throw new ConflictException("ALREADY_IN_SHOP", "Вы уже состоите в боксе");
      }
      String name = ShopNames.validate(request.name());
      Set<Long> brandIds = requireBrands(request.brandIds());
      Set<Long> categoryIds = categories.requireExisting(request.categoryIds());
      Long containerId = requireFreeContainer(request.containerId());

      Shop shop = new Shop(userId, name, containerId);
      shop.setSchedule(request.openFrom(), request.openTo(), request.workDays());
      validateSchedule(shop.getOpenFrom(), shop.getOpenTo(), shop.getWorkDays());
      shop.getBrandIds().addAll(brandIds);
      shop.getCategoryIds().addAll(categoryIds);
      try {
         shops.saveAndFlush(shop);
      } catch (DataIntegrityViolationException race) {
         throw containerTaken();
      }
      members.save(new ShopMember(shop.getId(), userId, MemberRole.OWNER));
      userService.changeRole(userId, UserRole.SELLER);
      return mine(access.requireMember(userId), lang);
   }

   @Transactional(readOnly = true)
   public MyShopDto myShop(Long userId, Lang lang) {
      return mine(access.requireMember(userId), lang);
   }

   @Transactional
   public MyShopDto update(Long userId, ShopRequests.UpdateShop request, Lang lang) {
      Shop shop = access.requireOwner(userId).shop();
      if (request.name() != null) {
         shop.setName(ShopNames.validate(request.name()));
      }
      shop.setSchedule(request.openFrom(), request.openTo(), request.workDays());
      validateSchedule(shop.getOpenFrom(), shop.getOpenTo(), shop.getWorkDays());
      if (request.phone() != null) {
         shop.setPhone(request.phone().isBlank() ? null : request.phone());
      }
      if (request.phoneVisible() != null) {
         shop.setPhoneVisible(request.phoneVisible());
      }
      if (shop.isPhoneVisible() && shop.getPhone() == null) {
         throw new BadRequestException("PHONE_REQUIRED", "Укажите телефон, чтобы показывать его покупателям");
      }
      return mine(access.requireMember(userId), lang);
   }

   /** Марки (экран 23): запросы приходят только по ним. */
   @Transactional
   public MyShopDto setBrands(Long userId, Set<Long> brandIds, Lang lang) {
      Shop shop = access.requireOwner(userId).shop();
      Set<Long> checked = requireBrands(brandIds);
      shop.getBrandIds().clear();
      shop.getBrandIds().addAll(checked);
      return mine(access.requireMember(userId), lang);
   }

   @Transactional
   public MyShopDto setCategories(Long userId, Set<Long> categoryIds, Lang lang) {
      Shop shop = access.requireOwner(userId).shop();
      Set<Long> checked = categories.requireExisting(categoryIds);
      shop.getCategoryIds().clear();
      shop.getCategoryIds().addAll(checked);
      return mine(access.requireMember(userId), lang);
   }

   /** «Бокс закрыт»: запросы не приходят, у покупателей «Закрыто». Может и сотрудник. */
   @Transactional
   public MyShopDto setOpen(Long userId, boolean open, Lang lang) {
      access.requireMember(userId).shop().setOpen(open);
      return mine(access.requireMember(userId), lang);
   }

   // ─────────────────────── сотрудники ───────────────────────

   @Transactional(readOnly = true)
   public List<MemberDto> members(Long userId) {
      Shop shop = access.requireMember(userId).shop();
      return members.findByShopIdOrderByCreatedAtAsc(shop.getId()).stream()
            .map(member -> {
               User user = users.findById(member.getUserId()).orElseThrow();
               return new MemberDto(user.getId(), user.getName(), user.getPhone(), member.getRole());
            })
            .toList();
   }

   /** Приглашение по номеру: аккаунт создаётся, если его нет; сотрудник получает SMS. */
   @Transactional
   public List<MemberDto> addMember(Long userId, String phone) {
      Shop shop = access.requireOwner(userId).shop();
      if (members.countByShopIdAndRole(shop.getId(), MemberRole.STAFF) >= MAX_STAFF) {
         throw new ConflictException("STAFF_LIMIT", "В боксе может быть не больше " + MAX_STAFF + " сотрудников");
      }
      User invited = userService.findOrCreate(phone, Lang.RU);
      members.findByUserId(invited.getId()).ifPresent(existing -> {
         throw new ConflictException(existing.getShopId().equals(shop.getId()) ? "ALREADY_MEMBER" : "USER_IN_OTHER_SHOP",
               existing.getShopId().equals(shop.getId()) ? "Этот продавец уже в вашем боксе"
                     : "Этот номер уже работает в другом боксе");
      });
      members.save(new ShopMember(shop.getId(), invited.getId(), MemberRole.STAFF));
      if (!invited.isOnboarded()) {
         // новый человек при первом входе сразу попадёт во вкладки продавца
         invited.switchRole(UserRole.SELLER);
      }
      sms.send(phone, SmsTexts.shopInvite(shop.getName(), invited.getLang()));
      return members(userId);
   }

   @Transactional
   public List<MemberDto> removeMember(Long userId, Long memberUserId) {
      Shop shop = access.requireOwner(userId).shop();
      ShopMember member = members.findByUserId(memberUserId)
            .filter(found -> found.getShopId().equals(shop.getId()))
            .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Такого продавца в боксе нет"));
      if (member.isOwner()) {
         throw new BadRequestException("CANNOT_REMOVE_OWNER", "Владельца бокса удалить нельзя");
      }
      members.delete(member);
      return members(userId);
   }

   // ─────────────────────── для покупателя ───────────────────────

   /** Профиль магазина (30). Не проверенный и заблокированный магазин покупателю не виден. */
   @Transactional(readOnly = true)
   public ShopPublicDto publicProfile(Long shopId, Long viewerId, Lang lang) {
      Shop shop = shops.findById(shopId).filter(Shop::isActive)
            .orElseThrow(() -> new NotFoundException("SHOP_NOT_FOUND", "Магазин не найден"));
      boolean favorite = viewerId != null && favorites.existsByUserIdAndShopId(viewerId, shopId);
      return mapper.publicProfile(shop, favorite, lang);
   }

   /** Ссылка «Поделиться» профилем (30): по публичному id. */
   @Transactional(readOnly = true)
   public ShopPublicDto publicProfileByPublicId(String publicId, Long viewerId, Lang lang) {
      Long shopId = shops.findIdByPublicId(publicId)
            .orElseThrow(() -> new NotFoundException("SHOP_NOT_FOUND", "Магазин не найден"));
      return publicProfile(shopId, viewerId, lang);
   }

   /** Список действующих магазинов: «Списком» на карте (15), выбор «Боксу» в запросе (06). */
   @Transactional(readOnly = true)
   public CursorPage<ShopCardDto> search(Long brandId, Long categoryId, Long rowId, String query, String cursor,
                                         Integer limit) {
      int size = CursorPage.limit(limit);
      String like = query == null || query.isBlank() ? null
            : "%" + query.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
      List<Shop> rows = shops.search(ShopStatus.ACTIVE, CursorPage.afterId(cursor), brandId, categoryId, rowId, like,
            PageRequest.of(0, size + 1));
      return CursorPage.of(rows, size, shop -> String.valueOf(shop.getId()), mapper::card);
   }

   @Transactional
   public void addFavorite(Long userId, Long shopId) {
      shops.findById(shopId).filter(Shop::isActive)
            .orElseThrow(() -> new NotFoundException("SHOP_NOT_FOUND", "Магазин не найден"));
      favorites.add(userId, shopId);
   }

   @Transactional
   public void removeFavorite(Long userId, Long shopId) {
      favorites.remove(userId, shopId);
   }

   @Transactional(readOnly = true)
   public List<ShopCardDto> favorites(Long userId) {
      return favorites.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(favorite -> shops.findById(favorite.getShopId()).filter(Shop::isActive))
            .flatMap(java.util.Optional::stream)
            .map(mapper::card)
            .toList();
   }

   // ─────────────────────── внутреннее ───────────────────────

   MyShopDto mine(ShopAccess.Membership membership, Lang lang) {
      Shop shop = membership.shop();
      int staff = (int) members.countByShopIdAndRole(shop.getId(), MemberRole.STAFF);
      return mapper.mine(shop, membership.member().getRole(), verification.status(shop), staff, lang);
   }

   /** Контейнер существует, активен и свободен (нет магазина и никто туда не переезжает). */
   Long requireFreeContainer(Long containerId) {
      market.container(containerId);
      if (shops.isContainerTaken(containerId)) {
         throw containerTaken();
      }
      return containerId;
   }

   static ConflictException containerTaken() {
      return new ConflictException("CONTAINER_TAKEN",
            "Этот контейнер уже занят в приложении. Если он ваш — нажмите «Это мой контейнер»");
   }

   private Set<Long> requireBrands(Set<Long> brandIds) {
      for (Long id : brandIds) {
         try {
            directory.brand(id);
         } catch (NotFoundException unknown) {
            throw new BadRequestException("UNKNOWN_BRAND", "Нет такой марки: " + id);
         }
      }
      return Set.copyOf(brandIds);
   }

   private static void validateSchedule(LocalTime from, LocalTime to, short workDays) {
      if (!from.isBefore(to)) {
         throw new BadRequestException("BAD_HOURS", "Время открытия должно быть раньше закрытия");
      }
      if (workDays == 0) {
         throw new BadRequestException("BAD_HOURS", "Нужен хотя бы один рабочий день");
      }
   }
}
