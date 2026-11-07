package fu.mss301.user.infrastructure.config;

import fu.mss301.user.application.port.in.*;
import fu.mss301.user.application.port.out.*;
import fu.mss301.user.application.usecase.*;
import fu.mss301.user.domain.repository.*;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;

import fu.mss301.user.domain.repository.PasswordOtpStore;
import fu.mss301.user.domain.repository.RefreshTokenStore;
import fu.mss301.user.domain.repository.RegistrationStore;
import fu.mss301.user.domain.repository.RoleStore;
import fu.mss301.user.domain.repository.UserStore;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.TransactionDefinition;

@Configuration
@EnableTransactionManagement
public class UseCaseConfig {
    @Bean
    UserAuthenticationUseCase userAuthenticationUseCase(UserStore users, PasswordHasher hasher) {
        return new UserAuthenticationUseCase(users, hasher);
    }

    @Bean
    UserProfileUseCase userProfileUseCase(UserProfileReadPort profiles) {
        return new UserProfileUseCase(profiles);
    }

    @Bean
    LoginUseCase loginUseCase(UserAuthenticationService authentication, JwtService jwt,
                              RefreshTokenService refreshTokens, UserStore users, Clock clock) {
        return new LoginUseCase(authentication, jwt, refreshTokens, users, clock);
    }

    @Bean
    RefreshTokenUseCase refreshTokenUseCase(RefreshTokenStore tokens, UserStore users, Clock clock,
                                            @Value("${jwt.refresh-token-ttl:P30D}") String ttl) {
        return new RefreshTokenUseCase(tokens, users, clock, Duration.parse(ttl));
    }

    @Bean
    RefreshUseCase refreshUseCase(RefreshTokenService refreshTokens, JwtService jwt) {
        return new RefreshUseCase(refreshTokens, jwt);
    }

    @Bean
    RegistrationUseCase registrationUseCase(UserStore users, RegistrationStore registrations,
            PasswordHasher hasher, OtpCodeGenerator codes, EventPublisher events, Clock clock,
            @Value("${registration.otp.ttl:PT5M}") String ttl,
            @Value("${registration.otp.resend-cooldown:PT60S}") String cooldown,
            @Value("${registration.otp.max-resends:5}") int maxResends) {
        return new RegistrationUseCase(users, registrations, hasher, codes, events, clock,
                Duration.parse(ttl), Duration.parse(cooldown), maxResends);
    }

    @Bean
    RegistrationVerificationUseCase registrationVerificationUseCase(RegistrationStore registrations,
                                                                    UserStore users, RoleStore roles, PasswordHasher hasher, Clock clock) {
        return new RegistrationVerificationUseCase(registrations, users, roles, hasher, clock);
    }

    @Bean
    PasswordResetUseCase passwordResetUseCase(UserStore users, PasswordOtpStore otps,
            RefreshTokenStore refreshTokens, PasswordHasher hasher, OtpCodeGenerator codes,
            EventPublisher events, Clock clock,
            @Value("${password-reset.otp.ttl:PT5M}") String ttl,
            @Value("${password-reset.otp.resend-cooldown:PT60S}") String cooldown,
            @Value("${password-reset.otp.max-attempts:5}") int maxAttempts) {
        return new PasswordResetUseCase(users, otps, refreshTokens, hasher, codes, events,
                clock, Duration.parse(ttl), Duration.parse(cooldown), maxAttempts);
    }

    @Bean
    PasswordChangeUseCase passwordChangeUseCase(UserStore users, PasswordOtpStore otps,
            RefreshTokenStore refreshTokens, PasswordHasher hasher, OtpCodeGenerator codes,
            EventPublisher events, Clock clock,
            @Value("${password-change.otp.ttl:PT5M}") String ttl,
            @Value("${password-change.otp.resend-cooldown:PT60S}") String cooldown,
            @Value("${password-change.otp.max-attempts:5}") int maxAttempts) {
        return new PasswordChangeUseCase(users, otps, refreshTokens, hasher, codes, events,
                clock, Duration.parse(ttl), Duration.parse(cooldown), maxAttempts);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor useCaseTransactions(PlatformTransactionManager transactions) {
        NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
        attributes.addTransactionalMethod("*", new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_REQUIRED));
        DefaultTransactionAttribute readOnly = new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_REQUIRED);
        readOnly.setReadOnly(true);
        attributes.addTransactionalMethod("authenticate", readOnly);
        attributes.addTransactionalMethod("me", readOnly);
        RuleBasedTransactionAttribute preserveAttempts = new RuleBasedTransactionAttribute();
        preserveAttempts.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        preserveAttempts.getRollbackRules().add(new NoRollbackRuleAttribute(UseCaseException.class));
        attributes.addTransactionalMethod("verify", preserveAttempts);
        attributes.addTransactionalMethod("confirm", preserveAttempts);
        attributes.addTransactionalMethod("reset", preserveAttempts);

        StaticMethodMatcherPointcut pointcut = new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(Method method, Class<?> targetClass) {
                return targetClass.getPackageName().equals("fu.mss301.user.application.usecase")
                        && targetClass.getSimpleName().endsWith("UseCase")
                        && method.getDeclaringClass() != Object.class;
            }
        };
        return new DefaultPointcutAdvisor(pointcut, new TransactionInterceptor(transactions, attributes));
    }
}
