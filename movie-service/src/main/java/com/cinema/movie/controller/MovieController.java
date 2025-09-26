// File: movie-service/src/main/java/com/cinema/movie/controller/MovieController.java
package com.cinema.movie.controller;

import com.cinema.shared.dto.MovieResponse;
import com.cinema.shared.dto.ScreeningResponse;
import com.cinema.movie.service.MovieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
@Slf4j
public class MovieController {

    private final MovieService movieService;

    // API Pubbliche (via Gateway)

    @GetMapping
    public ResponseEntity<List<MovieResponse.MovieInfo>> getAllMovies() {
        List<MovieResponse.MovieInfo> movies = movieService.getAllMovies();
        return ResponseEntity.ok(movies);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MovieResponse.MovieDetails> getMovie(@PathVariable Long id) {
        return movieService.getMovieWithScreenings(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/available")
    public ResponseEntity<List<MovieResponse.MovieInfo>> getAvailableMovies() {
        List<MovieResponse.MovieInfo> movies = movieService.getMoviesWithAvailableScreenings();
        return ResponseEntity.ok(movies);
    }

    @GetMapping("/screenings/today")
    public ResponseEntity<List<ScreeningResponse>> getTodayScreenings() {
        List<ScreeningResponse> screenings = movieService.getTodayScreenings();
        return ResponseEntity.ok(screenings);
    }

    @GetMapping("/search")
    public ResponseEntity<List<MovieResponse.MovieInfo>> searchMovies(
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String title) {

        List<MovieResponse.MovieInfo> movies = movieService.searchMovies(genre, title);
        return ResponseEntity.ok(movies);
    }

    // API Interne per altri microservizi

    @GetMapping("/internal/screenings/{id}")
    public ResponseEntity<ScreeningResponse> getScreeningInternal(@PathVariable Long id) {
        log.debug("Internal API call: getScreening for id {}", id);
        return movieService.getScreening(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/internal/screenings/{id}/seats")
    public ResponseEntity<Void> updateAvailableSeats(
            @PathVariable Long id,
            @RequestParam Integer seatsDelta) {

        log.debug("Internal API call: updateSeats for screening {} with delta {}", id, seatsDelta);
        movieService.updateAvailableSeats(id, seatsDelta);
        return ResponseEntity.ok().build();
    }
}