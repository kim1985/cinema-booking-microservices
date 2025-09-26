package com.cinema.shared.dto;

import java.util.List;

// Sealed interface per type safety - Java 21
public sealed interface MovieResponse permits MovieResponse.MovieInfo, MovieResponse.MovieDetails {

    record MovieInfo(
            Long id,
            String title,
            String genre,
            Integer duration
    ) implements MovieResponse {}

    record MovieDetails(
            Long id,
            String title,
            String genre,
            Integer duration,
            String description,
            List<ScreeningResponse> screenings
    ) implements MovieResponse {}
}