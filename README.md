# Popcorn Palace

[![CI](https://github.com/antoniosifov/popcorn-palace/actions/workflows/ci.yml/badge.svg)](https://github.com/antoniosifov/popcorn-palace/actions/workflows/ci.yml)

Movie ticket booking: pick a movie, choose seats on a live seat map, pay, get the tickets by email. One repository, three services.

- `frontend/` — the web client: Next.js 16 and TypeScript. The programme, the live seat map and checkout, bookings, and a back office for the cinema.
- `backend/` — the booking API: Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Redis, Kafka.
- `notifications/` — a second service that reads booking events from Kafka and emails tickets.
- `compose.yml` — the local stack: all three, PostgreSQL, Redis, Kafka, Mailpit (catches the emails), plus Prometheus, Jaeger and Grafana to watch them.
- `observability/` — Prometheus scrape config and the Grafana dashboard, loaded by compose.

## Run locally

Requirement: Docker (Docker Desktop on Windows/macOS).

Start everything from the repository root:

```sh
docker compose up --build
```

Then open http://localhost:3000.

The stack starts with a demo programme (three halls, eight movies, a week of showtimes that
rolls forward day by day), so there is something to book right away:

1. Create an account, pick a showtime and choose seats. Open the same showtime in a second
   window to watch the seats change there as you hold and buy them.
2. Pay with one of the test cards. The payment provider is a sandbox: no real money moves, no
   card details are asked for, and each test card shows one way a payment can go.
3. Read the ticket email in Mailpit at http://localhost:8025. Nothing reaches real mailboxes.

To manage movies, halls and showtimes, log in as the development administrator
(`admin@popcorn.local` / `admin-dev-password`) and open **Admin**.

| What | Where |
| --- | --- |
| The web client | http://localhost:3000 |
| The API, with interactive documentation | http://localhost:8080/swagger-ui.html |
| Emails the stack sent | http://localhost:8025 |
| Dashboard (Grafana; viewing needs no login) | http://localhost:3001 |
| Traces (Jaeger) | http://localhost:16686 |
| Metrics (Prometheus) | http://localhost:9090 |

To watch a booking travel through the stack, open Jaeger and search the `popcorn-palace`
service: the request that paid, the Kafka message and the notifications service's email are
one trace. Every API response carries that trace id in its `X-Trace-Id` header, and the web
client shows it as the reference of an error.

If one of the ports (3000, 8080, 5432, 6379, 29092, 8025, 1025, 3001, 16686, 9090) is taken,
copy `.env.example` to `.env` and change it. The same file sets the cinema's time zone
(`CINEMA_TIME_ZONE`) and switches the demo programme off (`DEMO_DATA=false`).

### Run the services from your IDE instead

Requirements: Java 21, Node.js 22 and Docker.

Start only the infrastructure:

```sh
docker compose up -d db redis kafka mailpit jaeger
```

Then start the API (and, the same way from `notifications/`, the notifications service):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

On macOS/Linux, use `./mvnw spring-boot:run` from `backend/`. Set `DEMO_DATA=true` to get the
demo programme here too.

And the web client:

```sh
cd frontend
npm install
npm run dev
```

Connection settings of the API come from the `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`
environment variables and default to the local compose database; the web client finds the
API through `API_URL`, which defaults to http://localhost:8080.

## Tests

```powershell
cd backend
.\mvnw.cmd verify
```

On macOS/Linux, use `./mvnw verify` from `backend/`; the same command in `notifications/`
runs that service's tests. Tests start disposable PostgreSQL, Redis and Kafka containers
through Testcontainers, so Docker must be running.

```sh
cd frontend
npm test        # unit and component tests
npm run e2e     # a real browser against the running Docker stack
```

All of it runs in CI on every pull request, including the end-to-end tests: the whole stack
is started with `docker compose` and a booking made in a browser has to arrive as an email.

## Component documentation

- [Web client](frontend/README.md)
- [Backend API, errors and business rules](backend/README.md)
- [Notifications service](notifications/README.md)

## Repository history

This repository combines `antoniosifov/popcorn-palace` and
`antoniosifov/popcorn-palace-frontend`. Both original commit histories are
preserved through the frontend import merge. To inspect older component history,
use `git log --follow -- backend/<file>` or inspect the import merge's second
parent for the original frontend paths. The static pages that `frontend/` held
before the Next.js client are in that history as well.
