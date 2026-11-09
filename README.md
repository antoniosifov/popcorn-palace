# Popcorn Palace

[![CI](https://github.com/antoniosifov/popcorn-palace/actions/workflows/ci.yml/badge.svg)](https://github.com/antoniosifov/popcorn-palace/actions/workflows/ci.yml)

Movie ticket booking application in a single repository.

- `backend/` — Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Flyway, PostgreSQL and Redis API.
- `frontend/` — legacy static client for the first API version; being replaced by a Next.js + TypeScript client.
- `compose.yml` — local stack: PostgreSQL, Redis and the API.

## Run locally

Requirement: Docker (Docker Desktop on Windows/macOS).

Start the database, Redis and the API from the repository root:

```sh
docker compose up --build
```

The API listens on http://localhost:8080. Open http://localhost:8080/swagger-ui.html
to browse and call every endpoint from the browser.

Browsing is public. To manage the catalog, log in at `POST /api/v1/auth/login` as the
development administrator (`admin@popcorn.local` / `admin-dev-password`), press
**Authorize** in Swagger UI and paste the `accessToken`. To book seats, register a
customer at `POST /api/v1/auth/register`, hold seats from a showtime's seat map and
confirm the hold.

If port 8080, 5432 or 6379 is taken, copy `.env.example` to `.env` and change the ports.

### Run the API from your IDE instead

Requirements: Java 21 and Docker.

Start only the database and Redis:

```sh
docker compose up -d db redis
```

Then start the API:

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

On macOS/Linux, use `./mvnw verify` from `backend/`.
Tests start disposable PostgreSQL and Redis containers through Testcontainers, so
Docker must be running. The same command runs in CI on every pull request.

## Component documentation

- [Backend API, errors and business rules](backend/README.md)
- [Legacy static frontend](frontend/README.md)

## Repository history

This repository combines `antoniosifov/popcorn-palace` and
`antoniosifov/popcorn-palace-frontend`. Both original commit histories are
preserved through the frontend import merge. To inspect older component history,
use `git log --follow -- backend/<file>` or inspect the import merge's second
parent for the original frontend paths.
