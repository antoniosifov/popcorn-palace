import { randomUUID } from "node:crypto";

import { expect, test, type Page } from "@playwright/test";

import {
  alertIn,
  emailTo,
  holdPicked,
  logIn,
  newCustomer,
  openShowtimeOfTomorrow,
  pay,
  pickSeats,
  register,
  seat,
} from "./support";

/** The block of the account page that deletes the account. */
const deletion = (page: Page) => page.getByRole("region", { name: "Delete account" });

test("a customer deletes the account: the tickets are refunded, the seats go back on sale, the email is free again", async ({
  page,
}) => {
  const customer = newCustomer();
  await register(page, customer);

  // A ticket for tomorrow, so that there is something to give back.
  const showtime = await openShowtimeOfTomorrow(page);
  const [picked] = await pickSeats(page, 1);
  await holdPicked(page);
  await pay(page);
  await expect(page).toHaveURL(/\/bookings\/[0-9a-f-]+$/);

  // The name in the top bar leads to the account.
  await page.getByRole("link", { name: customer.name }).click();
  await expect(page).toHaveURL("/account");
  await expect(page.getByRole("region", { name: "Details" })).toContainText(customer.email);

  // A wrong password deletes nothing.
  await deletion(page).getByRole("button", { name: "Delete account" }).click();
  await deletion(page).getByLabel("Your password").fill("not-the-password");
  await deletion(page).getByRole("button", { name: "Delete for good" }).click();
  await expect(alertIn(page)).toContainText("That is not the password of this account.");

  await deletion(page).getByLabel("Your password").fill(customer.password);
  await deletion(page).getByRole("button", { name: "Delete for good" }).click();

  await expect(page).toHaveURL("/goodbye");
  await expect(page.getByRole("heading", { name: "Your account is deleted" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Log in" })).toBeVisible();

  // The API wrote both to its outbox, Kafka carried them, the notifications service emailed them.
  await emailTo(customer.email, /was cancelled$/);
  await emailTo(customer.email, /^Your Popcorn Palace account has been deleted$/);

  // The seat is on sale again.
  await page.goto(showtime);
  await expect(seat(page, picked, "available")).toBeVisible();

  // The password opens nothing any more.
  await page.goto("/login");
  await logIn(page, customer.email, customer.password);
  await expect(alertIn(page)).toContainText("The email or the password is not right.");

  // And the address is free: a new account with it starts with no bookings.
  await register(page, { ...customer, password: randomUUID() });
  await page.goto("/bookings");
  await expect(page.getByText("No tickets for upcoming showtimes.")).toBeVisible();
});

test("deleting the account logs out a browser that was logged in elsewhere", async ({ page, browser }) => {
  const customer = newCustomer();
  // One browser stays logged in.
  await register(page, customer);
  await page.goto("/account");
  await expect(page.getByRole("heading", { name: "Account", level: 1 })).toBeVisible();

  // The account is deleted from another one.
  const elsewhere = await browser.newContext({ ignoreHTTPSErrors: process.env.E2E_IGNORE_HTTPS_ERRORS === "true" });
  const other = await elsewhere.newPage();
  await other.goto("/login?next=%2Faccount");
  await logIn(other, customer.email, customer.password);
  await expect(other).toHaveURL("/account");
  await deletion(other).getByRole("button", { name: "Delete account" }).click();
  await deletion(other).getByLabel("Your password").fill(customer.password);
  await deletion(other).getByRole("button", { name: "Delete for good" }).click();
  await expect(other).toHaveURL("/goodbye");
  await elsewhere.close();

  // The first browser still has its cookie, but there is no account behind it.
  await page.goto("/account");
  await expect(page).toHaveURL("/login?next=%2Faccount");
});

test("the account page is for whoever is logged in", async ({ page }) => {
  await page.goto("/account");
  await expect(page).toHaveURL("/login?next=%2Faccount");
});
