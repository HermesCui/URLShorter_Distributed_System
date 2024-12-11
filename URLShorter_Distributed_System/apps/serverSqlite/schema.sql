/**
create table bitly (
	shorturl text primary key,
	longurl text not null
);
**/
CREATE TABLE IF NOT EXISTS bitly (
	shorturl TEXT PRIMARY KEY,
	longurl TEXT NOT NULL,
	opTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);