package kg.kudaibergen.master;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.master.dto.MasterInputs;
import kg.kudaibergen.master.dto.MasterPublicDto;
import kg.kudaibergen.master.dto.MasterReviewDto;
import kg.kudaibergen.master.dto.MyMasterDto;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.master.entity.MasterReview;
import kg.kudaibergen.media.MediaPurpose;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.request.dto.ReviewTagDto;
import kg.kudaibergen.shop.ShopNames;
import kg.kudaibergen.shop.ShopProperties;
import kg.kudaibergen.shop.entity.WeekDays;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.UserService;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.UserRole;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Профиль мастера / СТО (38): регистрация, правка, «Принимаю», аватар и фото; профиль для клиента и отзывы.
 * Проверка мастера — как у магазинов: пока app.shops.verification-required выключено, профиль действует сразу.
 */
@Service
public class MasterService {

   static final int REVIEWS_PAGE = 50;

   private final MasterRepository masters;
   private final MasterReviewRepository reviews;
   private final MasterMapper mapper;
   private final ServiceCatalog catalog;
   private final VehicleDirectory directory;
   private final MediaService media;
   private final UserService userService;
   private final UserRepository users;
   private final boolean verificationRequired;

   public MasterService(MasterRepository masters, MasterReviewRepository reviews, MasterMapper mapper,
                        ServiceCatalog catalog, VehicleDirectory directory, MediaService media, UserService userService,
                        UserRepository users, ShopProperties shopProperties) {
      this.masters = masters;
      this.reviews = reviews;
      this.mapper = mapper;
      this.catalog = catalog;
      this.directory = directory;
      this.media = media;
      this.userService = userService;
      this.users = users;
      this.verificationRequired = shopProperties.verificationRequired();
   }

   /** Свой профиль; нет — 404 NO_MASTER, клиент ведёт на регистрацию (38). */
   @Transactional(readOnly = true)
   public Master requireMine(Long userId) {
      return masters.findByOwnerId(userId)
            .orElseThrow(() -> new NotFoundException("NO_MASTER", "Вы ещё не зарегистрированы как мастер"));
   }

   @Transactional(readOnly = true)
   public MyMasterDto mine(Long userId, Lang lang) {
      return mapper.mine(requireMine(userId), lang);
   }

   /** «Стать мастером» (38): пользователь переходит в режим мастера. */
   @Transactional
   public MyMasterDto create(Long userId, MasterInputs.CreateMaster input, Lang lang) {
      if (masters.findByOwnerId(userId).isPresent()) {
         throw new ConflictException("ALREADY_MASTER", "Профиль мастера уже есть");
      }
      Master master = new Master(userId, ShopNames.validate(input.name()), input.address().trim(), input.lat(),
            input.lng());
      master.replaceServices(catalog.requireAll(input.services()));
      master.replaceBrands(Boolean.TRUE.equals(input.allBrands()), requireBrands(input.allBrands(), input.brandIds()));
      master.replaceOrigins(input.origins() == null ? Set.of() : input.origins());
      master.setRadiusKm(input.radiusKm() == null ? 5 : input.radiusKm());
      master.setMobile(Boolean.TRUE.equals(input.mobile()));
      applySchedule(master, input.openFrom(), input.openTo(), input.workDays());
      master.setPhone(input.phone() == null || input.phone().isBlank()
            ? users.findById(userId).map(User::getPhone).orElse(null) : input.phone());
      if (verificationRequired) {
         master.pending();
      }
      masters.save(master);
      userService.changeRole(userId, UserRole.MASTER);
      return mapper.mine(master, lang);
   }

   @Transactional
   public MyMasterDto update(Long userId, MasterInputs.UpdateMaster input, Lang lang) {
      Master master = requireMine(userId);
      if (input.name() != null) {
         master.setName(ShopNames.validate(input.name()));
      }
      if (input.services() != null) {
         master.replaceServices(catalog.requireAll(input.services()));
      }
      if (input.allBrands() != null || input.brandIds() != null) {
         boolean all = input.allBrands() != null ? input.allBrands() : master.isAllBrands();
         Set<Long> ids = input.brandIds() != null ? input.brandIds() : master.getBrandIds();
         master.replaceBrands(all, requireBrands(all, ids));
      }
      if (input.origins() != null) {
         master.replaceOrigins(input.origins());
      }
      if (input.address() != null || input.lat() != null || input.lng() != null) {
         if (input.address() == null || input.address().isBlank() || input.lat() == null || input.lng() == null) {
            throw new BadRequestException("LOCATION_REQUIRED", "Передайте адрес и точку на карте вместе");
         }
         master.setLocation(input.address().trim(), input.lat(), input.lng());
      }
      if (input.radiusKm() != null) {
         master.setRadiusKm(input.radiusKm());
      }
      if (input.mobile() != null) {
         master.setMobile(input.mobile());
      }
      if (input.openFrom() != null || input.openTo() != null || input.workDays() != null) {
         applySchedule(master, input.openFrom() != null ? input.openFrom() : master.getOpenFrom(),
               input.openTo() != null ? input.openTo() : master.getOpenTo(),
               input.workDays() != null ? input.workDays() : WeekDays.fromMask(master.getWorkDays()));
      }
      if (input.phone() != null) {
         master.setPhone(input.phone().isBlank() ? null : input.phone());
      }
      return mapper.mine(master, lang);
   }

