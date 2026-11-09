package com.antoniosifov.popcornpalace.movie;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long>
{
    boolean existsByTitle(String title);

    boolean existsByTitleAndIdNot(String title, Long id);
}
