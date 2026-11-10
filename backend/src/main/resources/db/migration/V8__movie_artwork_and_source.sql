-- What a customer sees of a movie besides its facts (a description, a poster,
-- a trailer, who made it), and where the movie came from.
--
-- tmdb_id is the movie's id at TMDB when it was imported from there: the
-- import finds its own movies by it, so running it again updates them instead
-- of adding them twice. in_cinemas marks what the latest import found playing;
-- a movie that has left the cinemas keeps its bookings and its page.
ALTER TABLE movies
    ADD COLUMN overview     VARCHAR(4000),
    ADD COLUMN tagline      VARCHAR(300),
    ADD COLUMN poster_url   VARCHAR(500),
    ADD COLUMN backdrop_url VARCHAR(500),
    ADD COLUMN trailer_url  VARCHAR(500),
    ADD COLUMN director     VARCHAR(200),
    ADD COLUMN starring     VARCHAR(600),
    ADD COLUMN tmdb_id      BIGINT,
    ADD COLUMN popularity   DOUBLE PRECISION,
    ADD COLUMN in_cinemas   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT uq_movies_tmdb_id UNIQUE (tmdb_id);
