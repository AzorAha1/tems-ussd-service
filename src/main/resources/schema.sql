CREATE TABLE IF NOT EXISTS organization (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    contact_address TEXT,
    contact_telephone VARCHAR(255),
    description TEXT
);
CREATE TABLE IF NOT EXISTS subscriptions (
    id SERIAL PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    createdat TIMESTAMP NOT NULL,
    expiresat TIMESTAMP NOT NULL
);