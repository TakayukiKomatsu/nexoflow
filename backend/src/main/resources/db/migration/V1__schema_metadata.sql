create table schema_metadata (
    id integer primary key,
    schema_name varchar(100) not null unique,
    created_at timestamp not null
);

insert into schema_metadata (id, schema_name, created_at)
values (1, 'srm-credit-engine', current_timestamp);
