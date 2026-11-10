package fu.mss301.movie;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=${MOVIE_TEST_DB_URL}",
    "spring.datasource.username=${MOVIE_TEST_DB_USERNAME:movie}",
    "spring.datasource.password=${MOVIE_TEST_DB_PASSWORD:movie_test}",
    "cloudinary.cloud-name=test", "cloudinary.api-key=test", "cloudinary.api-secret=test",
    "eureka.client.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "MOVIE_TEST_DB_URL", matches = ".+")
class MovieServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
