create table users (id uuid primary key, email varchar(320) not null unique, password_hash varchar(100) not null, enabled boolean not null);
create table user_roles (user_id uuid not null references users(id), role varchar(30) not null, primary key (user_id, role));
