package com.antoniosifov.popcornpalace.controller;

import com.antoniosifov.popcornpalace.dto.ShowtimeDTO;
import com.antoniosifov.popcornpalace.model.Showtime;
import com.antoniosifov.popcornpalace.service.ShowtimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import static com.antoniosifov.popcornpalace.dto.ShowtimeDTO.*;

@RestController
@RequestMapping("/showtimes")
public class ShowtimeController
{
    private final ShowtimeService showtimeService;

    public ShowtimeController (ShowtimeService showtimeService)
    {
        this.showtimeService = showtimeService;
    }

    @GetMapping("/all")
    public ResponseEntity<List<ShowtimeDTO>> getAllShowtimes()
    {
        return ResponseEntity.ok(showtimeService.getAllShowtimes());
    }

    @GetMapping("/{showtimeId}")
    public ResponseEntity<ShowtimeDTO> getShowtimeById(@PathVariable Long showtimeId)
    {
        ShowtimeDTO showtimeDTO = showtimeService.getShowtimeById(showtimeId);
        return ResponseEntity.ok(showtimeDTO);
    }

    @PostMapping
    public ResponseEntity<ShowtimeDTO> addShowtime(@RequestBody Showtime showtime)
    {
        Showtime savedShowtime = showtimeService.save(showtime);
        return ResponseEntity.ok(convertToDTO(savedShowtime));
    }

    @PostMapping("/update/{showtimeId}")
    public ResponseEntity<Void> updateShowtime(@RequestBody Showtime showtime, @PathVariable("showtimeId") Long id)
    {
        showtimeService.upgradeById(showtime, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{showtimeId}")
    public ResponseEntity<Void> deleteShowtime(@PathVariable Long showtimeId)
    {
        showtimeService.deleteById(showtimeId);
        return ResponseEntity.ok().build();
    }
}
