const API_URL = window.POPCORN_CONFIG.apiUrl;

let showtimes = [];

document.addEventListener("DOMContentLoaded", async () => {
  document.getElementById("booking-form").addEventListener("submit", submitBooking);
  await loadShowtimesToSelect();
});

async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_URL}${path}`, options);

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const error = await response.json();
      message = error.message || error.error || message;
    } catch {
      // The API may return an empty response body.
    }
    throw new Error(message);
  }

  return response;
}

async function loadShowtimesToSelect() {
  const select = document.getElementById("add-showtimeId");
  select.innerHTML = '<option value="">Loading showtimes...</option>';
  select.disabled = true;

  try {
    const response = await apiRequest("/showtimes/all");
    showtimes = await response.json();
    select.innerHTML = '<option value="">Select Showtime</option>';

    showtimes.forEach((showtime) => {
      const option = document.createElement("option");
      option.value = showtime.id;
      option.textContent = `#${showtime.id} · ${showtime.theater} · ${formatDate(showtime.startTime)}`;
      select.append(option);
    });

    if (showtimes.length === 0) {
      select.innerHTML = '<option value="">No showtimes available</option>';
    }
  } catch (error) {
    select.innerHTML = '<option value="">Unable to load showtimes</option>';
    setResult(`Unable to load showtimes: ${error.message}`, true);
  } finally {
    select.disabled = showtimes.length === 0;
  }
}

async function submitBooking(event) {
  event.preventDefault();
  const form = event.currentTarget;

  const showtimeId = Number(document.getElementById("add-showtimeId").value);
  const seatNumber = Number(document.getElementById("add-seatNumber").value);
  const userId = document.getElementById("add-userId").value.trim();
  const selectedShowtime = showtimes.find((showtime) => showtime.id === showtimeId);

  if (!selectedShowtime) {
    setResult("Select an available showtime.", true);
    return;
  }

  try {
    const response = await apiRequest("/bookings", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        showtime: { id: selectedShowtime.id },
        seatNumber,
        userId,
      }),
    });

    const result = await response.json();
    setResult(`Booking successful. Booking ID: ${result.bookingId}`);
    form.reset();
  } catch (error) {
    setResult(`Booking failed: ${error.message}`, true);
  }
}

function formatDate(value) {
  return new Date(value).toLocaleString("en-GB", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function setResult(message, isError = false) {
  const result = document.getElementById("booking-result");
  result.textContent = message;
  result.classList.toggle("error", isError);
}
