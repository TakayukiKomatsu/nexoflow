create table parents (
    id uuid primary key,
    code varchar(20) not null unique
);

create table children (
    id uuid primary key,
    parent_id uuid not null,
    java_parent_id uuid not null,
    table_parent_id uuid not null,
    amount integer not null check (amount >= 0),
    money numeric(19,4) not null,
    code varchar(20) not null,
    constraint children_table_parent_fk
        foreign key (table_parent_id) references parents(code)
);

alter table children add constraint children_parent_fk
    foreign key (parent_id) references parents(id);
alter table children add constraint children_amount_check check (amount > 0);
alter table children add constraint children_code_unique unique (code);
