INSERT INTO users (full_name, email, password_hash, status, created_at)
SELECT 'Administrator', 'admin@gmail.com',
       '$2a$10$EdbgoCLXqp56DIvh2h5QpORNujXx6TyO7tVCgzOrWlpBV1lUdrK6q',
       'ACTIVE', CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@gmail.com');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ADMIN'
WHERE u.email = 'admin@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
