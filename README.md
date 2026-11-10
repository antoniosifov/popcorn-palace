# Popcorn Palace

[![CI](https://github.com/antoniosifov/popcorn-palace/actions/workflows/ci.yml/badge.svg)](https://github.com/antoniosifov/popcorn-palace/actions/workflows/ci.yml)

A cinema's ticketing product: pick a movie, choose seats on a seat map that changes as other people book, pay, and get the tickets by email. A web client, a booking API and a notifications service, with PostgreSQL, Redis and Kafka behind them, started by one command.

![Two browsers on the same showtime: seats one customer holds and then buys change on the other customer's screen without a reload](docs/screenshots/live-seat-map.gif)

It is built to keep its promises when requests race each other: a seat is sold once, a customer is charged once, and a ticket email is sent if and only if the booking exists.

## What it looks like

| The programme | Choosing seats |
| --- | --- |
| ![The programme for one day: movies with their showtimes, halls and prices](docs/screenshots/programme.png) | ![The seat map of a showtime with sold seats, seats being booked by others and two picked seats](docs/screenshots/seat-map.png) |
| **Paying, here with a declined card** | **The ticket** |
| ![The held seats with a countdown, the test cards, and a declined payment with its reference](docs/screenshots/checkout.png) | ![A confirmed booking: the seats, the prices, the payment and the option to cancel](docs/screenshots/ticket.png) |

<p align="center">
  <img src="docs/screenshots/phone.png" width="560" alt="The programme and the seat map on a phone">
</p>

The cinema has a back office too. A hall is designed as blocks of rows and drawn while it is typed:

![The form for a new hall, with a preview of its seats](docs/screenshots/back-office.png)

## Run it

Requirement: Docker (Docker Desktop on Windows/macOS).

```sh
docker compose up --build
```

Then open http://localhost:3000.

The stack starts with a demo programme (three halls, eight movies, a week of showtimes that rolls forward day by day), so there is something to book right away:

1. Create an account, pick a showtime and choose seats. Open the same showtime in a second window to watch the seats change there as you hold and buy them.
2. Pay with one of the test cards. The payment provider is a sandbox: no real money moves, no card details are asked for, and each test card shows one way a payment can go.
3. Read the ticket email in Mailpit at http://localhost:8025. Nothing reaches real mailboxes.

To manage movies, halls and showtimes, log in as the development administrator (`admin@popcorn.local` / `admin-dev-password`) and open **Admin**.

| What | Where |
| --- | --- |
| The web client | http://localhost:3000 |
| The API, with interactive documentation | http://localhost:8080/swagger-ui.html |
| Emails the stack sent | http://localhost:8025 |
| Dashboard (Grafana; viewing needs no login) | http://localhost:3001 |
| Traces (Jaeger) | http://localhost:16686 |
| Metrics (Prometheus) | http://localhost:9090 |

If one of the ports (3000, 8080, 5432, 6379, 29092, 8025, 1025, 3001, 16686, 9090) is taken, copy `.env.example` to `.env` and change it. The same file sets the cinema's time zone (`CINEMA_TIME_ZONE`) and switches the demo programme off (`DEMO_DATA=false`).

## How it is built

```mermaid
flowchart LR
    browser([Browser])
    web["Web client<br/>Next.js, TypeScript"]
    api["Booking API<br/>Java 21, Spring Boot 4"]
    notifications["Notifications service<br/>Spring Boot"]
    postgres[("PostgreSQL<br/>catalog, bookings, payments, outbox")]
    redis[("Redis<br/>seat holds, live changes, rate limits")]
    kafka[("Kafka<br/>booking events")]
    mail["Mailpit<br/>catches the emails"]

    browser -- "pages, actions, live seat stream" --> web
    web -- "REST with the customer's token" --> api
    api --> postgres
    api --> redis
    api -- "transactional outbox" --> kafka
    kafka --> notifications
    notifications --> mail
    notifications -. "which events were handled" .-> redis
```

A booking from the click to the email:

1. **Hold.** The customer picks seats and the API reserves them in Redis for ten minutes with one atomic script: all of the seats or none. Every browser watching that showtime sees them become "being booked".
2. **Pay.** The payment is recorded before the provider is called, under an idempotency key, so a retry is answered from the record instead of charging again.
3. **Book.** The booking, the payment's success and an event in the outbox table are saved in one database transaction. A unique index on the seats of a showtime is the last word on who got them.
4. **Tell.** A relay sends the outbox to Kafka. The notifications service turns the event into a ticket email and remembers which events it has handled, because Kafka may deliver one twice.

## Engineering worth a look

The parts that took thought, and where each is explained and tested.

| Problem | What was done | Read more |
| --- | --- | --- |
| Two people want the same seat | Holds in Redis taken by an atomic Lua script, bookings guarded by a partial unique index in PostgreSQL. A test has 50 customers grab the same seats at once and exactly one of them gets them | [Checkout](backend/README.md#checkout-holding-seats-then-booking-them) |
| A payment is retried, or the answer is lost | Idempotency keys, the payment recorded before the charge, a refund when the booking cannot be saved, and a job that settles what was left unfinished | [Paying](backend/README.md#paying-charging-exactly-once) |
| The email must match the database | A transactional outbox: the event is written in the booking's transaction and published afterwards, at least once; the consumer drops duplicates | [Events](backend/README.md#events-the-transactional-outbox) |
| A seat map that is live on several instances | Changes fan out through Redis pub/sub to server-sent event streams; an expired hold is announced by Redis itself | [Live seat map](backend/README.md#live-seat-map-1) |
| The browser misses what happened before it subscribed | Subscribe first, then load the map again; one reducer handles events that overtake answers | [Seat map and checkout](frontend/README.md#the-live-seat-map-and-the-checkout) |
| Overlapping showtimes in one hall | An exclusion constraint in PostgreSQL, so the rule holds under concurrency, with a friendly check in front of it | [Overlapping showtimes](backend/README.md#overlapping-showtimes) |
| Keeping the token away from scripts | The browser talks to the web client only; the token lives in an `HttpOnly` cookie and every call to the API is made by the server | [How it fits together](frontend/README.md#how-it-fits-together) |
| One request across two services and a queue | A trace that survives the outbox: the row stores the request's trace context and the relay continues it | [Traces](backend/README.md#traces) |
| A cinema has one clock | Showtimes are stored as instants and shown and entered on the cinema's clock, including the nights the clocks change | [Time](frontend/README.md#time) |
| Rules that hold when the code is wrong | Constraints in the database, not only checks in the services, and Flyway migrations that own the schema | [Business rules](backend/README.md#business-rules-and-where-they-are-enforced) |

## Watching it work

Both services report health, metrics and traces. The dashboard counts the business as well as the runtime: holds, bookings, payments by outcome, the outbox backlog, open seat maps, emails.

![The Grafana dashboard: bookings, revenue, declined payments, requests, latency, holds and bookings per minute](docs/screenshots/dashboard.png)

A paid booking is one trace: the request, its Redis calls, the outbox publish, the Kafka message and the notifications service that sent the email.

![A Jaeger trace of a paid booking crossing the API, Kafka and the notifications service](docs/screenshots/trace.png)

Every API response carries the trace id in its `X-Trace-Id` header, and the web client shows it as the reference of an error, so a customer's complaint leads straight to the request.

## Technology

| Layer | What |
| --- | --- |
| Web client | Next.js 16 (App Router, Server Components and Server Actions), React 19, TypeScript, Tailwind CSS 4 |
| API | Java 21, Spring Boot 4, Spring Security (JWT), Spring Data JPA, Flyway, springdoc OpenAPI |
| Data | PostgreSQL 17, Redis 8, Kafka 4 (KRaft) |
| Observability | Micrometer, OpenTelemetry, Prometheus, Grafana, Jaeger |
| Tests | JUnit, Testcontainers, Vitest, Testing Library, Playwright |
| Delivery | Docker, Docker Compose, GitHub Actions |

## Tests

| Suite | Count | What it runs against |
| --- | --- | --- |
| API | 215 | The application over HTTP with real PostgreSQL, Redis and Kafka (Testcontainers) |
| Notifications service | 8 | Real Kafka and Redis, an in-memory mail server |
| Web client, unit and component | 88 | The rules of the seat map, the checkout, time zones and errors, without a browser |
| End to end | 12 | A real browser against the whole stack: a booking has to arrive as an email |

All of it runs in CI on every pull request. The end-to-end job starts the stack with `docker compose`, the same way the section above does.

```sh
cd backend && ./mvnw verify          # the same in notifications/
cd frontend && npm test              # unit and component tests
cd frontend && npm run e2e           # needs the Docker stack running
```

On Windows use `.\mvnw.cmd verify`. The Java tests need Docker running.

## Run the services from your IDE instead

Requirements: Java 21, Node.js 22 and Docker.

Start only the infrastructure:

```sh
docker compose up -d db redis kafka mailpit jaeger
```

Then start the API (and, the same way from `notifications/`, the notifications service):

```sh
cd backend
./mvnw spring-boot:run
```

Set `DEMO_DATA=true` to get the demo programme here too. And the web client:

```sh
cd frontend
npm install
npm run dev
```

The API finds the database through `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`, which default to the compose database; the web client finds the API through `API_URL`, which defaults to http://localhost:8080.

## What it is not

- **Payments are a sandbox.** A token stands in for a card. A real provider would replace one class; the idempotency, refund and reconciliation logic around it is real.
- **One cinema.** There is one programme and one time zone, not a chain.
- **No refresh tokens.** A session lasts as long as its access token, one hour.
- **No artwork.** The API stores no posters, so the client draws a title card for each movie.
- **Not deployed yet.** It runs locally with one command; a public demo is the next step.

## Repository

```text
frontend/        the web client
backend/         the booking API
notifications/   the service that emails tickets
observability/   Prometheus configuration and the Grafana dashboard
compose.yml      the whole stack
docs/            the pictures on this page
```

- [Web client](frontend/README.md): how the browser, the client and the API fit together, the seat map, time, rendering, tests
- [Booking API](backend/README.md): the API, errors, business rules and where they are enforced, events, observability
- [Notifications service](notifications/README.md)

This repository combines `antoniosifov/popcorn-palace` and `antoniosifov/popcorn-palace-frontend`; both histories are preserved through the import merge. The static pages that `frontend/` held before the Next.js client are in that history as well.

Built by Anton Iosifov: [portfolio](https://antoniosifov.com) · [LinkedIn](https://www.linkedin.com/in/anton-iosifov/)
