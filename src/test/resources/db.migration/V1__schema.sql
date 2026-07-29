-- create sequence hours_seq start with 1 increment by 50;
CREATE SEQUENCE hours_seq START WITH 1 INCREMENT BY 50;

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

-- create table hours
CREATE TABLE hours (
                       id INTEGER NOT NULL,
                       date DATE,
                       id_user INTEGER,
                       start TIMESTAMP(6),
                       finish TIMESTAMP(6),
                       PRIMARY KEY (id)
);

-- alter tables to add constraints
ALTER TABLE IF EXISTS user_roles
    ADD CONSTRAINT fk_user_roles_user
    FOREIGN KEY (user_id_user) REFERENCES users(id_user);

ALTER TABLE IF EXISTS hours
    ADD CONSTRAINT fk_hours_user
    FOREIGN KEY (id_user) REFERENCES users(id_user);