package com.cinema.movie.repository;

import com.cinema.movie.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    List<Movie> findByGenreIgnoreCase(String genre);
    List<Movie> findByTitleContainingIgnoreCase(String title);
    List<Movie> findByDurationBetween(Integer minDuration, Integer maxDuration);

    @Query("""
        SELECT DISTINCT m FROM Movie m 
        JOIN m.screenings s 
        WHERE s.startTime > CURRENT_TIMESTAMP 
        AND s.availableSeats > 0
        ORDER BY m.title
        """)
    List<Movie> findMoviesWithAvailableScreenings();

    @Query("""
        SELECT m FROM Movie m 
        WHERE (:genre IS NULL OR LOWER(m.genre) = LOWER(:genre))
        AND (:title IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', :title, '%')))
        AND (:minDuration IS NULL OR m.duration >= :minDuration)
        AND (:maxDuration IS NULL OR m.duration <= :maxDuration)
        ORDER BY m.title
        """)
    List<Movie> searchMovies(
            @Param("genre") String genre,
            @Param("title") String title,
            @Param("minDuration") Integer minDuration,
            @Param("maxDuration") Integer maxDuration
    );
}