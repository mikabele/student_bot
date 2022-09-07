DROP TYPE IF EXISTS ROLE CASCADE;
CREATE TYPE ROLE AS ENUM ('admin','user');

DROP SEQUENCE IF EXISTS student_id_seq CASCADE;
CREATE SEQUENCE student_id_seq INCREMENT BY 1 NO MINVALUE NO MAXVALUE START 1 CACHE 1;

DROP TABLE IF EXISTS student CASCADE;
CREATE TABLE student
(
    id         INT          DEFAULT nextval('student_id_seq'),
    first_name VARCHAR(32)  NOT NULL,
    last_name  VARCHAR(30)  NOT NULL,
    university VARCHAR(100) NOT NULL,
    "group"    INT          NOT NULL,
    course     INT          NOT NULL,
    st_role    ROLE         DEFAULT 'user'::ROLE,
    tg_user_id VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id)
);


DROP SEQUENCE IF EXISTS queue_series_id_seq CASCADE;
CREATE SEQUENCE queue_series_id_seq INCREMENT BY 1 NO MINVALUE NO MAXVALUE START 1 CACHE 1;

DROP TABLE IF EXISTS queue_series CASCADE;
CREATE TABLE queue_series
(
    id         INT DEFAULT nextval('queue_series_id_seq'),
    "name"     VARCHAR(50)  NOT NULL,
    university VARCHAR(100) NOT NULL,
    "group"    INT          NOT NULL,
    course     INT          NOT NULL,
    PRIMARY KEY (id)
);

DROP SEQUENCE IF EXISTS queue_id_seq CASCADE;
CREATE SEQUENCE queue_id_seq INCREMENT BY 1 NO MINVALUE NO MAXVALUE START 1 CACHE 1;

DROP TABLE IF EXISTS queue CASCADE;
CREATE TABLE queue
(
    id              INT DEFAULT nextval('queue_id_seq'),
    "date"          DATE NOT NULL,
    queue_series_id INT  NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (queue_series_id) REFERENCES queue_series (id)
);

DROP SEQUENCE IF EXISTS record_id_seq CASCADE;
CREATE SEQUENCE record_id_seq INCREMENT BY 1 NO MINVALUE NO MAXVALUE START 1 CACHE 1;

DROP TABLE IF EXISTS record CASCADE;
CREATE TABLE record
(
    id         INT DEFAULT nextval('record_id_seq'),
    place      INT NOT NULL,
    student_id INT NOT NULL,
    queue_id   INT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (student_id) REFERENCES student (id),
    FOREIGN KEY (queue_id) REFERENCES queue (id)
);