DROP TABLE IF EXISTS student CASCADE;
CREATE TABLE student
(
    id          SERIAL,
    first_name  VARCHAR(32) NOT NULL,
    last_name   VARCHAR(30) NOT NULL,
    "group"     INT         NOT NULL,
    course      INT         NOT NULL,
    tg_username VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS queue_series CASCADE;
CREATE TABLE queue_series
(
    id      SERIAL,
    "name"  VARCHAR(50) NOT NULL,
    "group" INT         NOT NULL,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS queue CASCADE;
CREATE TABLE queue
(
    id              SERIAL,
    "date"          DATE NOT NULL,
    queue_series_id INT  NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (queue_series_id) REFERENCES queue_series (id)
);

DROP TABLE IF EXISTS record CASCADE;
CREATE TABLE record
(
    id         SERIAL,
    place      INT NOT NULL,
    student_id INT NOT NULL,
    queue_id   INT NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (student_id) REFERENCES student (id),
    FOREIGN KEY (queue_id) REFERENCES queue (id)
);