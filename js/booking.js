document.addEventListener('DOMContentLoaded', () => {
    loadShowtimesToSelect();
  
document.getElementById('booking-form').addEventListener('submit', async function (e) {
    e.preventDefault();

    const showtimeId = Number(document.getElementById('add-showtimeId').value);
    const seatNumber = Number(document.getElementById('add-seatNumber').value);
    const userId = document.getElementById('add-userId').value.trim();

    if (!showtimeId || !seatNumber || !userId) {
        alert('❗ Please fill all fields.');
        return;
    }

    const selectedShowtime = showtimes.find(s => s.id === showtimeId);

    const bookingData = {
    showtime: {
        id: selectedShowtime.id,
        movie: selectedShowtime.movie,       // movie тоже объект
        theater: selectedShowtime.theater,
        price: selectedShowtime.price,
        start_time: selectedShowtime.start_time,
        end_time: selectedShowtime.end_time
    },
    seatNumber,
    userId
    };


    try {
        const response = await fetch('http://localhost:8080/bookings', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(bookingData)
        });

        if (!response.ok) {
        throw new Error('Booking failed. Status: ' + response.status);
        }

        const result = await response.json();
        document.getElementById('booking-result').textContent =
        `✅ Booking successful! Booking ID: ${result.bookingId}`;

        document.getElementById('booking-form').reset();
    } catch (error) {
        console.error('Error booking ticket:', error);
        document.getElementById('booking-result').textContent =
        '❌ Booking failed. Please try again.';
    }
    });
});

async function loadShowtimesToSelect() {
    try {
    const response = await fetch('http://localhost:8080/showtimes/all');
    if (!response.ok) throw new Error('Failed to load showtimes');

    const showtimes = await response.json();
    const select = document.getElementById('add-showtimeId');
    select.innerHTML = '<option value="">Select Showtime</option>';

    showtimes.forEach(showtime => {
        const option = document.createElement('option');
        option.value = showtime.id;
        option.textContent = `#${showtime.id} | ${showtime.theater} | ${formatDate(showtime.startTime)}`;
        select.appendChild(option);
    });
    } catch (error) {
    console.error('Error loading showtimes:', error);
    }
}

function formatDate(dateTimeString) {
    const options = {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit'
    };
    return new Date(dateTimeString).toLocaleString('en-GB', options);
}