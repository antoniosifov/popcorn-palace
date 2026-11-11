import { randomUUID } from "node:crypto";

import { expect, type Locator, type Page } from "@playwright/test";

/** Where the mail catcher of the stack shows what was sent. */
export const MAILPIT_URL = process.env.E2E_MAILPIT_URL ?? "http://localhost:8025";

/** The administrator that docker compose creates (the development defaults of compose.yml). */
export const ADMIN = {
  email: process.env.E2E_ADMIN_EMAIL ?? "admin@popcorn.local",
  password: process.env.E2E_ADMIN_PASSWORD ?? "admin-dev-password",
};

export type Customer = { name: string; email: string; password: string };

/** A customer nobody has seen before, so tests never trip over each other's accounts. */
export function newCustomer(): Customer {
  const id = randomUUID().slice(0, 8);
  return { name: `Test ${id}`, email: `e2e-${id}@example.com`, password: randomUUID() };
}

export async function register(page: Page, customer: Customer): Promise<void> {
  await page.goto("/register");
  await page.getByLabel("Name").fill(customer.name);
  await page.getByLabel("Email").fill(customer.email);
  await page.getByLabel("Password").fill(customer.password);
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible();
}

/** Logs out and waits until the page shows the visitor as logged out. */
export async function logOut(page: Page): Promise<void> {
  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page.getByRole("link", { name: "Log in" })).toBeVisible();
}

export async function logIn(page: Page, email: string, password: string): Promise<void> {
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Log in" }).click();
}

/**
 * Opens a showtime of tomorrow: far enough away that it cannot start in the
 * middle of a test. `index` picks which one, so tests can use different halls.
 */
export async function openShowtimeOfTomorrow(page: Page, index = 0): Promise<string> {
  await page.goto("/");
  const tomorrow = page.getByRole("link", { name: /^Tomorrow/ });
  await tomorrow.click();
  await expect(tomorrow).toHaveAttribute("aria-current", "page");
  const showtime = page
    .getByRole("region", { name: /Movies on/ })
    .getByRole("link", { name: /^\d{2}:\d{2}/ })
    .nth(index);
  await showtime.click();
  await expect(page).toHaveURL(/\/showtimes\/\d+$/);
  await expect(page.getByText("Live", { exact: true })).toBeVisible();
  return new URL(page.url()).pathname;
}

/** An error shown on the page (the framework keeps an alert region of its own, for announcing navigation). */
export const alertIn = (scope: Page | Locator): Locator =>
  scope.locator('[role="alert"]:not(#__next-route-announcer__)');

export const order = (page: Page): Locator => page.getByRole("region", { name: "Your order" });
export const seatMap = (page: Page): Locator => page.getByRole("region", { name: "Seat map" });

/** Seats that can still be picked. */
export const freeSeats = (page: Page): Locator => seatMap(page).getByRole("button", { name: /, available$/ });

/** Picks the first free seats of the hall and returns their labels without the status ("Row 1, seat 3, standard, $11.00"). */
export async function pickSeats(page: Page, count: number): Promise<string[]> {
  const labels: string[] = [];
  for (let picked = 0; picked < count; picked++) {
    const seat = freeSeats(page).first();
    const label = (await seat.getAttribute("aria-label")) ?? "";
    await seat.click();
    labels.push(label.replace(/, available$/, ""));
  }
  return labels;
}

/** A seat by its label, whatever its status: a button while it can be picked, a picture once it cannot. */
export function seat(page: Page, label: string, status: string): Locator {
  return seatMap(page).getByLabel(`${label}, ${status}`, { exact: true });
}

export async function holdPicked(page: Page): Promise<void> {
  await order(page)
    .getByRole("button", { name: /^Hold \d+ seats?/ })
    .click();
  await expect(order(page).getByRole("heading", { name: "Your seats are held" })).toBeVisible();
}

export async function pay(page: Page, card = "Test card 1"): Promise<void> {
  await order(page)
    .getByRole("radio", { name: new RegExp(card) })
    .check();
  await order(page).getByRole("button", { name: /^Pay / }).click();
}

type MailpitSearch = { messages: { ID: string; Subject: string; To: { Address: string }[] }[] };

/** Waits for an email to the address to arrive in the mail catcher and returns its subject. */
export async function emailTo(address: string, subject: RegExp): Promise<string> {
  let found = "";
  await expect
    .poll(
      async () => {
        const response = await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${address}`)}`);
        const result = (await response.json()) as MailpitSearch;
        found = result.messages.map((message) => message.Subject).find((candidate) => subject.test(candidate)) ?? "";
        return found;
      },
      { timeout: 30_000, message: `an email matching ${subject} to ${address}` },
    )
    .not.toBe("");
  return found;
}

/**
 * Waits for an email to the address with a link in it and returns the newest
 * such link. The link is read from the text of the email, the way its reader gets it.
 */
export async function linkInEmailTo(address: string, subject: RegExp): Promise<string> {
  let link = "";
  await expect
    .poll(
      async () => {
        const search = await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${address}`)}`);
        const result = (await search.json()) as MailpitSearch;
        // The mail catcher lists the newest message first.
        const message = result.messages.find((candidate) => subject.test(candidate.Subject));
        if (!message) {
          return "";
        }
        const email = (await (await fetch(`${MAILPIT_URL}/api/v1/message/${message.ID}`)).json()) as { Text: string };
        link = /https?:\/\/\S+/.exec(email.Text)?.[0] ?? "";
        return link;
      },
      { timeout: 30_000, message: `an email matching ${subject} with a link, to ${address}` },
    )
    .not.toBe("");
  return link;
}
