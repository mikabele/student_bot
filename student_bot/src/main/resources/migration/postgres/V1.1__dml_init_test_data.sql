COPY students (last_name, first_name,course, "group")
    FROM E'X:\\Private\\student_bot\\student_bot\\src\\main\\resources\\sql_data\\3_c_12_g_students.csv'
    DELIMITER ','
    CSV HEADER;