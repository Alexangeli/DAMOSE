-- =====================================================
--  SQLITE SCHEMA - MINIMAL & SAFE
-- =====================================================

PRAGMA foreign_keys = ON;

-- -----------------------------------------------------
-- USERS
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
                                     id INTEGER PRIMARY KEY AUTOINCREMENT,
                                     username TEXT NOT NULL UNIQUE,
                                     email TEXT UNIQUE,
                                     password_hash TEXT NOT NULL
);

-- -----------------------------------------------------
-- SETTINGS (key / value per user)
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS settings (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                                        user_id INTEGER,
                                        key TEXT NOT NULL,
                                        value TEXT NOT NULL,

                                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (user_id, key)
    );

-- -----------------------------------------------------
-- USER FAVORITE BUS STOPS
-- Stops are identified ONLY by their code
-- Data lives in the CSV
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS user_favorite_stops (
                                                   user_id INTEGER NOT NULL,
                                                   stop_code TEXT NOT NULL,

                                                   PRIMARY KEY (user_id, stop_code),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

-- =====================================================
-- END OF SCHEMA
-- =====================================================
