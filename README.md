# Popcorn Palace

[![CI](https://github.com/antoniosifov/popcorn-palace/actions/workflows/ci.yml/badge.svg)](https://github.com/antoniosifov/popcorn-palace/actions/workflows/ci.yml)

Movie ticket booking application in a single repository.

- `backend/` — the booking API: Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Redis, Kafka.
- `notifications/` — a second service that reads booking events from Kafka and emails tickets.
- `frontend/` — legacy static client for the first API version; being replaced by a Next.js + TypeScript client.
- `compose.yml` — local stack: PostgreSQL, Redis, Kafka, Mailpit (catches the emails), the API and the notifications service.

## Run locally

Requirement: Docker (Docker Desktop on Windows/macOS).

Start everything from the repository root:

```sh
docker compose up --build
```

The API listens on http://localhost:8080. Open http://localhost:8080/swagger-ui.html
to browse and call every endpoint from the browser. Emails sent by the stack are
caught by Mailpit at http://localhost:8025; nothing reaches real mailboxes.

Browsing is public. To manage the catalog, log in at `POST /api/v1/auth/login` as the
development administrator (`admin@popcorn.local` / `admin-dev-password`), press
**Authorize** in Swagger UI and paste the `accessToken`. To book seats, register a
customer at `POST /api/v1/auth/register`, hold seats from a showtime's seat map and
pay for the hold with the sandbox token `tok_visa` (the request needs an
`Idempotency-Key` header with any unique value). The ticket email appears in Mailpit
a moment later.

To watch the seat map change live, open a showtime's event stream in a terminal and
hold or book seats from Swagger UI:

```sh
curl -N http://localhost:8080/api/v1/showtimes/1/seats/stream
```

If one of the ports (8080, 5432, 6379, 29092, 8025, 1025) is taken, copy `.env.example` to `.env` and change it.

### Run the services from your IDE instead

Requirements: Java 21 and Docker.

Start only the infrastructure:

```sh
docker compose up -d db redis kafka mailpit
```

Then start the API (and, the same way from `notifications/`, the notifications service):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

On macOS/Linux, use `./mvnw spring-boot:run` from `backend/`.

Connection settings come from the `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`
environment variables and default to the local compose database.

### Frontend

The static pages in `frontend/` target the first version of the API and do not
work with the current `/api/v1` backend. Until the new client lands, use
Swagger UI.

## Tests

```powershell
cd backend
.\mvnw.cmd verify
```

On macOS/Linux, use `./mvnw verify` from `backend/`; the same command in `notifications/`
runs that service's tests. Tests start disposable PostgreSQL, Redis and Kafka containers
through Testcontainers, so Docker must be running. The same commands run in CI on every
pull request.

## Component documentation

- [Backend API, errors and business rules](backend/README.md)
- [Notifications service](notifications/README.md)
- [Legacy static frontend](frontend/README.md)

## Repository history

This repository combines `antoniosifov/popcorn-palace` and
`antoniosifov/popcorn-palace-frontend`. Both original commit histories are
preserved through the frontend import merge. To inspect older component history,
use `git log --follow -- backend/<file>` or inspect the import merge's second
parent for the original frontend paths.
