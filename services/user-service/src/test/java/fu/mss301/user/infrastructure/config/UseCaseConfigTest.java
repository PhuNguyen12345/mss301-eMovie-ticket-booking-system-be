package fu.mss301.user.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import fu.mss301.user.application.port.in.*;
import fu.mss301.user.application.port.out.*;
import fu.mss301.user.domain.repository.*;
import fu.mss301.user.application.usecase.RegistrationVerificationUseCase;
import fu.mss301.user.application.usecase.StatusCode;
import fu.mss301.user.application.usecase.UseCaseException;
import fu.mss301.user.application.usecase.UserProfileUseCase;
import fu.mss301.user.application.usecase.dto.VerifyRegistrationRequest;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;

class UseCaseConfigTest {
    @Test
    void wiresEveryUseCaseThroughItsPorts() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(UseCaseConfig.class);
            context.registerBean(UserStore.class, () -> mock(UserStore.class));
            context.registerBean(RegistrationStore.class, () -> mock(RegistrationStore.class));
            context.registerBean(RoleStore.class, () -> mock(RoleStore.class));
            context.registerBean(PasswordOtpStore.class, () -> mock(PasswordOtpStore.class));
            context.registerBean(RefreshTokenStore.class, () -> mock(RefreshTokenStore.class));
            context.registerBean(UserProfileReadPort.class, () -> mock(UserProfileReadPort.class));
            context.registerBean(PasswordHasher.class, () -> mock(PasswordHasher.class));
            context.registerBean(OtpCodeGenerator.class, () -> mock(OtpCodeGenerator.class));
            context.registerBean(EventPublisher.class, () -> mock(EventPublisher.class));
            context.registerBean(JwtService.class, () -> mock(JwtService.class));
            context.registerBean(Clock.class, Clock::systemUTC);
            context.registerBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
            context.refresh();

            assertNotNull(context.getBean(LoginService.class));
            assertNotNull(context.getBean(RefreshService.class));
            assertNotNull(context.getBean(RefreshTokenService.class));
            assertNotNull(context.getBean(RegistrationService.class));
            assertNotNull(context.getBean(RegistrationVerificationService.class));
            assertNotNull(context.getBean(PasswordResetService.class));
            assertNotNull(context.getBean(PasswordChangeService.class));
            assertNotNull(context.getBean(UserAuthenticationService.class));
            assertNotNull(context.getBean(UserProfileService.class));
            assertTrue(AopUtils.isAopProxy(context.getBean(UserProfileService.class)));

            var interceptor = (TransactionInterceptor) context.getBean("useCaseTransactions", Advisor.class).getAdvice();
            var attributes = interceptor.getTransactionAttributeSource();
            var read = attributes.getTransactionAttribute(UserProfileUseCase.class.getMethod("me", Integer.class),
                    UserProfileUseCase.class);
            assertTrue(read.isReadOnly());
            var verify = attributes.getTransactionAttribute(RegistrationVerificationUseCase.class.getMethod(
                    "verify", VerifyRegistrationRequest.class), RegistrationVerificationUseCase.class);
            assertFalse(verify.rollbackOn(new UseCaseException(StatusCode.BAD_REQUEST, "Invalid OTP")));
        }
    }
}
