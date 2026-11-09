package com.antoniosifov.popcornpalace.movie;

import com.antoniosifov.popcornpalace.common.web.PageResponse;
import com.antoniosifov.popcornpalace.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/movies")
@Tag(name = "Movies", description = "The movie catalog")
public class MovieController
{
    private final MovieService movieService;

    public MovieController(MovieService movieService)
    {
        this.movieService = movieService;
    }

    @GetMapping
    @Operation(summary = "List movies", description = "Paginated. Sortable by id, title, genre, durationMinutes, rating, releaseYear.")
    public PageResponse<MovieResponse> list(@ParameterObject @PageableDefault(size = 20, sort = "title") Pageable pageable)
    {
        return movieService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a movie")
    public MovieResponse get(@PathVariable Long id)
    {
        return movieService.get(id);
    }

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Add a movie")
    public ResponseEntity<MovieResponse> create(@Valid @RequestBody MovieRequest request)
    {
        MovieResponse created = movieService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Replace a movie")
    public MovieResponse update(@PathVariable Long id, @Valid @RequestBody MovieRequest request)
    {
        return movieService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Delete a movie", description = "Fails with 409 while the movie still has showtimes.")
    public void delete(@PathVariable Long id)
    {
        movieService.delete(id);
    }
}
