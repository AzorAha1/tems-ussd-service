CREATE TABLE IF NOT EXISTS organization (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    contact_address TEXT,
    contact_telephone VARCHAR(255),
    description TEXT
);