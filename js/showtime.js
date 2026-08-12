const API_URL = window.POPCORN_CONFIG.apiUrl;

let movies = [];
let showtimes = [];

document.addEventListener("DOMContentLoaded", async () => {
  document.getElementById("get-showtime-button").addEventListener("click", getShowtimeById);
  document.getElementById("add-form").addEventListener("submit", addShowtime);

  await loadMoviesToSelect();
  await loadShowtimes();
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

async function loadMoviesToSelect() {
  const select = document.getElementById("add-movieId");
  select.innerHTML = '<option value="">Select Movie</option>';

  try {
    const response = await apiRequest("/movies/all");
    movies = await response.json();

    movies.forEach((movie) => {
      const option = document.createElement("option");
      option.value = movie.id;
      option.textContent = `${movie.title} (${movie.releaseYear})`;
      select.append(option);
    });
  } catch (error) {
    showToast(`Unable to load movies: ${error.message}`, true);
  }
}

async function loadShowtimes() {
  const tableBody = document.getElementById("showtime-table-body");
  tableBody.replaceChildren(createStatusRow("Loading showtimes..."));

  try {
    const response = await apiRequest("/showtimes/all");
    showtimes = await response.json();

    tableBody.replaceChildren();
    if (showtimes.length === 0) {
      tableBody.append(createStatusRow("No showtimes have been added yet."));
      return;
    }

    showtimes.forEach((showtime) => tableBody.append(createShowtimeRow(showtime)));
  } catch (error) {
    tableBody.replaceChildren(createStatusRow(`Unable to load showtimes: ${error.message}`, true));
  }
}

function createShowtimeRow(showtime) {
  const row = document.createElement("tr");
  row.className = "fade-in";

  const values = [
    showtime.id,
    `${showtime.movieId} (${getMovieTitleById(showtime.movieId)})`,
    showtime.theater,
    `$${Number(showtime.price).toFixed(2)}`,
    formatDateTime(showtime.startTime),
    formatDateTime(showtime.endTime),
  ];

  values.forEach((value) => {
    const cell = document.createElement("td");
    cell.textContent = value;
    row.append(cell);
  });

  const actions = document.createElement("td");
  const updateButton = document.createElement("button");
  updateButton.type = "button";
  updateButton.className = "update-btn";
  updateButton.textContent = "Update";
  updateButton.addEventListener("click", () => showUpdateForm(showtime.id, row));

  const deleteButton = document.createElement("button");
  deleteButton.type = "button";
  deleteButton.className = "delete-btn";
  deleteButton.textContent = "Delete";
  deleteButton.addEventListener("click", () => deleteShowtime(showtime.id));

  actions.append(updateButton, deleteButton);
  row.append(actions);
  return row;
}

function createStatusRow(message, isError = false) {
  const row = document.createElement("tr");
  const cell = document.createElement("td");
  cell.colSpan = 7;
  cell.className = isError ? "table-status error" : "table-status";
  cell.textContent = message;
  row.append(cell);
  return row;
}

async function getShowtimeById() {
  const id = document.getElementById("get-id-input").value;
  const result = document.getElementById("showtime-by-id-result");

  if (!id) {
    result.textContent = "Enter a showtime ID.";
    return;
  }

  try {
    const response = await apiRequest(`/showtimes/${id}`);
    const showtime = await response.json();
    result.textContent = [
      `ID: ${showtime.id}`,
      `Movie: ${getMovieTitleById(showtime.movieId)} (#${showtime.movieId})`,
      `Theater: ${showtime.theater}`,
      `Price: $${Number(showtime.price).toFixed(2)}`,
      `Starts: ${formatDateTime(showtime.startTime)}`,
      `Ends: ${formatDateTime(showtime.endTime)}`,
    ].join("\n");
  } catch (error) {
    result.textContent = `Unable to find showtime: ${error.message}`;
  }
}

async function addShowtime(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const movieId = Number(document.getElementById("add-movieId").value);

  try {
    await apiRequest("/showtimes", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(readShowtimeForm(movieId)),
    });
    form.reset();
    showToast("Showtime added successfully.");
    await loadShowtimes();
  } catch (error) {
    showToast(`Unable to add showtime: ${error.message}`, true);
  }
}

function readShowtimeForm(movieId, id = "add") {
  return {
    movie: { id: movieId },
    theater: document.getElementById(`${id}-theater`).value.trim(),
    price: Number(document.getElementById(`${id}-price`).value),
    start_time: document.getElementById(`${id}-startTime`).value,
    end_time: document.getElementById(`${id}-endTime`).value,
  };
}

function showUpdateForm(id, row) {
  document.querySelector(".update-row")?.remove();
  const showtime = showtimes.find((item) => item.id === id);
  if (!showtime) return;

  const formRow = document.createElement("tr");
  formRow.className = "update-row";
  const cell = document.createElement("td");
  cell.colSpan = 7;

  const form = document.createElement("form");
  form.className = "inline-form";

  const fields = [
    ["theater", "text", showtime.theater],
    ["price", "number", showtime.price],
    ["startTime", "datetime-local", showtime.startTime.slice(0, 16)],
    ["endTime", "datetime-local", showtime.endTime.slice(0, 16)],
  ];

  fields.forEach(([name, type, value]) => {
    const input = document.createElement("input");
    input.id = `update-${name}`;
    input.type = type;
    input.value = value;
    input.placeholder = name;
    input.required = true;
    if (name === "price") input.step = "0.01";
    form.append(input);
  });

  const saveButton = document.createElement("button");
  saveButton.type = "submit";
  saveButton.textContent = "Save";

  const cancelButton = document.createElement("button");
  cancelButton.type = "button";
  cancelButton.textContent = "Cancel";
  cancelButton.addEventListener("click", () => formRow.remove());

  form.append(saveButton, cancelButton);
  form.addEventListener("submit", (event) => submitUpdate(event, showtime));
  cell.append(form);
  formRow.append(cell);
  row.after(formRow);
}

async function submitUpdate(event, showtime) {
  event.preventDefault();

  const updatedShowtime = {
    movie: { id: showtime.movieId },
    theater: document.getElementById("update-theater").value.trim(),
    price: Number(document.getElementById("update-price").value),
    start_time: document.getElementById("update-startTime").value,
    end_time: document.getElementById("update-endTime").value,
  };

  try {
    await apiRequest(`/showtimes/update/${showtime.id}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(updatedShowtime),
    });
    showToast("Showtime updated successfully.");
    await loadShowtimes();
  } catch (error) {
    showToast(`Unable to update showtime: ${error.message}`, true);
  }
}

async function deleteShowtime(id) {
  if (!window.confirm(`Delete showtime #${id}?`)) return;

  try {
    await apiRequest(`/showtimes/${id}`, { method: "DELETE" });
    showToast(`Showtime #${id} deleted.`);
    await loadShowtimes();
  } catch (error) {
    showToast(`Unable to delete showtime: ${error.message}`, true);
  }
}

function getMovieTitleById(id) {
  return movies.find((movie) => movie.id === id)?.title || "Unknown movie";
}

function formatDateTime(value) {
  return new Date(value).toLocaleString("en-GB", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function showToast(message, isError = false) {
  const toast = document.getElementById("toast");
  toast.textContent = message;
  toast.classList.toggle("error", isError);
  toast.classList.add("show");
  window.setTimeout(() => toast.classList.remove("show"), 3000);
}
