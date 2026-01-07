UPDATE users
SET username = COALESCE(username, email)
WHERE username IS NULL;

UPDATE users
SET password = COALESCE(password, password_bcrypt)
WHERE password IS NULL;

UPDATE users
SET role = COALESCE(role, 'USER')
WHERE role IS NULL;

UPDATE users
SET created_time = COALESCE(created_time, created_at)
WHERE created_time IS NULL;

UPDATE users
SET updated_time = COALESCE(updated_time, created_time)
WHERE updated_time IS NULL;

ALTER TABLE users
  MODIFY username VARCHAR(64) NOT NULL,
  MODIFY password VARCHAR(100) NOT NULL,
  MODIFY role VARCHAR(32) NOT NULL,
  MODIFY created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  MODIFY updated_time TIMESTAMP NULL;

ALTER TABLE users
  DROP COLUMN password_bcrypt,
  DROP COLUMN created_at;
