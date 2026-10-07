-- =========================================================
-- The Omega Hub - Movie Service
-- Flyway Migration: V1__init_schema.sql
-- Database: omega_movie_db
-- =========================================================


-- =========================================================
-- 1. MOVIE
-- =========================================================

CREATE TABLE movie (
    movie_id INT UNSIGNED NOT NULL AUTO_INCREMENT,

    title VARCHAR(255) NOT NULL,
    description TEXT,

    duration_minutes INT UNSIGNED NOT NULL,
    release_date DATE NOT NULL,

    language VARCHAR(50),
    age_rating VARCHAR(20),

    poster_url VARCHAR(500),
    trailer_url VARCHAR(500),

    status ENUM(
        'WILL_PRESENT',
        'PRESENTING',
        'EXPIRED'
    ) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_movie
        PRIMARY KEY (movie_id),

    CONSTRAINT chk_movie_duration
        CHECK (duration_minutes > 0)
)
ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_unicode_ci;


-- =========================================================
-- 2. GENRE
-- =========================================================

CREATE TABLE genre (
    genre_id INT UNSIGNED NOT NULL AUTO_INCREMENT,

    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),

    CONSTRAINT pk_genre
        PRIMARY KEY (genre_id),

    CONSTRAINT uk_genre_name
        UNIQUE (name)
)
ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_unicode_ci;


-- =========================================================
-- 3. PERSON
-- Actors / Directors
-- =========================================================

CREATE TABLE person (
    person_id INT UNSIGNED NOT NULL AUTO_INCREMENT,

    full_name VARCHAR(255) NOT NULL,

    CONSTRAINT pk_person
        PRIMARY KEY (person_id)
)
ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_unicode_ci;


-- =========================================================
-- 4. MOVIE_GENRE
-- Many-to-Many: Movie <-> Genre
-- =========================================================

CREATE TABLE movie_genre (
    movie_id INT UNSIGNED NOT NULL,
    genre_id INT UNSIGNED NOT NULL,

    CONSTRAINT pk_movie_genre
        PRIMARY KEY (movie_id, genre_id),

    CONSTRAINT fk_movie_genre_movie
        FOREIGN KEY (movie_id)
        REFERENCES movie (movie_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT fk_movie_genre_genre
        FOREIGN KEY (genre_id)
        REFERENCES genre (genre_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
)
ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_unicode_ci;


-- =========================================================
-- 5. MOVIE_CREDIT
-- Movie <-> Person
-- DIRECTOR / ACTOR
-- =========================================================

CREATE TABLE movie_credit (
    movie_credit_id INT UNSIGNED NOT NULL AUTO_INCREMENT,

    movie_id INT UNSIGNED NOT NULL,
    person_id INT UNSIGNED NOT NULL,

    credit_type ENUM(
        'DIRECTOR',
        'ACTOR'
    ) NOT NULL,

    character_name VARCHAR(255),

    display_order INT UNSIGNED,

    CONSTRAINT pk_movie_credit
        PRIMARY KEY (movie_credit_id),

    CONSTRAINT fk_movie_credit_movie
        FOREIGN KEY (movie_id)
        REFERENCES movie (movie_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT fk_movie_credit_person
        FOREIGN KEY (person_id)
        REFERENCES person (person_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
)
ENGINE = InnoDB
DEFAULT CHARSET = utf8mb4
COLLATE = utf8mb4_unicode_ci;


-- =========================================================
-- INDEXES
-- =========================================================

CREATE INDEX idx_movie_title
    ON movie (title);

CREATE INDEX idx_movie_status
    ON movie (status);

CREATE INDEX idx_movie_release_date
    ON movie (release_date);

CREATE INDEX idx_movie_genre_genre_id
    ON movie_genre (genre_id);

CREATE INDEX idx_movie_credit_movie_id
    ON movie_credit (movie_id);

CREATE INDEX idx_movie_credit_person_id
    ON movie_credit (person_id);

CREATE INDEX idx_movie_credit_type
    ON movie_credit (credit_type);