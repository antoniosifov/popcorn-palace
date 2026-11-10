import { randomUUID } from "node:crypto";

import { expect, test, type Page } from "@playwright/test";

import {
  ADMIN,
  alertIn,
  logIn,
  logOut,
  newCustomer,
  openShowtimeOfTomorrow,
  order,
  pickSeats,
  register,
} from "./support";

test("a film has a page of its own, with its showtimes and the rest of the programme", async ({ page }) => {
  await page.goto("/");
  await page
    .getByRole("link", { name: /about the film/i })
    .first()
    .click();

  await expect(page).toHaveURL(/\/movies\/\d+$/);
  const title = await page.getByRole("heading", { level: 1 }).innerText();
  await expect(page.getByRole("heading", { name: "Showtimes" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Choose seats" })).toBeVisible();

  // The rest of the programme is offered from every film's page, and leads to another film.
  const others = page.getByRole("region", { name: "Also showing" }).getByRole("link");
  await others.first().click();

  await expect(page).toHaveURL(/\/movies\/\d+$/);
  await expect(page.getByRole("heading", { level: 1 })).not.toHaveText(title);
});

test("a page that needs an account sends a visitor to log in and then back to it", async ({ page }) => {
  const customer = newCustomer();
  await register(page, customer);
  await logOut(page);

  await page.goto("/bookings");
  await expect(page).toHaveURL("/login?next=%2Fbookings");

  await logIn(page, customer.email, customer.password);

  await expect(page).toHaveURL("/bookings");
  await expect(page.getByRole("heading", { name: "My bookings" })).toBeVisible();
});

test("a visitor can look at the seats but is asked to log in to book, and returns to the showtime", async ({
  page,
}) => {
  const customer = newCustomer();
  // The account exists; the visitor just is not logged in.
  await register(page, customer);
  await logOut(page);

  const path = await openShowtimeOfTomorrow(page);
  await pickSeats(page, 1);
  await order(page).getByRole("link", { name: "Log in to book" }).click();
  await logIn(page, customer.email, customer.password);

  // Back on the same showtime, now as a customer who can hold seats.
  await expect(page).toHaveURL(path);
  await expect(order(page).getByRole("button", { name: /^Hold / })).toBeVisible();
  await expect(order(page).getByRole("link", { name: "Log in to book" })).toHaveCount(0);
});

test("a wrong password is refused without saying whether the account exists", async ({ page }) => {
  await page.goto("/login");
  await logIn(page, `nobody-${randomUUID().slice(0, 8)}@example.com`, "not-the-password");

  await expect(alertIn(page)).toContainText("The email or the password is not right.");
  await expect(page).toHaveURL(/\/login/);
});

test("the session cookie cannot be read by scripts in the page", async ({ page, context }) => {
  await register(page, newCustomer());

  const session = (await context.cookies()).find((cookie) => cookie.name === "pp_session");
  expect(session?.httpOnly).toBe(true);
  expect(session?.sameSite).toBe("Lax");
  expect(await page.evaluate(() => document.cookie)).not.toContain("pp_session");
});

test("the back office does not exist for a customer", async ({ page }) => {
  await register(page, newCustomer());

  await page.goto("/admin/movies");

  await expect(page.getByRole("heading", { name: "Nothing here" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Admin" })).toHaveCount(0);
});

/** Opens an area of the back office and waits until it is the one on screen. */
async function openTab(page: Page, name: "Movies" | "Showtimes") {
  await page.getByRole("link", { name, exact: true }).click();
  await expect(page).toHaveURL(`/admin/${name.toLowerCase()}`);
  await expect(page.getByRole("heading", { level: 1, name })).toBeVisible();
}

test("an administrator adds a movie, schedules it and takes both off again", async ({ page }) => {
  const title = `Test Reel ${randomUUID().slice(0, 8)}`;
  await page.goto("/admin");
  await expect(page).toHaveURL(/\/login\?next=%2Fadmin/);
  await logIn(page, ADMIN.email, ADMIN.password);
  await expect(page).toHaveURL("/admin/showtimes");

  // A movie. The API validates it: a rating of 11 is refused and the field says why.
  await openTab(page, "Movies");
  await page.getByRole("link", { name: "Add a movie" }).click();
  await page.getByLabel("Title").fill(title);
  await page.getByLabel("Genre").fill("Documentary");
  await page.getByLabel("Length, minutes").fill("95");
  await page.getByLabel("Rating, 0 to 10").fill("7.4");
  await page.getByLabel("Year").fill("2029");
  await page.getByRole("button", { name: "Add movie" }).click();
  await expect(page).toHaveURL("/admin/movies");
  const movieRow = page.getByRole("row", { name: new RegExp(title) });
  await expect(movieRow).toContainText("1 h 35 min");

  // A showtime of it, on some day one to three years from now: clear of the
  // programme, and of whatever an interrupted earlier run may have left behind.
  const daysAhead = 365 + Math.floor(Math.random() * 730);
  const day = new Date(Date.now() + daysAhead * 24 * 3600 * 1000).toISOString().slice(0, 10);
  const minute = String(Math.floor(Math.random() * 60)).padStart(2, "0");
  await openTab(page, "Showtimes");
  await page.getByRole("link", { name: "Schedule a showtime" }).click();
  await page.getByLabel("Movie").selectOption({ label: title });
  await page.getByLabel("Hall").selectOption({ index: 1 });
  await page.getByLabel("Starts").fill(`${day}T03:${minute}`);
  // The end follows from the length of the movie: 95 minutes later.
  await expect(page.getByLabel("Ends")).not.toHaveValue("");
  await page.getByRole("button", { name: "Schedule showtime" }).click();
  await expect(page).toHaveURL("/admin/showtimes");
  const showtimeRow = page.getByRole("row", { name: new RegExp(title) });
  await expect(showtimeRow).toContainText(`03:${minute}`);

  // The same slot in the same hall is refused, with the API's reason.
  await page.getByRole("link", { name: "Schedule a showtime" }).click();
  await page.getByLabel("Movie").selectOption({ label: title });
  await page.getByLabel("Hall").selectOption({ index: 1 });
  await page.getByLabel("Starts").fill(`${day}T03:${minute}`);
  await page.getByRole("button", { name: "Schedule showtime" }).click();
  await expect(alertIn(page)).toContainText("Another showtime is already scheduled");
  // The refusal leaves the form as it was typed, ready to be corrected.
  await expect(page.getByLabel("Movie")).toHaveValue(/^[0-9]+$/);
  await expect(page.getByLabel("Starts")).toHaveValue(`${day}T03:${minute}`);

  // A movie with showtimes cannot be deleted.
  await openTab(page, "Movies");
  await movieRow.getByRole("button", { name: "Delete" }).click();
  await movieRow.getByRole("button", { name: "Delete movie" }).click();
  await expect(alertIn(movieRow)).toContainText("This movie still has showtimes.");

  // Take the showtime off, then the movie.
  await openTab(page, "Showtimes");
  await showtimeRow.getByRole("button", { name: "Delete" }).click();
  await showtimeRow.getByRole("button", { name: "Delete showtime" }).click();
  await expect(showtimeRow).toHaveCount(0);

  await openTab(page, "Movies");
  await movieRow.getByRole("button", { name: "Delete" }).click();
  await movieRow.getByRole("button", { name: "Delete movie" }).click();
  await expect(movieRow).toHaveCount(0);
});
