COPY student (last_name, first_name, course, "group")
    FROM E'X:\\Private\\student_bot\\student_bot\\src\\main\\resources\\sql_data\\students\\3_c_12_g_students.csv'
    DELIMITER ','
    CSV HEADER;

COPY queue_series ("name", "group")
    FROM E'X:\\Private\\student_bot\\student_bot\\src\\main\\resources\\sql_data\\queues\\3_c_12_g_queues.csv'
    DELIMITER ','
    CSV HEADER;