-- -- Seed users (passwords are BCrypt hashed: admin123, user123, other123)
-- INSERT INTO users (id_user, first_name, last_name, password) VALUES
-- (1, 'Admin','Admin','$2b$10$CXYqObUXLwFmWzxVv0IqgOw7sc.DSkRK1KyM2BaN0tdx0B7NeW4/e'),
-- (2, 'John', 'Doe','$2b$10$HNxZ6VhhUcW0Sp4zUqetQu/guQroMaAe0Uztwgl5TOUI7Y2aPLsnK'),
-- (3, 'Jane', 'Roe','$2b$10$caIMzxHiESoTvN4fLZlYGesMVyB.kFhpV6cvo26pClCQ188A8glV6');
--
-- -- Map user roles
-- INSERT INTO user_roles (user_id_user, roles) VALUES
--                                                  (1, 'Administrator'),
--                                                  (1, 'User'),
--                                                  (2, 'User'),
--                                                  (3, 'User');

INSERT INTO users (id_user, first_name, last_name, password) VALUES
 ( 1, 'Admin', 'Admin', '$2a$10$BwRsNgRZi2d31PoL/CLTnub.H02mxYAN8oQD7ktuqfQ7GAQpm0nQu' ),
 ( 2, 'John', 'Doe', '$2a$10$ohqLEbyxwaamlzc0C0L3z.3iUnuhtJMfOu1dlMYhkjheGNNNSXQi6' ),
 ( 3, 'Jane', 'Roe', '$2a$10$ohqLEbyxwaamlzc0C0L3z.3iUnuhtJMfOu1dlMYhkjheGNNNSXQi6' );

INSERT INTO user_roles (user_id_user, roles) VALUES
                                                 ( 1, 'Administrator' ),
                                                 ( 1, 'User' ),
                                                 ( 2, 'User' ),
                                                 ( 3, 'User' );