let updatingShowtimeId = null; // хранит ID сеанса, который мы редактируем

document.addEventListener('DOMContentLoaded', () => {
    loadShowtimes();
    loadMoviesToSelect();
});


// LOADING SHOWTIMES
async function loadShowtimes() 
{
    try {
        const response = await fetch('http://localhost:8080/showtimes/all')

        if (!response.ok) 
        {
                throw new Error('Network error' + response.status)
        }

        listShowtimes = await response.json()
        const tableBody = document.getElementById('showtime-table-body'); // 📋 Находим элемент таблицы
        tableBody.innerHTML = ''; // Очищаем, если что-то было

        listShowtimes.forEach(showtime => {
            const row = document.createElement('tr'); // Создаем новую строку таблицы

            row.innerHTML = `
                <td>${showtime.id}</td>
                <td>${showtime.movieId} (${getMovieTitleById(showtime.movieId)})</td>
                <td>${showtime.theater}</td>
                <td>${showtime.price}</td>
                <td>${formatDateTime(showtime.startTime)}</td>
                <td>${formatDateTime(showtime.endTime)}</td>
                <td>
                    <button class="update-btn" onclick="updateShowtime(${showtime.id})">Update</button>
                    <button class="delete-btn" onclick="deleteShowtime(${showtime.id})">Delete</button>
                </td>
            `;

            row.classList.add('fade-in');
            tableBody.appendChild(row); // Добавляем строку в таблицу
        });

    } catch (error) {
        console.error('Error loading showtimes:', error); // ⚡ Выводим ошибку в консоль
    }

}

function formatDateTime(dateTimeString) {
    const options = {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit'
    };
    return new Date(dateTimeString).toLocaleString('en-GB', options);
}


//GET SHOWTIME BY ID
async function getShowtimeById() 
{
    const id = document.getElementById('get-id-input').value;

    if (!id) 
    {
        alert('Please enter a Showtime ID');
        return;
    }

    try {
        const response = await fetch(`http://localhost:8080/showtimes/${id}`);

        if (!response.ok)
        {
            throw new Error(`Showtime not found. Status: ${response.status}`);
        }

        const showtime = await response.json();

        const resultElement = document.getElementById('showtime-by-id-result');
        resultElement.innerHTML = `
            <div style="background:#1c1c2c; border:1px solid #c084fc; padding:15px; border-radius:12px; margin-top:15px; box-shadow: 0 0 10px #a855f7;">
                <p><strong>ID:</strong> ${showtime.id}</p>
                <p><strong>Movie ID:</strong> ${showtime.movieId}</p>
                <p><strong>Theater:</strong> ${showtime.theater}</p>
                <p><strong>Price:</strong> $${showtime.price}</p>
                <p><strong>Start Time:</strong> ${formatDateTime(showtime.startTime)}</p>
                <p><strong>End Time:</strong> ${formatDateTime(showtime.endTime)}</p>
            </div>
        `;

    } catch (error) {
        console.error('Error getting showtime:', error);
        document.getElementById('showtime-by-id-result').textContent = '❌ Showtime not found or server error.';
    }
}



