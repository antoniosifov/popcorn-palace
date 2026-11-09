# Popcorn Palace API

A movie ticket booking backend built with Java 21 and Spring Boot 4. It manages a movie catalog, halls with real seat layouts, showtimes with per-category prices and seat bookings for registered users, and keeps its rules intact when requests race each other.

[Frontend](../frontend/README.md) · [Portfolio](https://antoniosifov.com) · [LinkedIn](https://www.linkedin.com/in/anton-iosifov/)

## Highlights

- Versioned REST API (`/api/v1`) with request/response DTOs, pagination and an allow-list for sorting
- Halls with row-by-row seat layouts, and a seat map per showtime with prices and availability
- Accounts with stateless JWT authentication and two roles: customers book, administrators manage the catalog
- Interactive API documentation generated from the code (Swagger UI, OpenAPI 3.1)
- Database schema owned by Flyway migrations that carry existing data forward; Hibernate only validates it
- Business rules enforced by database constraints, so they hold under concurrency
- Uniform error responses in the RFC 9457 "problem details" format with machine-readable codes
- 126 automated tests, most of them calling the API over HTTP against a real PostgreSQL

## Architecture

The code is organized by feature. Each feature package holds its controller, service, repository, entity and DTOs; `common` holds what they share.

```mermaid
flowchart LR
    Client["API client"] --> Controller["Controllers<br/>validate request DTOs"]
    Controller --> Service["Services<br/>business rules, transactions"]
    Service --> Repository["Spring Data JPA repositories"]
    Repository --> Database[("PostgreSQL<br/>constraints + Flyway schema")]
    Controller -. errors .-> Handler["ApiExceptionHandler<br/>problem details"]
    Service -. errors .-> Handler
    Database -. constraint violations .-> Handler
```

```text
src/main/java/com/antoniosifov/popcornpalace/
├── movie/       # catalog
├── hall/        # halls and their seats
├── showtime/    # scheduling, prices per seat category
├── booking/     # seat booking, seat map
├── user/        # accounts
├── auth/        # registration, login, token issuing
├── security/    # who may call what, token verification
└── common/      # error handling, pagination, configuration
src/main/resources/db/migration/   # Flyway SQL migrations
```

Packages depend on each other in one direction only: `booking` → `showtime` → `hall`, `movie`.

## Technology

| Area | Technologies |
| --- | --- |
| Runtime | Java 21, Spring Boot 4.1 |
| API | Spring Web MVC, Bean Validation, springdoc-openapi |
| Security | Spring Security (OAuth2 resource server), self-issued JWT (HS256), BCrypt |
| Persistence | Spring Data JPA, Hibernate 7, PostgreSQL 17, Flyway |
| Testing | JUnit 5, MockMvc, Testcontainers (PostgreSQL), AssertJ |
| Tooling | Maven Wrapper, Docker, Docker Compose, GitHub Actions, Lombok |

## API

Base URL: `http://localhost:8080/api/v1`. Interactive documentation: http://localhost:8080/swagger-ui.html (OpenAPI JSON at `/v3/api-docs`).

| Method | Endpoint | Purpose | Who | Success |
| --- | --- | --- | --- | --- |
| `POST` | `/auth/register` | Create a customer account, returns a token | anyone | `201` |
| `POST` | `/auth/login` | Log in, returns a token | anyone | `200` |
| `GET` | `/users/me` | The account that owns the token | logged in | `200` |
| `GET` | `/movies` | List movies (paginated) | anyone | `200` |
| `GET` | `/movies/{id}` | Get a movie | anyone | `200` |
| `POST` | `/movies` | Add a movie | admin | `201` + `Location` |
| `PUT` | `/movies/{id}` | Replace a movie | admin | `200` |
| `DELETE` | `/movies/{id}` | Delete a movie | admin | `204` |
| `GET` | `/halls` | List halls (paginated) | anyone | `200` |
| `GET` | `/halls/{id}` | Get a hall with its row-by-row layout | anyone | `200` |
| `POST` | `/halls` | Create a hall and its seats | admin | `201` + `Location` |
| `DELETE` | `/halls/{id}` | Delete a hall and its seats | admin | `204` |
| `GET` | `/showtimes` | List showtimes (paginated, optional `movieId` filter) | anyone | `200` |
| `GET` | `/showtimes/{id}` | Get a showtime | anyone | `200` |
| `POST` | `/showtimes` | Schedule a showtime | admin | `201` + `Location` |
| `PUT` | `/showtimes/{id}` | Replace a showtime | admin | `200` |
| `DELETE` | `/showtimes/{id}` | Delete a showtime | admin | `204` |
| `GET` | `/showtimes/{id}/seats` | Seat map: every seat with price and availability | anyone | `200` |
| `POST` | `/bookings` | Book a seat for the logged-in user | logged in | `201` + `Location` |
| `GET` | `/bookings` | List my bookings (paginated, newest first) | logged in | `200` |
| `GET` | `/bookings/{id}` | Get a booking | owner or admin | `200` |

### Authentication and roles

Browsing is public. Everything else needs an access token, sent as `Authorization: Bearer <accessToken>`.

Register (or log in with `email` and `password` at `/auth/login`):

```json
{
  "email": "anna@example.com",
  "password": "correct-horse-battery",
  "displayName": "Anna"
}
```

Both calls return a token and the account:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": { "id": "85df2e82-eb22-4966-8885-8234a8556154", "email": "anna@example.com", "displayName": "Anna", "role": "CUSTOMER", "createdAt": "2030-01-10T12:00:00.000Z" }
}
```

| Role | Can do |
| --- | --- |
| anonymous | Register, log in, browse movies, halls, showtimes and seat maps |
| `CUSTOMER` | The above, plus book seats and see their own bookings |
| `ADMIN` | Everything, including managing movies, halls and showtimes and opening any booking |

Design decisions:

- **Stateless tokens.** The API signs its own JWTs (HS256) and verifies signature, issuer and expiry on every request. There is no session and no token table. A token carries only the account id and role.
- **The user comes from the token, never from the request body.** A booking request has no user field, so one user cannot book as another.
- **Registration cannot grant a role.** Every registration creates a `CUSTOMER`. The first administrator is created at startup from `ADMIN_EMAIL` and `ADMIN_PASSWORD`; an existing account is never overwritten.
- **Login does not reveal which emails exist.** An unknown email and a wrong password get the same `401 INVALID_CREDENTIALS`, and the password hash comparison runs in both cases so the response time is similar.
- **Someone else's booking is a `404`, not a `403`.** A `403` would confirm that the id exists.
- **Passwords are stored only as BCrypt hashes** and are kept out of logs (`toString` of the request objects masks them).

Not implemented yet: refresh tokens and login rate limiting.

### Request bodies

Movie:

```json
{
  "title": "Interstellar",
  "genre": "Science Fiction",
  "durationMinutes": 169,
  "rating": 8.7,
  "releaseYear": 2014
}
```

Hall. The layout is given as blocks of identical rows, from the screen to the back; rows are numbered across blocks. This example creates rows 1–8 with 12 standard seats and rows 9–10 with 10 VIP seats. A layout cannot be changed after creation, because bookings point at individual seats.

```json
{
  "name": "Hall 1",
  "sections": [
    { "rows": 8, "seatsPerRow": 12, "category": "STANDARD" },
    { "rows": 2, "seatsPerRow": 10, "category": "VIP" }
  ]
}
```

Showtime. Times are ISO-8601 instants; any UTC offset is accepted and responses are in UTC. Every seat category used in the hall needs a price.

```json
{
  "movieId": 1,
  "hallId": 1,
  "startTime": "2030-01-15T18:00:00Z",
  "endTime": "2030-01-15T20:49:00Z",
  "prices": { "STANDARD": 14.50, "VIP": 22.00 }
}
```

Booking. The seat id comes from the showtime's seat map; the user comes from the access token.

```json
{
  "showtimeId": 1,
  "seatId": 101
}
```

The response is the ticket: the movie, hall and start time, where the seat is and what was paid. The price is a copy, so changing the showtime's prices later does not rewrite past bookings:

```json
{
  "id": "f2fc3714-ea45-4679-ba32-bba5019b91ba",
  "showtimeId": 1,
  "movieTitle": "Interstellar",
  "hallName": "Hall 1",
  "startTime": "2030-01-15T18:00:00Z",
  "seatId": 101,
  "row": 9,
  "seatNumber": 5,
  "category": "VIP",
  "price": 22.00,
  "userId": "85df2e82-eb22-4966-8885-8234a8556154",
  "createdAt": "2030-01-10T12:00:00.000Z"
}
```

### Seat map

`GET /showtimes/{id}/seats` returns everything a client needs to draw the hall and let a customer pick a seat (shortened):

```json
{
  "showtimeId": 1,
  "hallId": 1,
  "hallName": "Hall 1",
  "totalSeats": 116,
  "availableSeats": 115,
  "rows": [
    {
      "row": 9,
      "seats": [
        { "seatId": 100, "number": 4, "category": "VIP", "price": 22.00, "status": "AVAILABLE" },
        { "seatId": 101, "number": 5, "category": "VIP", "price": 22.00, "status": "BOOKED" }
      ]
    }
  ]
}
```

### Pagination and sorting

List endpoints take `page` (zero-based), `size` (default 20, capped at 100) and `sort` (`field,asc|desc`).

```text
GET /api/v1/movies?page=0&size=20&sort=releaseYear,desc
```

```json
{
  "content": [{ "id": 1, "title": "Interstellar", "genre": "Science Fiction", "durationMinutes": 169, "rating": 8.7, "releaseYear": 2014 }],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Sorting is limited to an allow-list per resource (movies: `id`, `title`, `genre`, `durationMinutes`, `rating`, `releaseYear`; halls: `id`, `name`, `rowCount`, `seatCount`; showtimes: `id`, `startTime`, `endTime`; bookings: `createdAt`, `price`). Any other field returns `400 INVALID_SORT`.

## Errors

Every error is an `application/problem+json` response ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)) with a stable `code` that clients can branch on:

