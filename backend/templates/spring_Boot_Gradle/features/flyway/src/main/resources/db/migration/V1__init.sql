-- First schema migration. Flyway applies files in this folder in version order:
-- add V2__..., V3__... as the schema evolves and never edit a migration once applied.
CREATE TABLE IF NOT EXISTS app_info
(
    id   INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);
