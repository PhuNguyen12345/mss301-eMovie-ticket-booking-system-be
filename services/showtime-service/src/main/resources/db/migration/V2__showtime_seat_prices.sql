CREATE TABLE showtime_seat_prices (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    showtime_id BIGINT NOT NULL,
    seat_type_id INT NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_seat_price_showtime FOREIGN KEY (showtime_id) REFERENCES showtime (showtime_id),
    CONSTRAINT uq_showtime_seat_type UNIQUE (showtime_id, seat_type_id),
    CONSTRAINT chk_seat_price CHECK (price >= 0)
);