   @Transactional
   public MyMasterDto setAccepting(Long userId, boolean accepting, Lang lang) {
      Master master = requireMine(userId);
      master.setAccepting(accepting);
      return mapper.mine(master, lang);
   }

   @Transactional
   public MyMasterDto setAvatar(Long userId, Long mediaId, Lang lang) {
      Master master = requireMine(userId);
      if (mediaId != null) {
         media.requireUsable(List.of(mediaId), List.of(userId), Set.of(MediaPurpose.MASTER, MediaPurpose.AVATAR));
      }
      master.setAvatarMediaId(mediaId);
      return mapper.mine(master, lang);
   }

   /** Фото работ и места целиком, по порядку: до 8, первое — обложка. */
   @Transactional
   public MyMasterDto setPhotos(Long userId, List<Long> mediaIds, Lang lang) {
      Master master = requireMine(userId);
      List<Long> ids = mediaIds.stream().distinct().toList();
      if (!ids.isEmpty()) {
         media.requireUsable(ids, List.of(userId), Set.of(MediaPurpose.MASTER));
      }
      master.replacePhotos(ids);
      return mapper.mine(master, lang);
   }

   // ─────────────────────── для клиента ───────────────────────

   @Transactional(readOnly = true)
   public MasterPublicDto publicProfile(Long masterId, Lang lang) {
      return mapper.publicProfile(visible(masterId), lang);
   }

   @Transactional(readOnly = true)
   public MasterPublicDto publicProfileByPublicId(String publicId, Lang lang) {
      Long id = masters.findIdByPublicId(publicId).orElseThrow(MasterService::notFound);
      return publicProfile(id, lang);
   }

   @Transactional(readOnly = true)
   public List<MasterReviewDto> reviews(Long masterId, Lang lang) {
      visible(masterId);
      List<MasterReview> page = reviews.findByMasterIdOrderByCreatedAtDescIdDesc(masterId, PageRequest.of(0, REVIEWS_PAGE));
      var names = users.findAllById(page.stream().map(MasterReview::getBuyerId).filter(java.util.Objects::nonNull)
                  .distinct().toList()).stream()
            .filter(user -> user.getName() != null)
            .collect(Collectors.toMap(User::getId, User::getName));
      return page.stream().map(review -> new MasterReviewDto(review.getId(), review.getStars(),
                  review.getTags().stream().map(tag -> new ReviewTagDto(tag, tag.label(lang))).toList(),
                  review.getBuyerId() == null ? null : names.get(review.getBuyerId()), review.getCreatedAt()))
            .toList();
   }

   private Master visible(Long masterId) {
      return masters.findById(masterId).filter(Master::isActive).orElseThrow(MasterService::notFound);
   }

   // ─────────────────────── внутреннее ───────────────────────

   private Set<Long> requireBrands(Boolean all, Set<Long> ids) {
      if (Boolean.TRUE.equals(all)) {
         return Set.of();
      }
      if (ids == null || ids.isEmpty()) {
         throw new BadRequestException("BRANDS_REQUIRED", "Выберите марки или «Все марки»");
      }
      ids.forEach(directory::brand);
      return ids;
   }

   private static void applySchedule(Master master, LocalTime from, LocalTime to, Set<java.time.DayOfWeek> days) {
      LocalTime openFrom = from == null ? LocalTime.of(9, 0) : from;
      LocalTime openTo = to == null ? LocalTime.of(19, 0) : to;
      short mask = days == null || days.isEmpty() ? WeekDays.ALL : WeekDays.toMask(days);
      if (!openFrom.isBefore(openTo)) {
         throw new BadRequestException("BAD_HOURS", "Время открытия должно быть раньше закрытия");
      }
      master.setSchedule(openFrom, openTo, mask);
   }

   static NotFoundException notFound() {
      return new NotFoundException("MASTER_NOT_FOUND", "Мастер не найден");
   }
}
