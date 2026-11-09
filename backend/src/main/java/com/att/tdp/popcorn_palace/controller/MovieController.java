package com.att.tdp.popcorn_palace.controller;

import com.att.tdp.popcorn_palace.model.Movie;
import com.att.tdp.popcorn_palace.service.MovieService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/movies")
public class MovieController
{
    private final MovieService movieService;

    public MovieController(MovieService movieService)
    {
        this.movieService = movieService;
    }

    @GetMapping("/all")
    public ResponseEntity<List<Movie>> fetchAllMovies()
    {
        List<Movie> allMovies = movieService.getAllMovies();
        return ResponseEntity.ok(allMovies);
    }

    @PostMapping
    public ResponseEntity<Movie> addMovie(@RequestBody Movie movie)
    {
        Movie savedMovie = movieService.save(movie);
        return ResponseEntity.ok(savedMovie);
    }

    @PostMapping("/update/{movieTitle}")
    public ResponseEntity<Void> updateMovie(@RequestBody Movie movie, @PathVariable("movieTitle") String title)
    {
        movieService.upgradeByTitle(movie, title);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{movieTitle}")
    public ResponseEntity<Void> deleteMovie(@PathVariable("movieTitle") String title)
    {
        movieService.deleteByTitle(title);
        return ResponseEntity.ok().build();
    }
}
