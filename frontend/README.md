# Popcorn Palace Frontend

A responsive browser interface for managing the Popcorn Palace movie catalog, showtimes, and seat bookings. It connects to the companion [Spring Boot REST API](../backend/README.md) and provides a complete UI for the assignment's core workflows.

## Features

- Browse, add, update, and delete movies
- Browse, create, update, delete, and look up showtimes
- Create seat bookings for available showtimes
- Client-side validation and readable API error states
- Responsive navigation, cards, forms, and showtime table
- Centralized API configuration for local or hosted backends

## Tech stack

- HTML5
- CSS3
- Vanilla JavaScript
- Fetch API
- Spring Boot REST API and PostgreSQL backend

## Run locally

### 1. Start the backend

Follow the setup instructions in [the root README](../README.md). The frontend expects the API at `http://localhost:8080` by default.

### 2. Serve the frontend

From the monorepo root, enter `frontend/` and start a static file server. For example:

```bash
cd frontend
python -m http.server 5500
```

Open [http://localhost:5500](http://localhost:5500).

Do not open the HTML files directly with `file://`; serving them over HTTP gives the browser a consistent origin for API requests.

## API configuration

The backend URL is defined once in [`js/config.js`](js/config.js):

```js
window.POPCORN_CONFIG = Object.freeze({
  apiUrl: new URLSearchParams(window.location.search).get("apiUrl") || "http://localhost:8080",
});
```

Change the fallback URL when connecting the frontend to a hosted API. For temporary local testing, you can also pass an override such as `?apiUrl=http://127.0.0.1:8081` without editing the source.

## Main API routes

| Workflow | Method | Endpoint |
| --- | --- | --- |
| List movies | `GET` | `/movies/all` |
| Add movie | `POST` | `/movies` |
| Update movie | `POST` | `/movies/update/{title}` |
| Delete movie | `DELETE` | `/movies/{title}` |
| List showtimes | `GET` | `/showtimes/all` |
| Get showtime | `GET` | `/showtimes/{id}` |
| Add showtime | `POST` | `/showtimes` |
| Update showtime | `POST` | `/showtimes/update/{id}` |
| Delete showtime | `DELETE` | `/showtimes/{id}` |
| Create booking | `POST` | `/bookings` |

## Project structure

```text
.
├── index.html
├── movie.html
├── showtime.html
├── booking.html
├── css/
├── img/
└── js/
    ├── config.js
    ├── movie.js
    ├── showtime.js
    └── booking.js
```
