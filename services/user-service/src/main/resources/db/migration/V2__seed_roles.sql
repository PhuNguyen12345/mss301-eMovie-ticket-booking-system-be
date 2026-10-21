INSERT INTO roles (name, description)
SELECT 'ADMIN', 'Administrator account'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

INSERT INTO roles (name, description)
SELECT 'CUSTOMER', 'Customer account'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'CUSTOMER');
