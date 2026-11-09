package com.antoniosifov.popcornpalace.hall;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HallRepository extends JpaRepository<Hall, Long>
{
    boolean existsByName(String name);
}
