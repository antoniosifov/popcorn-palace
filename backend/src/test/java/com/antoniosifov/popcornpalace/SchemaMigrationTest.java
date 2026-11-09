package com.antoniosifov.popcornpalace;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks that migrations carry existing data forward correctly, not only that
 * they produce the right schema on an empty database. Each test builds a
 * separate database at an old version, fills it the way that version of the
 * application would have, then migrates to the latest version.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaMigrationTest
{
    private static final String DATABASE = "migration_check";

    @Autowired
    private PostgreSQLContainer postgres;

    @Autowired
    private DataSource applicationDataSource;

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createEmptyDatabase()
    {
        JdbcTemplate admin = new JdbcTemplate(applicationDataSource);
        admin.execute("DROP DATABASE IF EXISTS " + DATABASE + " WITH (FORCE)");
        admin.execute("CREATE DATABASE " + DATABASE);

        String url = "jdbc:postgresql://%s:%d/%s".formatted(postgres.getHost(), postgres.getMappedPort(5432), DATABASE);
        dataSource = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @DisplayName("Data from V1 is carried forward through every migration: halls with seats, prices, accounts, bookings with seat rows")
    @Test
    void migratesExistingDataFromV1()
    {
        migrateTo("1");
        jdbc.update("""
                INSERT INTO movies (title, genre, duration_minutes, rating, release_year)
                VALUES ('Interstellar', 'Science Fiction', 169, 8.7, 2014)""");
        jdbc.update("""
                INSERT INTO showtimes (movie_id, theater, start_time, end_time, price) VALUES
                    (1, 'Hall 1', '2030-01-15T18:00:00Z', '2030-01-15T20:00:00Z', 14.50),
                    (1, 'Hall 1', '2030-01-15T21:00:00Z', '2030-01-15T23:00:00Z', 16.00),
                    (1, 'IMAX',   '2030-01-15T18:00:00Z', '2030-01-15T20:00:00Z', 25.00)""");
        jdbc.update("""
                INSERT INTO bookings (id, showtime_id, seat_number, user_id, created_at) VALUES
                    ('00000000-0000-0000-0000-000000000001', 1, 1,   '84438967-f68f-4fa0-b620-0f08217e76af', now()),
                    ('00000000-0000-0000-0000-000000000002', 1, 21,  '84438967-f68f-4fa0-b620-0f08217e76af', now()),
                    ('00000000-0000-0000-0000-000000000003', 2, 300, '84438967-f68f-4fa0-b620-0f08217e76af', now()),
                    ('00000000-0000-0000-0000-000000000004', 3, 20,  '84438967-f68f-4fa0-b620-0f08217e76af', now())""");

        migrateToLatest();

        // One hall per distinct theater, each with 15 x 20 standard seats.
        assertThat(jdbc.queryForList("SELECT name, row_count, seat_count FROM halls ORDER BY name"))
                .containsExactly(
                        Map.of("name", "Hall 1", "row_count", 15, "seat_count", 300),
                        Map.of("name", "IMAX", "row_count", 15, "seat_count", 300));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seats", Long.class)).isEqualTo(600);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT category) FROM seats", Long.class)).isEqualTo(1);

        // Showtimes point at the hall named after their old theater; the old price became the STANDARD price.
        assertThat(jdbc.queryForList("""
                SELECT s.id, h.name AS hall, p.category, p.price::text AS price
                FROM showtimes s
                         JOIN halls h ON h.id = s.hall_id
                         JOIN showtime_prices p ON p.showtime_id = s.id
                ORDER BY s.id"""))
                .containsExactly(
                        Map.of("id", 1L, "hall", "Hall 1", "category", "STANDARD", "price", "14.50"),
                        Map.of("id", 2L, "hall", "Hall 1", "category", "STANDARD", "price", "16.00"),
                        Map.of("id", 3L, "hall", "IMAX", "category", "STANDARD", "price", "25.00"));

        // Seat numbers 1..300 map onto rows of 20; each booking keeps the price of its showtime.
        // V4: each old single-seat booking is now an order with exactly one seat row.
        assertThat(jdbc.queryForList("""
                SELECT right(b.id::text, 1) AS booking, h.name AS hall, se.row_number AS seat_row,
                       se.seat_number AS seat, bs.price::text AS price, b.total_price::text AS total
                FROM bookings b
                         JOIN booking_seats bs ON bs.booking_id = b.id AND bs.showtime_id = b.showtime_id
                         JOIN seats se ON se.id = bs.seat_id
                         JOIN halls h ON h.id = b.hall_id
                ORDER BY b.id"""))
                .containsExactly(
                        Map.of("booking", "1", "hall", "Hall 1", "seat_row", 1, "seat", 1, "price", "14.50", "total", "14.50"),
                        Map.of("booking", "2", "hall", "Hall 1", "seat_row", 2, "seat", 1, "price", "14.50", "total", "14.50"),
                        Map.of("booking", "3", "hall", "Hall 1", "seat_row", 15, "seat", 20, "price", "16.00", "total", "16.00"),
                        Map.of("booking", "4", "hall", "IMAX", "seat_row", 1, "seat", 20, "price", "25.00", "total", "25.00"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_seats", Long.class)).isEqualTo(4);

        // V5: bookings from before payments are confirmed, have no payment, and their seats are active.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM bookings WHERE status = 'CONFIRMED' AND payment_id IS NULL AND cancelled_at IS NULL",
                Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_seats WHERE active", Long.class)).isEqualTo(4);
        // A seat sold before the migration still cannot be sold again ...
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO booking_seats (booking_id, showtime_id, hall_id, seat_id, price)
                SELECT booking_id, showtime_id, hall_id, seat_id, price FROM booking_seats LIMIT 1"""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_booking_seats_showtime_seat");
        // ... unless that sale is no longer active.
        jdbc.update("UPDATE booking_seats SET active = false WHERE id = (SELECT min(id) FROM booking_seats)");
        assertThat(jdbc.update("""
                INSERT INTO booking_seats (booking_id, showtime_id, hall_id, seat_id, price)
                SELECT booking_id, showtime_id, hall_id, seat_id, price FROM booking_seats ORDER BY id LIMIT 1""")).isEqualTo(1);

        // The columns replaced by the new model are gone.
        List<String> columns = jdbc.queryForList("""
                SELECT table_name || '.' || column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name IN ('showtimes', 'bookings')""", String.class);
        assertThat(columns).doesNotContain("showtimes.theater", "showtimes.price",
                "bookings.seat_number", "bookings.seat_id", "bookings.price");

        // V3: the user id carried by the old bookings became one placeholder account
        // that cannot be logged into, and bookings now reference accounts.
        assertThat(jdbc.queryForList("SELECT id::text AS id, email, password_hash, role FROM users"))
                .containsExactly(Map.of(
                        "id", "84438967-f68f-4fa0-b620-0f08217e76af",
                        "email", "legacy-84438967-f68f-4fa0-b620-0f08217e76af@placeholder.invalid",
                        "password_hash", "!",
                        "role", "CUSTOMER"));
        assertThatThrownBy(() -> jdbc.update("UPDATE bookings SET user_id = '00000000-0000-0000-0000-00000000dead'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_bookings_user");

        // And the migrated data is protected by the new rules.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO showtimes (movie_id, hall_id, start_time, end_time)
                VALUES (1, (SELECT id FROM halls WHERE name = 'Hall 1'), '2030-01-15T19:00:00Z', '2030-01-15T19:30:00Z')"""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_showtimes_hall_time");
    }

    @DisplayName("Every migration applies cleanly to an empty database")
    @Test
    void migratesEmptyDatabase()
    {
        migrateToLatest();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Long.class)).isGreaterThanOrEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM halls", Long.class)).isZero();
    }

    private void migrateTo(String version)
    {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(version).load().migrate();
    }

    private void migrateToLatest()
    {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }
}
