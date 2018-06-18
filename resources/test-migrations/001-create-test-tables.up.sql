-- Ensure this is H2
CALL 5*5;

-- Table to simulate the collections table in Oracle DB
DROP TABLE IF EXISTS collections;
create table collections (
	collectionid varchar(64) NOT NULL,
    collectionname varchar(255),
    collection_type  integer NOT NULL
);

DROP TABLE IF EXISTS categories;
create table categories (
  id bigint not null constraint categories_pkey primary key,
	name varchar(1000) not null,
	image_desc varchar(500),
	image_url varchar(100),
	blurb_url varchar(4000),
	short_description varchar(1000),
	lead_object_id varchar(100)
);