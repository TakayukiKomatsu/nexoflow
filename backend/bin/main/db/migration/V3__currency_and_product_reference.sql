create table currencies (code varchar(3) primary key, decimal_scale integer not null, active boolean not null);
create table product_types (code varchar(50) primary key, active boolean not null);
insert into currencies (code, decimal_scale, active) values ('BRL', 2, true), ('USD', 2, true);
insert into product_types (code, active) values ('MERCANTILE_INVOICE', true), ('POST_DATED_CHEQUE', true);