// ADD SHOWTIME
document.getElementById('add-form').addEventListener('submit', async function (event) {
    event.preventDefault();
  
    const movieId = Number(document.getElementById('add-movieId').value);
    const movie = movies.find(m => m.id === movieId); // 👈 ищем весь объект Movie
  
    if (!movie) {
        showToast("❌ Movie not found.");
        return;
    }
  
    const theater = document.getElementById('add-theater').value;
    const price = Number(document.getElementById('add-price').value);
    const startTime = document.getElementById('add-startTime').value;
    const endTime = document.getElementById('add-endTime').value;
  
    const showtime = {
      movie,
      theater,
      price,
      start_time: startTime,
      end_time: endTime
    };
    
    console.log("📤 Отправляем обновлённый сеанс:", showtime);
    try {
      const response = await fetch('http://localhost:8080/showtimes', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(showtime)
      });
  
      if (!response.ok) {
        throw new Error('Failed to add showtime. Status: ' + response.status);
      }
  
      showToast('✅ Showtime added successfully!');
      document.getElementById('add-form').reset();
      loadShowtimes();
    } catch (error) {
      console.error('Error adding showtime:', error);
      showToast('❌ Error adding showtime.');
    }
  });
  
  

  let movies = []; // ⬅️ сюда загрузим все фильмы

  // MOVIE SELECT IN ADD SHOWTIME FORM
  async function loadMoviesToSelect() {
    try {
      const response = await fetch('http://localhost:8080/movies/all');
      if (!response.ok) throw new Error('Failed to load movies');
  
      movies = await response.json();
      const select = document.getElementById('add-movieId');
      select.innerHTML = '<option value="">Select Movie</option>';
  
      movies.forEach(movie => {
        const option = document.createElement('option');
        option.value = movie.id;
        option.textContent = `${movie.title} (${movie.releaseYear})`;
        select.appendChild(option);
      });
    } catch (error) {
      console.error('Error loading movies:', error);
    }
  }


  function getMovieTitleById(id) {
    const movie = movies.find(m => m.id === id);
    return movie ? movie.title : 'Unknown';
  }

  
  // DELETE SHOWTIME
  async function deleteShowtime(id) {
    if (!confirm(`❗ Are you sure you want to delete showtime #${id}?`)) return;
  
    try {
      const response = await fetch(`http://localhost:8080/showtimes/${id}`, {
        method: 'DELETE'
      });
  
      if (!response.ok) {
        throw new Error('Failed to delete showtime. Status: ' + response.status);
      }
  
      showToast(`🗑️ Showtime #${id} deleted.`);
      loadShowtimes(); // 🔄 Обновим таблицу
    } catch (error) {
      console.error('Error deleting showtime:', error);
      showToast('❌ Failed to delete showtime.');
    }
  }


  // UPDATE SHOWTIME
  function updateShowtime(id) {
    // Удаляем предыдущую форму, если она уже есть
    const oldFormRow = document.querySelector('.update-row');
    if (oldFormRow) oldFormRow.remove();
  
    const showtime = listShowtimes.find(s => s.id === id);
    if (!showtime) {
        showToast('❌ Showtime not found!');
        return;
    }
  
    const table = document.getElementById('showtime-table-body');
    const row = [...table.rows].find(r => r.cells[0].textContent == id);
  
    // Создаём новую строку с формой
    const formRow = document.createElement('tr');
    formRow.classList.add('update-row');
    formRow.innerHTML = `
      <td colspan="7">
        <form onsubmit="submitUpdate(event, ${id})" class="inline-form">
          <input type="text" id="update-theater-${id}" value="${showtime.theater}" placeholder="Theater" required />
          <input type="number" id="update-price-${id}" value="${showtime.price}" placeholder="Price" required />
          <input type="datetime-local" id="update-start-${id}" value="${showtime.startTime.slice(0,16)}" required />
          <input type="datetime-local" id="update-end-${id}" value="${showtime.endTime.slice(0,16)}" required />
          <button type="submit">Save</button>
        </form>
      </td>
    `;
  
    // Вставляем строку после строки нужного сеанса
    row.after(formRow);
  }

  async function submitUpdate(event, id) {
    event.preventDefault();
  
    const theater = document.getElementById(`update-theater-${id}`).value;
    const price = Number(document.getElementById(`update-price-${id}`).value);
    const startTime = document.getElementById(`update-start-${id}`).value;
    const endTime = document.getElementById(`update-end-${id}`).value;
  
    const showtime = listShowtimes.find(s => s.id === id);
    const movie = movies.find(m => m.id === showtime.movieId);
  
    if (!movie) {
        showToast('❌ Movie not found.');
        return;
    }
  
    const updatedShowtime = {
      id,
      movie,
      theater,
      price,
      start_time: startTime,
      end_time: endTime
    };
  
    try {
      const response = await fetch(`http://localhost:8080/showtimes/update/${id}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(updatedShowtime)
      });
  
      if (!response.ok) {
        throw new Error('Failed to update showtime. Status: ' + response.status);
      }
  
      showToast('✅ Showtime updated successfully!');
      document.querySelector('.update-row').remove(); // убираем форму
      loadShowtimes(); // обновим таблицу
    } catch (error) {
      console.error('Error updating showtime:', error);
      alert('❌ Error updating showtime.');
    }
  }


  function showToast(message) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.classList.add('show');
    setTimeout(() => {
      toast.classList.remove('show');
    }, 3000);
  }
  
  
  

  