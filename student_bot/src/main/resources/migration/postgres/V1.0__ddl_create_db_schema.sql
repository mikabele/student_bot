DROP TABLE IF EXISTS students CASCADE;
CREATE TABLE students
(
    id SERIAL,
    first_name VARCHAR(32) NOT NULL,
    last_name VARCHAR(30) NOT NULL,
    "group" INT NOT NULL,
    course INT NOT NULL,
    tgUsername VARCHAR(100) DEFAULT NULL,
    PRIMARY KEY(id)
);
