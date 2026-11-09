package com.att.tdp.popcorn_palace.dto;

import com.att.tdp.popcorn_palace.model.Showtime;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class ShowtimeDTO
{
    private Long id;
    private Long movieId;
    private String theater;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double price;

    public static ShowtimeDTO convertToDTO(Showtime showtime)
    {
        return new ShowtimeDTO(
                showtime.getId(),
                showtime.getMovie().getId(),
                showtime.getTheater(),
                showtime.getStart_time(),
                showtime.getEnd_time(),
                showtime.getPrice()
        );
    }
}