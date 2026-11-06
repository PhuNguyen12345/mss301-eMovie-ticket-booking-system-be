package fu.mss301.cinema;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import fu.mss301.common.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@Import(GlobalExceptionHandler.class)
@OpenAPIDefinition(info = @Info(title = "Cinema Service API", version = "1.0.0",
    description = "Quản lý rạp, phòng chiếu, loại ghế và sơ đồ ghế vật lý. Các API ghi yêu cầu quyền ADMIN qua API Gateway."))
public class CinemaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CinemaServiceApplication.class, args);
    }

}
