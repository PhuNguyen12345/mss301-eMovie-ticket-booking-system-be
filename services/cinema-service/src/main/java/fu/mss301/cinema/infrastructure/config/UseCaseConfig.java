package fu.mss301.cinema.infrastructure.config;

import fu.mss301.cinema.application.usecase.CinemaUseCase;
import fu.mss301.cinema.domain.repository.CinemaStore;
import java.lang.reflect.Method;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

@Configuration
@EnableTransactionManagement
public class UseCaseConfig {
    @Bean
    CinemaUseCase cinemaUseCase(CinemaStore store) {
        return new CinemaUseCase(store);
    }
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Advisor cinemaTransactions(PlatformTransactionManager manager) {
        var attributes = new NameMatchTransactionAttributeSource();
        var read = new DefaultTransactionAttribute();
        read.setReadOnly(true);
        attributes.addTransactionalMethod("*", read);
        for (String pattern : new String[] {"create*", "update*", "set*"})
            attributes.addTransactionalMethod(pattern, new DefaultTransactionAttribute());
        var pointcut = new StaticMethodMatcherPointcut() {
            public boolean matches(Method method, Class<?> type) {
                return type == CinemaUseCase.class && method.getDeclaringClass() != Object.class;
            }
        };
        return new DefaultPointcutAdvisor(pointcut, new TransactionInterceptor(manager, attributes));
    }
}
