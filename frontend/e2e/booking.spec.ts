import { expect, test } from "@playwright/test";

import {
  alertIn,
  emailTo,
  freeSeats,
  holdPicked,
  newCustomer,
  openShowtimeOfTomorrow,
  order,
  pay,
  pickSeats,
  register,
  seat,
} from "./support";

test("a customer picks seats, pays and gets the tickets on the page and by email", async ({ page }) => {
  const customer = newCustomer();
  await register(page, customer);

  await openShowtimeOfTomorrow(page);
  const movie = await page.getByRole("heading", { level: 1 }).innerText();
  const picked = await pickSeats(page, 2);

  // The picked seats are the customer's and listed in the order.
  await expect(seat(page, picked[0], "yours")).toBeVisible();
  await expect(order(page).getByRole("button", { name: /^Hold 2 seats · \$\d+\.\d{2}$/ })).toBeEnabled();

  await holdPicked(page);
  await expect(order(page).getByRole("timer")).toHaveText(/^\d:\d{2}$/);

  await pay(page);

  // The booking page is the ticket.
  await expect(page).toHaveURL(/\/bookings\/[0-9a-f-]{36}$/);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(movie);
  await expect(page.getByText("Confirmed", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Your tickets" })).toBeVisible();
  await expect(page.getByText(`The tickets were also emailed to ${customer.email}.`)).toBeVisible();

  // And it is listed under the customer's bookings.
  await page.getByRole("link", { name: "My bookings" }).first().click();
  await expect(page.getByRole("region", { name: "Upcoming" }).getByRole("heading", { name: movie })).toBeVisible();

  // The API published the booking to Kafka; the notifications service turned it into an email.
  expect(await emailTo(customer.email, /^Your tickets for /)).toBe(`Your tickets for ${movie}`);
});

test("a seat somebody else holds, releases or buys changes on the map without a reload", async ({ page, browser }) => {
  // Alex looks at the showtime without an account; Sam is going to book.
  const alex = page;
  const path = await openShowtimeOfTomorrow(alex, 1);

  const samsWindow = await browser.newContext();
  const sam = await samsWindow.newPage();
  await register(sam, newCustomer());
  await sam.goto(path);
  await expect(sam.getByText("Live", { exact: true })).toBeVisible();

  const [label] = await pickSeats(sam, 1);
  // Picking is local: nothing is reserved yet, so Alex still sees the seat as free.
  await expect(seat(alex, label, "available")).toBeVisible();
  const freeBefore = await freeSeats(alex).count();

  await holdPicked(sam);
  await expect(seat(alex, label, "being booked by someone else")).toBeVisible();
  await expect(freeSeats(alex)).toHaveCount(freeBefore - 1);

  await order(sam).getByRole("button", { name: "Change seats" }).click();
  await expect(seat(alex, label, "available")).toBeVisible();

  await holdPicked(sam);
  await pay(sam);
  await expect(sam).toHaveURL(/\/bookings\//);
  await expect(seat(alex, label, "sold")).toBeVisible();

  await samsWindow.close();
});

test("a picked seat that somebody else takes first leaves the order, with a word about it", async ({
  page,
  browser,
}) => {
  await register(page, newCustomer());
  const path = await openShowtimeOfTomorrow(page, 2);
  const picked = await pickSeats(page, 2);

  // Somebody else holds one of those two seats before this customer does.
  const otherWindow = await browser.newContext();
  const other = await otherWindow.newPage();
  await register(other, newCustomer());
  await other.goto(path);
  await seat(other, picked[1], "available").click();
  await holdPicked(other);

  await expect(order(page).getByText(/was just taken by somebody else\.$/)).toBeVisible();
  await expect(order(page).getByRole("button", { name: /^Hold 1 seat · / })).toBeEnabled();
  await expect(seat(page, picked[1], "being booked by someone else")).toBeVisible();

  await otherWindow.close();
});

test("a declined card keeps the seats held, and another card completes the booking", async ({ page }) => {
  await register(page, newCustomer());
  await openShowtimeOfTomorrow(page, 3);
  await pickSeats(page, 1);
  await holdPicked(page);

  await pay(page, "Test card 3");

  await expect(alertIn(order(page))).toContainText("The card was declined: insufficient funds.");
  await expect(alertIn(order(page))).toContainText(/Reference [0-9a-f]{32}/);
  await expect(order(page).getByRole("heading", { name: "Your seats are held" })).toBeVisible();

  await pay(page, "Test card 1");

  await expect(page).toHaveURL(/\/bookings\//);
  await expect(page.getByText("Confirmed", { exact: true })).toBeVisible();
});

test("cancelling a booking refunds it, says so by email and puts the seat back on sale", async ({ page }) => {
  const customer = newCustomer();
  await register(page, customer);
  const path = await openShowtimeOfTomorrow(page, 4);
  const [label] = await pickSeats(page, 1);
  await holdPicked(page);
  await pay(page);
  await expect(page).toHaveURL(/\/bookings\//);

  await page.getByRole("button", { name: "Cancel booking" }).click();
  await expect(
    page.getByText(/^Cancel this booking\? The seats go back on sale and \$\d+\.\d{2} is refunded\.$/),
  ).toBeVisible();
  await page.getByRole("button", { name: "Cancel and refund" }).click();

  await expect(page.getByText(/^Cancelled/).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Cancel booking" })).toHaveCount(0);

  await page.goto(path);
  await expect(seat(page, label, "available")).toBeVisible();

  await page.goto("/bookings?show=cancelled");
  await expect(page.getByRole("region", { name: "Cancelled" }).getByRole("listitem")).toHaveCount(1);

  await emailTo(customer.email, /was cancelled$/);
});

test("a hold survives leaving the page: coming back shows the same seats and the time left", async ({ page }) => {
  await register(page, newCustomer());
  const path = await openShowtimeOfTomorrow(page, 5);
  const [label] = await pickSeats(page, 1);
  await holdPicked(page);

  await page.goto("/");
  await page.goto(path);

  await expect(order(page).getByRole("heading", { name: "Your seats are held" })).toBeVisible();
  await expect(seat(page, label, "yours")).toBeVisible();
  await expect(order(page).getByRole("timer")).toHaveText(/^\d:\d{2}$/);

  // Give the seat back, so the test leaves the hall as it found it.
  await order(page).getByRole("button", { name: "Change seats" }).click();
  await order(page).getByRole("heading", { name: "Your seats", exact: true }).waitFor();
});
