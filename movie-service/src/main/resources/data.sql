-- File: movie-service/src/main/resources/data.sql

-- Film di esempio
INSERT INTO movies (title, genre, duration, description, created_at, updated_at) VALUES
                                                                                     ('Avatar: The Way of Water', 'Sci-Fi', 192, 'Sequel del famoso film Avatar', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                                                                                     ('Top Gun: Maverick', 'Action', 131, 'Sequel di Top Gun con Tom Cruise', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                                                                                     ('The Batman', 'Action', 176, 'Nuovo film su Batman con Robert Pattinson', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                                                                                     ('Dune', 'Sci-Fi', 155, 'Adattamento del classico romanzo di fantascienza', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                                                                                     ('Spider-Man: No Way Home', 'Action', 148, 'Multiverso di Spider-Man', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Proiezioni di esempio (alcune per oggi, alcune future)
INSERT INTO screenings (movie_id, start_time, total_seats, available_seats, price, created_at) VALUES
-- Avatar proiezioni
(1, DATEADD(HOUR, 2, CURRENT_TIMESTAMP), 100, 85, 12.50, CURRENT_TIMESTAMP),
(1, DATEADD(HOUR, 5, CURRENT_TIMESTAMP), 100, 92, 12.50, CURRENT_TIMESTAMP),
(1, DATEADD(DAY, 1, CURRENT_TIMESTAMP), 100, 100, 12.50, CURRENT_TIMESTAMP),

-- Top Gun proiezioni
(2, DATEADD(HOUR, 3, CURRENT_TIMESTAMP), 80, 65, 11.00, CURRENT_TIMESTAMP),
(2, DATEADD(HOUR, 6, CURRENT_TIMESTAMP), 80, 78, 11.00, CURRENT_TIMESTAMP),
(2, DATEADD(DAY, 1, CURRENT_TIMESTAMP), 80, 80, 11.00, CURRENT_TIMESTAMP),

-- The Batman proiezioni
(3, DATEADD(HOUR, 4, CURRENT_TIMESTAMP), 120, 45, 13.00, CURRENT_TIMESTAMP),
(3, DATEADD(DAY, 1, CURRENT_TIMESTAMP), 120, 120, 13.00, CURRENT_TIMESTAMP),

-- Dune proiezioni
(4, DATEADD(HOUR, 1, CURRENT_TIMESTAMP), 90, 30, 11.50, CURRENT_TIMESTAMP),
(4, DATEADD(HOUR, 7, CURRENT_TIMESTAMP), 90, 88, 11.50, CURRENT_TIMESTAMP),

-- Spider-Man proiezioni
(5, DATEADD(HOUR, 3, CURRENT_TIMESTAMP), 150, 12, 14.00, CURRENT_TIMESTAMP),
(5, DATEADD(HOUR, 8, CURRENT_TIMESTAMP), 150, 145, 14.00, CURRENT_TIMESTAMP);