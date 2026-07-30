create table runtime_fixture_records (
    fixture_id varchar(100) primary key,
    fixture_set varchar(100) not null,
    fixture_value varchar(500) not null,
    loaded_at timestamp not null
);
