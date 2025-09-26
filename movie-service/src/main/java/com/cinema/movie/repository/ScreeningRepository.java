package com.cinema.movie.repository;

import com.cinema.movie.entity.Screening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    List<Screening> findByMovieIdOrderByStartTime(Long movieId);

    @Query("""
        SELECT s FROM Screening s 
        WHERE s.startTime > :now 
        AND s.availableSeats > 0
        ORDER BY s.startTime
        """)
    List<Screening> findAvailableScreenings(@Param("now") LocalDateTime now);

    @Query("""
        SELECT s FROM Screening s 
        WHERE s.startTime >= :startOfDay 
        AND s.startTime < :endOfDay
        AND s.availableSeats > 0
        ORDER BY s.startTime
        """)
    List<Screening> findTodayScreenings(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );
}