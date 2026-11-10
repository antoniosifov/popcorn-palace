// The shapes the API returns and accepts, as documented in backend/README.md.
// Instants are ISO-8601 strings in UTC; money is a JSON number with two decimals.

export type Role = "CUSTOMER" | "ADMIN";
export type SeatCategory = "STANDARD" | "VIP";
export type SeatStatus = "AVAILABLE" | "HELD" | "BOOKED";
export type BookingStatus = "CONFIRMED" | "CANCELLED";
export type PaymentStatus = "PENDING" | "SUCCEEDED" | "FAILED" | "REFUND_PENDING" | "REFUNDED";

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type User = {
  id: string;
  email: string;
  displayName: string;
  role: Role;
  createdAt: string;
};

export type AuthResponse = {
  accessToken: string;
  tokenType: "Bearer";
  /** Seconds until the token expires. */
  expiresIn: number;
  user: User;
};

export type Movie = {
  id: number;
  title: string;
  genre: string;
  durationMinutes: number;
  rating: number;
  releaseYear: number;
  /** What the movie is about. Everything from here down is optional: a movie may have none of it. */
  overview: string | null;
  tagline: string | null;
  /** An https address of the poster (2:3). Movies imported from TMDB have one; the built-in catalog does not. */
  posterUrl: string | null;
  /** An https address of a wide still. */
  backdropUrl: string | null;
  trailerUrl: string | null;
  director: string | null;
  /** The leading cast, most prominent first. */
  starring: string[];
  /** Whether the movie is playing in cinemas right now, according to the latest import. */
  inCinemas: boolean;
  /** How much attention the movie gets at its source; null for a movie added by hand. */
  popularity: number | null;
};

/** What an administrator can set. Where a movie came from and how popular it is are not theirs to edit. */
export type MovieInput = Omit<Movie, "id" | "inCinemas" | "popularity">;

/** A form as it was typed: any field may still be empty. The API says which ones must not be. */
export type Draft<T> = { [K in keyof T]: T[K] | null };

export type HallSummary = {
  id: number;
  name: string;
  rowCount: number;
  seatCount: number;
};

export type Hall = HallSummary & {
  rows: { row: number; category: SeatCategory; seats: number }[];
};

export type HallSection = { rows: number; seatsPerRow: number; category: SeatCategory };
export type HallInput = { name: string; sections: Draft<HallSection>[] };

export type Prices = Partial<Record<SeatCategory, number>>;

export type Showtime = {
  id: number;
  movieId: number;
  movieTitle: string;
  hallId: number;
  hallName: string;
  startTime: string;
  endTime: string;
  prices: Prices;
};

export type ShowtimeInput = {
  movieId: number;
  hallId: number;
  startTime: string;
  endTime: string;
  prices: Prices;
};

export type Seat = {
  seatId: number;
  number: number;
  category: SeatCategory;
  price: number;
  status: SeatStatus;
  heldByMe: boolean;
};

export type SeatRow = { row: number; seats: Seat[] };

export type SeatMap = {
  showtimeId: number;
  hallId: number;
  hallName: string;
  totalSeats: number;
  availableSeats: number;
  rows: SeatRow[];
};

/** One `seats` event of the live stream. */
export type SeatEvent = {
  showtimeId: number;
  seatIds: number[];
  status: SeatStatus;
  /** The hold that now has the seats, when the status is HELD. */
  holdId: string | null;
};

export type Ticket = {
  seatId: number;
  row: number;
  seatNumber: number;
  category: SeatCategory;
  price: number;
};

export type Hold = {
  id: string;
  showtimeId: number;
  seats: Ticket[];
  totalPrice: number;
  expiresAt: string;
};

export type Booking = {
  id: string;
  status: BookingStatus;
  showtimeId: number;
  movieId: number;
  movieTitle: string;
  moviePosterUrl: string | null;
  hallName: string;
  startTime: string;
  seats: Ticket[];
  totalPrice: number;
  payment: { id: string; status: PaymentStatus; amount: number; currency: string } | null;
  userId: string;
  createdAt: string;
  cancelledAt: string | null;
};

/** The body of every error response (RFC 9457 problem details, plus the API's own fields). */
export type Problem = {
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Stable, machine-readable: what the client branches on. */
  code?: string;
  /** The trace of the failed request; quote it when reporting a problem. */
  traceId?: string;
  /** Rejected fields of a VALIDATION_FAILED response. */
  errors?: { field: string; message: string }[];
  /** The seats that were lost, on SEAT_ALREADY_HELD and SEAT_ALREADY_BOOKED. */
  seatIds?: number[];
  /** The hold the user already has, on HOLD_ALREADY_EXISTS. */
  holdId?: string;
  /** Why the card was refused, on PAYMENT_DECLINED. */
  declineCode?: string;
};
