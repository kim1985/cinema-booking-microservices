package com.cinema.movie.service;

import com.cinema.movie.entity.Movie;
import com.cinema.movie.entity.Screening;
import com.cinema.movie.repository.MovieRepository;
import com.cinema.movie.repository.ScreeningRepository;
import com.cinema.shared.dto.MovieResponse;
import com.cinema.shared.dto.ScreeningResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class MovieService {

    private final MovieRepository movieRepository;
    private final ScreeningRepository screeningRepository;

    // Temporaneamente disabilitato cache per evitare problemi serializzazione
    // @Cacheable(value = "movies", key = "'all'")
    public List<MovieResponse.MovieInfo> getAllMovies() {
        return movieRepository.findAll()
                .stream()
                .map(this::mapToMovieInfo)
                .toList();
    }

    // @Cacheable(value = "movies", key = "#id")
    public Optional<MovieResponse.MovieDetails> getMovieWithScreenings(Long id) {
        return movieRepository.findById(id)
                .map(this::mapToMovieDetails);
    }

    public List<MovieResponse.MovieInfo> getMoviesWithAvailableScreenings() {
        return movieRepository.findMoviesWithAvailableScreenings()
                .stream()
                .map(this::mapToMovieInfo)
                .toList();
    }

    public List<ScreeningResponse> getTodayScreenings() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        return screeningRepository.findTodayScreenings(startOfDay, endOfDay)
                .stream()
                .map(this::mapToScreeningResponse)
                .toList();
    }

    public List<MovieResponse.MovieInfo> searchMovies(String genre, String title) {
        return movieRepository.searchMovies(genre, title, null, null)
                .stream()
                .map(this::mapToMovieInfo)
                .toList();
    }

    // Metodi per API interne - cache temporaneamente disabilitata

    // @Cacheable(value = "screenings", key = "#id", unless = "#result == null")
    public Optional<ScreeningResponse> getScreening(Long id) {
        log.debug("Getting screening with id: {}", id);
        return screeningRepository.findById(id)
                .map(this::mapToScreeningResponse);
    }

    @Transactional
    public void updateAvailableSeats(Long screeningId, Integer seatsDelta) {
        screeningRepository.findById(screeningId).ifPresentOrElse(screening -> {
            int currentSeats = screening.getAvailableSeats();
            int newSeats = currentSeats + seatsDelta;
            // Vincoli business: non può essere negativo o superare il totale
            newSeats = Math.max(0, Math.min(newSeats, screening.getTotalSeats()));
            screening.setAvailableSeats(newSeats);
            screeningRepository.save(screening);

            log.info("Updated screening {} seats: {} -> {} (delta: {})",
                    screeningId, currentSeats, newSeats, seatsDelta);
        }, () -> {
            log.warn("Screening {} not found for seat update", screeningId);
        });
    }

    // Mapping methods - ora gestiscono meglio i null
    private MovieResponse.MovieInfo mapToMovieInfo(Movie movie) {
        if (movie == null) return null;

        return new MovieResponse.MovieInfo(
                movie.getId(),
                movie.getTitle(),
                movie.getGenre(),
                movie.getDuration()
        );
    }

    private MovieResponse.MovieDetails mapToMovieDetails(Movie movie) {
        if (movie == null) return null;

        List<ScreeningResponse> screenings = movie.getScreenings()
                .stream()
                .map(this::mapToScreeningResponse)
                .filter(screening -> screening != null) // Filtra eventuali null
                .toList();

        return new MovieResponse.MovieDetails(
                movie.getId(),
                movie.getTitle(),
                movie.getGenre(),
                movie.getDuration(),
                movie.getDescription(),
                screenings
        );
    }

    private ScreeningResponse mapToScreeningResponse(Screening screening) {
        if (screening == null || screening.getMovie() == null) {
            log.warn("Null screening or movie found during mapping");
            return null;
        }

        return new ScreeningResponse(
                screening.getId(),
                screening.getMovie().getId(),
                screening.getMovie().getTitle(),
                screening.getStartTime(),
                screening.getAvailableSeats(),
                screening.getPrice()
        );
    }
}