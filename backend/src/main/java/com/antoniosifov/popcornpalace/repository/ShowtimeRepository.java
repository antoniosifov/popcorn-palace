package com.antoniosifov.popcornpalace.repository;

import com.antoniosifov.popcornpalace.model.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<Showtime,Long>
{
    List<Showtime> findAllByTheater(String theater);
}
