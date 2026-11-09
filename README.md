# Popcorn Palace

[![CI](https://github.com/antoniosifov/popcorn-palace/actions/workflows/ci.yml/badge.svg)](https://github.com/antoniosifov/popcorn-palace/actions/workflows/ci.yml)

Movie ticket booking application in a single repository.

- `backend/` — Java 21, Spring Boot 4, Spring Data JPA and PostgreSQL API.
- `frontend/` — existing HTML, CSS and JavaScript client; starting point for the planned Next.js + TypeScript migration.
- `compose.yml` — local stack: PostgreSQL and the API.

## Run locally

Requirement: Docker (Docker Desktop on Windows/macOS).

Start the database and the API from the repository root:

```sh
docker compose up --build
```

The API listens on http://localhost:8080.

### Run the API from your IDE instead

Requirements: Java 21 and Docker.

Start only the database:

```sh
docker compose up -d db
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

Serve the frontend from the repository root (Python 3 or any static HTTP server):

```sh
python -m http.server 5500 --directory frontend
```

Open http://localhost:5500. The client defaults to http://localhost:8080;
see [frontend configuration](frontend/README.md#api-configuration) for overrides.

## Tests

```powershell
cd backend
.\mvnw.cmd verify
```

On macOS/Linux, use `./mvnw verify` from `backend/`.
Tests start a disposable PostgreSQL container through Testcontainers, so Docker
must be running. The same command runs in CI on every pull request.

## Component documentation

- [Backend API and validation](backend/README.md)
- [Frontend workflows and configuration](frontend/README.md)

## Repository history

This repository combines `antoniosifov/popcorn-palace` and
`antoniosifov/popcorn-palace-frontend`. Both original commit histories are
preserved through the frontend import merge. To inspect older component history,
use `git log --follow -- backend/<file>` or inspect the import merge's second
parent for the original frontend paths.
