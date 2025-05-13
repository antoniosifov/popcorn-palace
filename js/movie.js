document.addEventListener('DOMContentLoaded', () => {
     loadMovies();
   });


cancelButton.addEventListener('click', () => {
  updateForm.style.display = 'none';
});
   
   async function loadMovies() 
   {
     try {
          const response = await fetch('http://localhost:8080/movies/all')

          if (!response.ok) 
          {
               throw new Error('Network error' + response.status)
          }
     
          const listMovies = await response.json()
          const movieListContainer = document.getElementById('movie-list')
       
          movieListContainer.innerHTML = ''
   
          listMovies.forEach(movie => {
               const movieCard = document.createElement('div')
               movieCard.classList.add('movie-card')
     
               movieCard.innerHTML = `
               <img src="img/photo7.webp" alt="Movie Image" class="movie-image" />
               <h2>${movie.title}</h2>
               <p><strong>Genre:</strong> ${movie.genre}</p>
               <p><strong>Duration:</strong> ${movie.duration} мин</p>
               <p><strong>Rating:</strong> ${movie.rating}</p>
               <p><strong>Release year:</strong> ${movie.releaseYear}</p>
               <div class="movie-actions">
                    <button class="update-button" data-id="${movie.id}">Update</button>
                    <button class="delete-button" data-id="${movie.id}">Delete</button>
               </div>
               `;
     
               // movieCard.querySelector('.update-button').addEventListener('click', () => {
               // alert(`Обновление фильма с ID: ${movie.id}`)
               // });
     
               movieCard.querySelector('.delete-button').addEventListener('click', () => {
                    deleteMovie(movie.title)
               });

               movieListContainer.appendChild(movieCard)


               const updateForm = document.createElement('form')
               updateForm.classList.add('update-form');

               updateForm.innerHTML = `
                    <input type="text" name="title" value="${movie.title}" placeholder="Title" required />
                    <input type="text" name="genre" value="${movie.genre}" placeholder="Genre" required />
                    <input type="number" name="duration" value="${movie.duration}" placeholder="Duration" required />
                    <input type="number" step="0.1" name="rating" value="${movie.rating}" placeholder="Rating" required />
                    <input type="number" name="releaseYear" value="${movie.releaseYear}" placeholder="Release Year" required />
                    <button type="submit">Save</button>
                    <button type="button" class="cancel-button">Cancel</button>
                    `
               updateForm.style.display = 'none';
               movieCard.appendChild(updateForm);

               const updateButton = movieCard.querySelector('.update-button');
               const cancelButton = updateForm.querySelector('.cancel-button');

               updateButton.addEventListener('click', () => {
               updateForm.style.display = 'block';
               })

cancelButton.addEventListener('click', () => {
     updateForm.style.display = 'none';
});

updateForm.addEventListener('submit', async (event) => {
     event.preventDefault();
   
     const updatedMovie = {
       title: updateForm.title.value,
       genre: updateForm.genre.value,
       duration: parseInt(updateForm.duration.value),
       rating: parseFloat(updateForm.rating.value),
       releaseYear: parseInt(updateForm.releaseYear.value),
     };
   
     try {
       const response = await fetch(`http://localhost:8080/movies/update/${encodeURIComponent(movie.title)}`, {
         method: 'POST',
         headers: {
           'Content-Type': 'application/json'
         },
         body: JSON.stringify(updatedMovie)
       });
   
       if (!response.ok) {
         throw new Error(`HTTP error! status: ${response.status}`);
       }
   
       // Обновим отображение фильмов
       await loadMovies();
     } catch (error) {
       console.error('Error updating movie:', error);
     }
     });
     })
     } catch (error) {
          console.error('Ошибка при получении фильмов:', error)
     };



     // Создание и добавление кнопки "Add New Movie"
// const addMovieButton = document.createElement('button');
// addMovieButton.textContent = 'Add New Movie';
// addMovieButton.id = 'add-movie-button';
// document.body.appendChild(addMovieButton);

// Создание и добавление формы для добавления нового фильма
const addMovieForm = document.createElement('form');
addMovieForm.classList.add('addMovieForm');
addMovieForm.style.display = 'none'; // Скрыть форму по умолчанию

addMovieForm.innerHTML = `
  <input type="text" name="title" placeholder="Title" required />
  <input type="text" name="genre" placeholder="Genre" required />
  <input type="number" name="duration" placeholder="Duration" required />
  <input type="number" step="0.1" name="rating" placeholder="Rating" required />
  <input type="number" name="releaseYear" placeholder="Release Year" required />
  <button type="submit">Add Movie</button>
  <button type="button" class="cancel-button">Cancel</button>
`;

document.body.appendChild(addMovieForm);

// Обработчик для отображения формы при нажатии на кнопку


document.getElementById('add-movie-button').addEventListener('click', () => {
  addMovieForm.style.display = 'block';
});

// Обработчик для скрытия формы при нажатии на кнопку "Cancel"
addMovieForm.querySelector('.cancel-button').addEventListener('click', () => {
  addMovieForm.style.display = 'none';
});

// Обработчик для отправки данных формы на сервер
addMovieForm.addEventListener('submit', async (event) => {
  event.preventDefault();

  const formData = new FormData(addMovieForm);
  const newMovie = {
    title: formData.get('title'),
    genre: formData.get('genre'),
    duration: parseInt(formData.get('duration')),
    rating: parseFloat(formData.get('rating')),
    releaseYear: parseInt(formData.get('releaseYear')),
  };

  try {
    const response = await fetch('http://localhost:8080/movies', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(newMovie)
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

   
    await loadMovies();
    addMovieForm.reset(); // Очистить форму
    addMovieForm.style.display = 'none'; // Скрыть форму после отправки

  } catch (error) 
  {
    console.error('Error adding movie:', error);
  }
});

}





async function deleteMovie(title) 
{
     console.log(`id movie: ${title}`)
     try {
          const response = await fetch(`http://localhost:8080/movies/${encodeURIComponent(title)}`, { method: 'DELETE' })

          if (!response.ok) 
          {
               throw new Error(`Error deleting movie: ${response.status}`);
          }
          
     } catch (error) {
          console.error('Error deleting movie:', error);
     }
     await loadMovies();
}


document.getElementById('back-to-home').addEventListener('click', () => {
     window.location.href = 'http://localhost/js/';
})


   