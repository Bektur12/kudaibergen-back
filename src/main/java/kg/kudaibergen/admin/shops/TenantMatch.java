package kg.kudaibergen.admin.shops;

/**
 * Сверка продавца со списком арендаторов [A2] — вычисляется при выдаче для контейнера, который проверяется
 * (новое место при переезде, иначе текущее). Порядок проверки — как в {@link #of}.
 */
public enum TenantMatch {
   /** «Контейнер занят»: открыт спор за этот контейнер с участием магазина. */
   CONTAINER_TAKEN,
   /** «Подтверждён по SMS»: арендатор ввёл код из SMS. */
   SMS_CONFIRMED,
   /** «В списке аренды»: телефон арендатора совпадает с телефоном владельца или сотрудника бокса. */
   IN_TENANT_LIST,
   /** «Нет в списке»: арендатор известен, но телефон другой. */
   NOT_IN_LIST,
   /** «Ждёт сверки»: телефона арендатора в базе нет. */
   AWAITING_CHECK;

   public static TenantMatch of(boolean openDispute, boolean smsConfirmed, boolean tenantPhoneKnown,
                                boolean tenantIsMember) {
      if (openDispute) {
         return CONTAINER_TAKEN;
      }
      if (smsConfirmed) {
         return SMS_CONFIRMED;
      }
      if (!tenantPhoneKnown) {
         return AWAITING_CHECK;
      }
      return tenantIsMember ? IN_TENANT_LIST : NOT_IN_LIST;
   }
}
