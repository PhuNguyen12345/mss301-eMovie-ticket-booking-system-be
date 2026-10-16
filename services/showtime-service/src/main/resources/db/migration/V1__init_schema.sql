CREATE TABLE screen_schedule_lock (
    screen_id INT NOT NULL PRIMARY KEY
);

CREATE TABLE showtime (
    showtime_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    movie_id INT NOT NULL,
    cinema_id INT NOT NULL,
    screen_id INT NOT NULL,
    cinema_location VARCHAR(255) NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    base_price DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_showtime_time CHECK (end_time > start_time),
    CONSTRAINT chk_showtime_price CHECK (base_price >= 0),
    CONSTRAINT chk_showtime_status CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    INDEX idx_showtime_screen_time (screen_id, status, start_time, end_time),
    INDEX idx_showtime_movie_time (movie_id, start_time),
    INDEX idx_showtime_cinema_time (cinema_id, start_time),
    INDEX idx_showtime_time (start_time)
);
