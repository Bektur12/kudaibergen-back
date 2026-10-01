package kg.kudaibergen.admin.audit;

import kg.kudaibergen.common.error.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuditAspectTest {

   private AuditLog log;
   private PlatformTransactionManager transactions;
   private Actions actions;

   @BeforeEach
   void setUp() {
      log = mock(AuditLog.class);
      transactions = mock(PlatformTransactionManager.class);
      AspectJProxyFactory factory = new AspectJProxyFactory(new Actions());
      factory.setProxyTargetClass(true);
      factory.addAspect(new AuditAspect(log, transactions));
      actions = factory.getProxy();
   }

   @Test
   void idИКомментарийИзАргументовПослеИзОтветаДоОтМетода() {
      actions.block(15L, new Reason("Контрафакт"));

      verify(log).record(isNull(), eq("SHOP_BLOCK"), eq("SHOP"), eq(15L), eq("ACTIVE"), eq("BLOCKED"),
            eq("Контрафакт"));
      verify(transactions).commit(any());
   }

   @Test
   void методМожетСамЗадатьIdИПосле() {
      actions.create();

      verify(log).record(isNull(), eq("MASTER_CREATE"), eq("MASTER"), eq(99L), isNull(), eq("snapshot"), isNull());
   }

   @Test
   void упавшееДействиеВЖурналНеПишетсяИОткатывается() {
      assertThatThrownBy(() -> actions.fail(1L)).isInstanceOf(ConflictException.class);

      verify(log, never()).record(any(), anyString(), anyString(), any(), any(), any(), any());
      verify(transactions).rollback(any());
   }

   @Test
   void внеДействияСледНичегоНеДелает() {
      AuditTrail.before("x");
      AuditTrail.entityId(1L);
      assertThat(actions).isNotNull();
   }

   record Reason(String reason) {
   }

   static class Actions {

      @Audited(action = "SHOP_BLOCK", entity = "SHOP", id = "#id", comment = "#request.reason")
      public String block(Long id, Reason request) {
         AuditTrail.before("ACTIVE");
         return "BLOCKED";
      }

      @Audited(action = "MASTER_CREATE", entity = "MASTER")
      public String create() {
         AuditTrail.entityId(99L);
         AuditTrail.after("snapshot");
         return "full response";
      }

      @Audited(action = "CONTAINER_DELETE", entity = "CONTAINER", id = "#id")
      public void fail(Long id) {
         throw new ConflictException("CONTAINER_TAKEN", "Контейнер занят");
      }
   }
}
