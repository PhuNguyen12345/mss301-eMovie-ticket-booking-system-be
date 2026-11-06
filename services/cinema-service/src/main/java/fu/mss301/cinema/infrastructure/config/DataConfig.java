package fu.mss301.cinema.infrastructure.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class DataConfig {
    @Bean
    @ConditionalOnProperty(prefix = "cinema.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner seedCinemaData(JdbcTemplate jdbc, TransactionTemplate transactions) {
        return args -> transactions.executeWithoutResult(status -> seed(jdbc));
    }

    private void seed(JdbcTemplate jdbc) {
        int standard = seatType(jdbc, "STANDARD", "Ghế thường");
        int vip = seatType(jdbc, "VIP", "Ghế VIP");

        int haNoi = cinema(jdbc, "DEMO_HN", "Rạp Hà Nội", "Hà Nội");
        int hoChiMinh = cinema(jdbc, "DEMO_HCM", "Rạp Hồ Chí Minh", "Hồ Chí Minh");

        seedScreen(jdbc, haNoi, "S1", "Phòng chiếu 1", standard, vip);
        seedScreen(jdbc, haNoi, "S2", "Phòng chiếu 2", standard, vip);
        seedScreen(jdbc, hoChiMinh, "S1", "Phòng chiếu 1", standard, vip);
        seedScreen(jdbc, hoChiMinh, "S2", "Phòng chiếu 2", standard, vip);
    }

    private int cinema(JdbcTemplate jdbc, String code, String name, String location) {
        jdbc.update("INSERT INTO cinemas (code, name, location, status) VALUES (?, ?, ?, 'ACTIVE') "
            + "ON DUPLICATE KEY UPDATE id = id", code, name, location);
        return jdbc.queryForObject("SELECT id FROM cinemas WHERE code = ?", Integer.class, code);
    }

    private int screen(JdbcTemplate jdbc, int cinemaId, String code, String name) {
        jdbc.update("INSERT INTO screens (cinema_id, code, name, status) VALUES (?, ?, ?, 'ACTIVE') "
            + "ON DUPLICATE KEY UPDATE id = id", cinemaId, code, name);
        return jdbc.queryForObject("SELECT id FROM screens WHERE cinema_id = ? AND code = ?", Integer.class,
            cinemaId, code);
    }

    private int seatType(JdbcTemplate jdbc, String code, String name) {
        jdbc.update("INSERT INTO seat_types (code, name) VALUES (?, ?) ON DUPLICATE KEY UPDATE id = id", code, name);
        return jdbc.queryForObject("SELECT id FROM seat_types WHERE code = ?", Integer.class, code);
    }

    private void seedScreen(JdbcTemplate jdbc, int cinemaId, String code, String name, int standard, int vip) {
        int screenId = screen(jdbc, cinemaId, code, name);
        for (String row : new String[] {"A", "B", "C"}) {
            int typeId = row.equals("C") ? vip : standard;
            for (int number = 1; number <= 6; number++) {
                jdbc.update("INSERT INTO seats (screen_id, row_code, seat_number, seat_type_id, status) "
                    + "VALUES (?, ?, ?, ?, 'ACTIVE') ON DUPLICATE KEY UPDATE id = id",
                    screenId, row, number, typeId);
            }
        }
    }
}
