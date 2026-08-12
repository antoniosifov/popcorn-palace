window.POPCORN_CONFIG = Object.freeze({
  apiUrl: new URLSearchParams(window.location.search).get("apiUrl") || "http://localhost:8080",
});
