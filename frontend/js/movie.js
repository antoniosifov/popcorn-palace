const API_URL = window.POPCORN_CONFIG.apiUrl;

document.addEventListener("DOMContentLoaded", () => {
  const addMovieButton = document.getElementById("add-movie-button");
  const addMovieForm = document.getElementById("add-movie-form");
  const cancelAddButton = document.getElementById("cancel-add-movie");

  addMovieButton.addEventListener("click", () => {
    addMovieForm.hidden = false;
  });

  cancelAddButton.addEventListener("click", () => {
    addMovieForm.hidden = true;
    addMovieForm.reset();
  });

  addMovieForm.addEventListener("submit", addMovie);
  loadMovies();
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

async function loadMovies() {
  const movieList = document.getElementById("movie-list");
  movieList.replaceChildren(createStatus("Loading movies..."));

  try {
    const response = await apiRequest("/movies/all");
    const movies = await response.json();

    movieList.replaceChildren();
    if (movies.length === 0) {
      movieList.append(createStatus("No movies have been added yet."));
      return;
    }

    movies.forEach((movie) => movieList.append(createMovieCard(movie)));
  } catch (error) {
    movieList.replaceChildren(createStatus(`Unable to load movies: ${error.message}`, true));
  }
}

function createMovieCard(movie) {
  const card = document.createElement("article");
  card.className = "movie-card";

  const image = document.createElement("img");
  image.src = "img/photo7.webp";
  image.alt = `${movie.title} poster placeholder`;
  image.className = "movie-image";

  const title = document.createElement("h2");
  title.textContent = movie.title;

  card.append(
    image,
    title,
    createDetail("Genre", movie.genre),
    createDetail("Duration", `${movie.duration} min`),
    createDetail("Rating", movie.rating),
    createDetail("Release year", movie.releaseYear),
  );

  const actions = document.createElement("div");
  actions.className = "movie-actions";

  const updateButton = document.createElement("button");
  updateButton.type = "button";
  updateButton.className = "update-button";
  updateButton.textContent = "Update";

  const deleteButton = document.createElement("button");
  deleteButton.type = "button";
  deleteButton.className = "delete-button";
  deleteButton.textContent = "Delete";
  deleteButton.addEventListener("click", () => deleteMovie(movie.title));

  actions.append(updateButton, deleteButton);
  card.append(actions);

  const updateForm = createUpdateForm(movie);
  updateButton.addEventListener("click", () => {
    updateForm.hidden = !updateForm.hidden;
  });
  card.append(updateForm);

  return card;
}

function createDetail(label, value) {
  const paragraph = document.createElement("p");
  const strong = document.createElement("strong");
  strong.textContent = `${label}: `;
  paragraph.append(strong, document.createTextNode(String(value)));
  return paragraph;
}

function createUpdateForm(movie) {
  const form = document.createElement("form");
  form.className = "update-form";
  form.hidden = true;

  const fields = [
    ["title", "text", movie.title],
    ["genre", "text", movie.genre],
    ["duration", "number", movie.duration],
    ["rating", "number", movie.rating, "0.1"],
    ["releaseYear", "number", movie.releaseYear],
  ];

  fields.forEach(([name, type, value, step]) => {
    const input = document.createElement("input");
    input.name = name;
    input.type = type;
    input.value = value;
    input.placeholder = name;
    input.required = true;
    if (step) input.step = step;
    form.append(input);
  });

  const saveButton = document.createElement("button");
  saveButton.type = "submit";
  saveButton.textContent = "Save";

  const cancelButton = document.createElement("button");
  cancelButton.type = "button";
  cancelButton.className = "cancel-button";
  cancelButton.textContent = "Cancel";
  cancelButton.addEventListener("click", () => {
    form.hidden = true;
  });

  form.append(saveButton, cancelButton);
  form.addEventListener("submit", (event) => updateMovie(event, movie.title));
  return form;
}

function readMovieForm(form) {
  const data = new FormData(form);
  return {
    title: data.get("title").trim(),
    genre: data.get("genre").trim(),
    duration: Number(data.get("duration")),
    rating: Number(data.get("rating")),
    releaseYear: Number(data.get("releaseYear")),
  };
}

async function addMovie(event) {
  event.preventDefault();
  const form = event.currentTarget;

  try {
    await apiRequest("/movies", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(readMovieForm(form)),
    });
    form.reset();
    form.hidden = true;
    await loadMovies();
  } catch (error) {
    window.alert(`Unable to add movie: ${error.message}`);
  }
}

async function updateMovie(event, originalTitle) {
  event.preventDefault();
  const form = event.currentTarget;

  try {
    await apiRequest(`/movies/update/${encodeURIComponent(originalTitle)}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(readMovieForm(form)),
    });
    await loadMovies();
  } catch (error) {
    window.alert(`Unable to update movie: ${error.message}`);
  }
}

async function deleteMovie(title) {
  if (!window.confirm(`Delete “${title}”?`)) return;

  try {
    await apiRequest(`/movies/${encodeURIComponent(title)}`, { method: "DELETE" });
    await loadMovies();
  } catch (error) {
    window.alert(`Unable to delete movie: ${error.message}`);
  }
}

function createStatus(message, isError = false) {
  const status = document.createElement("p");
  status.className = isError ? "page-status error" : "page-status";
  status.textContent = message;
  return status;
}
