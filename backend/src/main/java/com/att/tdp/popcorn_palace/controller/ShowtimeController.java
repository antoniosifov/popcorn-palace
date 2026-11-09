package com.att.tdp.popcorn_palace.controller;

import com.att.tdp.popcorn_palace.dto.ShowtimeDTO;
import com.att.tdp.popcorn_palace.model.Showtime;
import com.att.tdp.popcorn_palace.service.ShowtimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import static com.att.tdp.popcorn_palace.dto.ShowtimeDTO.*;

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
