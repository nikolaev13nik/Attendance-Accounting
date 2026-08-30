-- create table users
CREATE TABLE users (
                       id_user INTEGER NOT NULL,
                       first_name VARCHAR(255),
                       last_name VARCHAR(255),
                       password VARCHAR(255),
                       PRIMARY KEY (id_user)
);

-- create table user_roles
CREATE TABLE user_roles (
                            user_id_user INTEGER NOT NULL,
                            roles VARCHAR(255),
                            UNIQUE (user_id_user, roles)
);

-- alter tables to add constraints
ALTER TABLE IF EXISTS user_roles
    ADD CONSTRAINT fk_user_roles_user
    FOREIGN KEY (user_id_user) REFERENCES users(id_user);
