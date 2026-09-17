-- create table users
CREATE TABLE users (
                       id_user INTEGER NOT NULL,
                       first_name VARCHAR(255),
                       last_name VARCHAR(255),
                       password VARCHAR(255),
                       email VARCHAR(255),
                       tenant_id INTEGER NOT NULL,
                       PRIMARY KEY (id_user)
);

-- create table user_roles
CREATE TABLE user_roles (
                            user_id_user INTEGER NOT NULL,
                            roles VARCHAR(255),
                            UNIQUE (user_id_user, roles)
);

-- create table hours (backs the DataTime entity, @Table(name = "hours"))
CREATE TABLE hours (
                       id INTEGER NOT NULL,
                       id_user INTEGER,
                       date DATE,
                       start TIMESTAMP,
                       finish TIMESTAMP,
                       PRIMARY KEY (id)
);

-- alter tables to add constraints
ALTER TABLE IF EXISTS user_roles
    ADD CONSTRAINT fk_user_roles_user
    FOREIGN KEY (user_id_user) REFERENCES users(id_user);

ALTER TABLE IF EXISTS hours
    ADD CONSTRAINT fk_hours_user
    FOREIGN KEY (id_user) REFERENCES users(id_user);
