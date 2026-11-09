# Popcorn Palace

Movie ticket booking application in a single repository.

- `backend/` — Java 21, Spring Boot, Spring Data JPA and PostgreSQL API.
- `frontend/` — existing HTML, CSS and JavaScript client; starting point for the planned Next.js + TypeScript migration.
- `compose.yml` — local PostgreSQL database.

## Run locally

Requirements: Java 21, Docker Compose, and Python 3 (or another static HTTP server).

Start PostgreSQL from the repository root:

```sh
docker compose up -d
```

Start the API in another terminal:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

On macOS/Linux, use `./mvnw spring-boot:run` from `backend/`.
The API listens on http://localhost:8080.

Serve the frontend from the repository root:

```sh
python -m http.server 5500 --directory frontend
```

Open http://localhost:5500. The client defaults to http://localhost:8080;
see [frontend configuration](frontend/README.md#api-configuration) for overrides.

## Tests

```powershell
cd backend
.\mvnw.cmd test
```

On macOS/Linux, use `./mvnw test` from `backend/`.
Tests use H2 and do not require a running PostgreSQL instance.

## Component documentation

- [Backend API and validation](backend/README.md)
- [Frontend workflows and configuration](frontend/README.md)

## Repository history

This repository combines `antoniosifov/popcorn-palace` and
`antoniosifov/popcorn-palace-frontend`. Both original commit histories are
preserved through the frontend import merge. To inspect older component history,
use `git log --follow -- backend/<file>` or inspect the import merge's second
parent for the original frontend paths.
