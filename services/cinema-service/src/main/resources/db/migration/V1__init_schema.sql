CREATE TABLE cinemas (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(150) NOT NULL,
    location VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uq_cinemas_code UNIQUE (code),
    CONSTRAINT chk_cinemas_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE screens (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cinema_id INT NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_screens_cinema FOREIGN KEY (cinema_id) REFERENCES cinemas (id),
    CONSTRAINT uq_screens_cinema_code UNIQUE (cinema_id, code),
    CONSTRAINT chk_screens_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE'))
);

CREATE TABLE seat_types (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_seat_types_code UNIQUE (code)
);

CREATE TABLE seats (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    screen_id INT NOT NULL,
    row_code VARCHAR(8) NOT NULL,
    seat_number INT NOT NULL,
    seat_type_id INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT fk_seats_screen FOREIGN KEY (screen_id) REFERENCES screens (id),
    CONSTRAINT fk_seats_type FOREIGN KEY (seat_type_id) REFERENCES seat_types (id),
    CONSTRAINT uq_seats_position UNIQUE (screen_id, row_code, seat_number),
    CONSTRAINT chk_seats_number CHECK (seat_number > 0),
    CONSTRAINT chk_seats_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE')),
    INDEX idx_seats_screen (screen_id)
);
