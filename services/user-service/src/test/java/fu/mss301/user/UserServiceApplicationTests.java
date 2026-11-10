package fu.mss301.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=${USER_TEST_DB_URL}",
    "spring.datasource.username=${USER_TEST_DB_USERNAME:user_service}",
    "spring.datasource.password=${USER_TEST_DB_PASSWORD:user_test}",
    "jwt.secret=ZGV2ZWxvcG1lbnQtb25seS1jaGFuZ2UtdGhpcy1zZWNyZXQ=",
    "gateway.shared-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "registration.otp.mail-from=tests@example.invalid",
    "password.otp.mail-from=tests@example.invalid",
    "eureka.client.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "USER_TEST_DB_URL", matches = ".+")
class UserServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
