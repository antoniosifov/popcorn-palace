package com.antoniosifov.popcornpalace.hall;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/halls")
@Tag(name = "Halls", description = "Screening rooms and their seat layouts")
public class HallController
{
    private final HallService hallService;

    public HallController(HallService hallService)
    {
        this.hallService = hallService;
    }

    @GetMapping
    @Operation(summary = "List halls", description = "Paginated. Sortable by id, name, rowCount, seatCount.")
    public PageResponse<HallSummaryResponse> list(@ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable)
    {
        return hallService.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a hall with its row-by-row layout")
    public HallResponse get(@PathVariable Long id)
    {
        return hallService.get(id);
    }

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Create a hall", description = """
            The layout is given as blocks of identical rows, from the screen to the back, \
            and cannot be changed afterwards. At most 60 rows and 1000 seats.""")
    public ResponseEntity<HallResponse> create(@Valid @RequestBody HallRequest request)
    {
        HallResponse created = hallService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Delete a hall", description = "Fails with 409 while the hall still has showtimes.")
    public void delete(@PathVariable Long id)
    {
        hallService.delete(id);
    }
}
