INSERT INTO hours (id, id_user, date, start, finish) VALUES
                                                         (1001, 2, DATE '2024-01-02', TIMESTAMP '2024-01-02 09:00:00', TIMESTAMP '2024-01-02 17:30:00'),
                                                         (1002, 2, DATE '2024-01-03', TIMESTAMP '2024-01-03 09:00:00', TIMESTAMP '2024-01-03 17:00:00'),
                                                         (1003, 2, DATE '2024-01-04', TIMESTAMP '2024-01-04 09:00:00', NULL);

INSERT INTO hours (id, id_user, date, start, finish) VALUES
    (1005, 2, CURRENT_DATE, DATEADD(HOUR, 9, CAST(CURRENT_DATE AS TIMESTAMP)), NULL);

INSERT INTO hours (id, id_user, date, start, finish) VALUES
    (1004, 1, DATE '2024-01-10', TIMESTAMP '2024-01-10 08:00:00', TIMESTAMP '2024-01-10 16:00:00');