CREATE TABLE IF NOT EXISTS organization (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    initials VARCHAR(50),
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

DROP TABLE IF EXISTS formal_fhis_enrollment CASCADE;
DROP TABLE IF EXISTS informal_fhis_enrollment CASCADE;
CREATE TABLE IF NOT EXISTS fhis_enrollment (
    id BIGSERIAL PRIMARY KEY,
    
    -- System Fields (REQUIRED)
    phone_number VARCHAR(20) NOT NULL UNIQUE,
    enrollment_type VARCHAR(50),  -- 'Formal' or 'Informal'
    current_step VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    
    -- Personal Data (COMMON)
    fhis_no VARCHAR(255),
    surname VARCHAR(255),
    first_name VARCHAR(255),
    middle_name VARCHAR(255),
    date_of_birth VARCHAR(255),
    sex VARCHAR(10),              -- Only for Formal
    blood_group VARCHAR(10),
    
    -- Title field (ONLY for Informal)
    title VARCHAR(50),
    
    -- Professional Data (ONLY for Formal)
    designation VARCHAR(255),
    occupation VARCHAR(255),
    present_station VARCHAR(255),
    rank VARCHAR(255),
    pf_number VARCHAR(255),
    sda_name VARCHAR(255),
    
    -- Social Data (COMMON but different order)
    marital_status VARCHAR(50),
    telephone_number VARCHAR(20),
    residential_address TEXT,
    email VARCHAR(255),
    
    -- Corporate Data (ONLY for Informal)
    nin_number VARCHAR(20),
    organization_name VARCHAR(255),
    
    -- Dependants Data (COMMON)
    number_of_children INTEGER,
    
    -- Healthcare Provider Data (COMMON)
    hospital_name VARCHAR(255),
    hospital_location VARCHAR(255),
    hospital_code_no VARCHAR(255)
);

-- Step 3: Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_fhis_enrollment_phone ON fhis_enrollment(phone_number);
CREATE INDEX IF NOT EXISTS idx_fhis_enrollment_type ON fhis_enrollment(enrollment_type);
CREATE INDEX IF NOT EXISTS idx_fhis_enrollment_step ON fhis_enrollment(current_step);

-- Step 4: Verify the table structure
SELECT column_name, data_type, is_nullable, character_maximum_length
FROM information_schema.columns 
WHERE table_name = 'fhis_enrollment' 
ORDER BY ordinal_position;