```json
{
  "title": "Conflict",
  "status": 409,
  "detail": "Row 9 seat 5 is already booked for this showtime",
  "instance": "/api/v1/bookings",
  "code": "SEAT_ALREADY_BOOKED"
}
```

Validation failures list every rejected field:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/movies",
  "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "durationMinutes", "message": "must be greater than 0" },
    { "field": "title", "message": "must not be blank" }
  ]
}
```

| Status | Code | When |
| --- | --- | --- |
| `400` | `VALIDATION_FAILED` | A request field is missing or invalid |
| `400` | `MALFORMED_REQUEST` | The body is missing or is not valid JSON |
| `400` | `INVALID_SORT` | Sorting by a field outside the allow-list |
| `400` | `HALL_TOO_LARGE` | A hall layout over 60 rows or 1000 seats |
| `400` | `SHOWTIME_PRICE_MISSING` | No price for a seat category the hall uses |
| `400` | `SEAT_NOT_IN_HALL` | Booking a seat that is not in the showtime's hall |
| `401` | `UNAUTHENTICATED` | No access token, or one that is invalid or expired (with a `WWW-Authenticate: Bearer` header) |
| `401` | `INVALID_CREDENTIALS` | Login with an unknown email or a wrong password |
| `403` | `FORBIDDEN` | The account's role does not allow the action |
| `404` | `MOVIE_NOT_FOUND`, `HALL_NOT_FOUND`, `SHOWTIME_NOT_FOUND`, `SEAT_NOT_FOUND`, `BOOKING_NOT_FOUND` | The resource does not exist |
| `409` | `EMAIL_TAKEN`, `MOVIE_TITLE_TAKEN`, `HALL_NAME_TAKEN` | The email or name is already in use |
| `409` | `MOVIE_HAS_SHOWTIMES`, `HALL_HAS_SHOWTIMES` | Deleting a movie or hall that still has showtimes |
| `409` | `SHOWTIME_OVERLAP` | The showtime overlaps another one in the same hall |
| `409` | `SHOWTIME_HAS_BOOKINGS` | Deleting a showtime with bookings, or moving it to another hall |
| `409` | `SHOWTIME_ALREADY_STARTED` | Booking a showtime that has already started |
| `409` | `SEAT_ALREADY_BOOKED` | The seat is already booked for this showtime |

Errors raised by the framework (unknown path, unsupported method or media type) use the same format, with the HTTP status name as the code, for example `NOT_FOUND`. Authentication and authorization failures are raised inside Spring Security's filter chain and are routed to the same handler, so they have the same shape too.

## Business rules and where they are enforced

Request validation gives early, precise errors. The database constraint is what guarantees the rule, including when two requests arrive at the same moment.

| Rule | Request / service | Database |
| --- | --- | --- |
| Emails unique, ignoring letter case | `RegisterRequest` lowercases, service check | `uq_users_email`, `CHECK (email = lower(email))` |
| A booking belongs to an existing account | user id taken from the token | `fk_bookings_user` |
| Movie fields valid; titles unique | `MovieRequest`, service check | `CHECK` constraints, `uq_movies_title` |
| Hall names unique; layout within size limits | `HallRequest`, service check | `uq_halls_name` |
| A seat position exists once per hall | — | `uq_seats_position` |
| Showtime ends after it starts; prices not negative | `ShowtimeRequest` | `CHECK` constraints |
| Every seat category of the hall has a price | service check | — |
| Showtimes in one hall do not overlap (back-to-back is allowed) | service query | exclusion constraint `ex_showtimes_hall_time` |
| A seat is booked at most once per showtime | service check | `uq_bookings_showtime_seat` |
| A booked seat belongs to the showtime's hall | service check | composite foreign keys `fk_bookings_showtime`, `fk_bookings_seat` |
| Movies and halls with showtimes, and showtimes with bookings, cannot be deleted | — | foreign keys |

Constraint violations are translated to API errors by constraint name in one place (`ConstraintErrors`).

### Double booking

Checking "is the seat free?" and then inserting is not enough: two simultaneous requests can both see the seat as free. The unique constraint `uq_bookings_showtime_seat` is the real guard. The losing insert fails in the database and the API reports it as `409 SEAT_ALREADY_BOOKED`.

`BookingConcurrencyTest` fires 30 simultaneous requests at one seat and asserts that exactly one gets `201` and one row exists. With the constraint removed, the same test produced 10 bookings for the seat.

### Overlapping showtimes

The same problem exists for scheduling: two managers can pass the overlap check at the same moment. The rule therefore lives in PostgreSQL as an exclusion constraint over the hall and the half-open time range:

```sql
EXCLUDE USING gist (hall_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&)
```

`ShowtimeConcurrencyTest` fires 20 simultaneous requests for the same hall and time and asserts that exactly one showtime is created. With the constraint removed, the same test created 10 overlapping showtimes.

### A seat that is not in the hall

`bookings` repeats the hall id and has two composite foreign keys, `(showtime_id, hall_id)` and `(seat_id, hall_id)`. Together they make a booking for a seat from another hall impossible to store, and they stop a showtime that already has bookings from being moved to another hall.

## Migrations

| Version | What it does |
| --- | --- |
| `V1` | Movies, showtimes, bookings |
| `V2` | Halls, seats, prices per seat category, exclusion constraint for overlaps; bookings point at seats |
| `V3` | Users; bookings reference the account that made them |

`V2` migrates existing data instead of dropping it: each distinct theater name becomes a hall with 15 × 20 seats (the first API accepted seat numbers 1–300), the old showtime price becomes its standard price, and each booking is pointed at its seat and keeps the price it was sold for. `V3` gives the user ids carried by older bookings placeholder accounts that cannot be logged into, so the new foreign key holds. `SchemaMigrationTest` builds a database at `V1`, fills it, migrates to the latest version and checks the result.

## Run locally

### Requirements

- Docker (Docker Desktop on Windows/macOS)
- Java 21, only when running the API outside Docker

### Everything in Docker

From the repository root:

```bash
docker compose up --build
```

The API listens on http://localhost:8080 and Flyway creates the schema on first start. Compose also creates a development administrator, `admin@popcorn.local` with the password `admin-dev-password`; change both in `.env` (see `.env.example`) for anything that is not your own machine.

### API from your IDE

Start only the database, from the repository root:

```bash
docker compose up -d db
```

Then, from `backend/`:

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

### Configuration

| Variable | Purpose | Default |
| --- | --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Database connection | the compose database |
| `JWT_SECRET` | Key that signs access tokens, at least 32 characters | empty: a random key per start, so tokens stop working after a restart |
| `JWT_TTL` | Lifetime of an access token (ISO-8601 duration) | `PT1H` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Administrator created at startup if missing | none outside compose |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | Browser origins allowed to call the API | any `localhost` port |

## Tests

macOS/Linux:

```bash
./mvnw verify
```

Windows:

```powershell
.\mvnw.cmd verify
```

Docker must be running: the API tests start a disposable PostgreSQL container through Testcontainers and call the application over HTTP, so they exercise JSON mapping, validation, transactions, migrations and constraints together. Rules that need no database (text normalization, request validation, sort allow-list) have plain unit tests.

Current suite: **126 tests passing**.
