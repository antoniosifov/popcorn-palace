import { randomUUID } from "node:crypto";

import { expect, test, type Page } from "@playwright/test";

import { alertIn, logOut } from "./support";

/**
 * Signing in with Google, against a stand-in for Google (compose.e2e.yml).
 * Without it there is nothing to sign in with, and these tests are skipped.
 */
const FAKE_GOOGLE = process.env.E2E_FAKE_GOOGLE_URL;

test.skip(!FAKE_GOOGLE, "needs the stack of compose.e2e.yml and E2E_FAKE_GOOGLE_URL");

type Person = { sub: string; email: string; name: string; emailVerified: boolean };

/** Somebody Google has never sent here before. */
function newPerson(): Person {
  const id = randomUUID().slice(0, 8);
  return {
    sub: `1${Date.now()}${Math.floor(Math.random() * 1e6)}`,
    email: `google-${id}@example.com`,
    name: `Gina ${id}`,
    emailVerified: true,
  };
}

/** Tells the stand-in who "signs in" next. */
async function atGoogle(person: Person): Promise<void> {
  const response = await fetch(`${FAKE_GOOGLE}/person`, { method: "POST", body: JSON.stringify(person) });
  expect(response.ok).toBe(true);
}

async function continueWithGoogle(page: Page): Promise<void> {
  await page.getByRole("link", { name: "Continue with Google" }).click();
}

test("a visitor signs in with Google, gets an account, and is taken where they were going", async ({
  page,
  context,
}) => {
  const person = newPerson();
  await atGoogle(person);

  // Asked to log in on the way to a page that needs an account.
  await page.goto("/bookings");
  await expect(page).toHaveURL("/login?next=%2Fbookings");
  await continueWithGoogle(page);

  // To Google and back, and on to where they were going.
  await expect(page).toHaveURL("/bookings");
  await expect(page.getByRole("heading", { name: "My bookings" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible();

  const cookies = await context.cookies();
  // The session is the same kind of session a password gives ...
  expect(cookies.find((cookie) => cookie.name === "pp_session")?.httpOnly).toBe(true);
  // ... and nothing is left of the attempt.
  expect(cookies.find((cookie) => cookie.name === "pp_google")).toBeUndefined();
});

test("signing in with Google again is the same account", async ({ page }) => {
  const person = newPerson();
  await atGoogle(person);
  await page.goto("/login");
  await continueWithGoogle(page);
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible();
  await logOut(page);

  await page.goto("/register");
  await continueWithGoogle(page);

  // No second account, no complaint that the email is taken: just logged in.
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible();
  await expect(alertIn(page)).toHaveCount(0);
});

test("an address Google has not confirmed is turned away, with a reason", async ({ page }) => {
  await atGoogle({ ...newPerson(), emailVerified: false });

  await page.goto("/login");
  await continueWithGoogle(page);

  await expect(page).toHaveURL(/\/login\?/);
  await expect(alertIn(page)).toContainText("Google has not confirmed this email address yet.");
  await expect(page.getByRole("button", { name: "Log out" })).toHaveCount(0);
});

test("an answer that does not belong to a sign-in this browser started is refused", async ({ page }) => {
  // What an attacker would send a victim: the tail end of a sign-in the victim never began.
  await page.goto("/api/auth/google/callback?code=somebody-elses-code&state=somebody-elses-state");

  await expect(page).toHaveURL(/\/login\?/);
  await expect(alertIn(page)).toContainText("Signing in with Google did not work.");
  await expect(page.getByRole("button", { name: "Log out" })).toHaveCount(0);
});

test("pressing Cancel at Google leads back to the login form, without a complaint", async ({ page }) => {
  await page.goto("/api/auth/google/callback?error=access_denied");

  await expect(page).toHaveURL(/\/login/);
  await expect(page.getByRole("link", { name: "Continue with Google" })).toBeVisible();
  await expect(alertIn(page)).toHaveCount(0);
});

test("words written into the address do not appear on the page", async ({ page }) => {
  await page.goto("/login?google=Your%20account%20was%20deleted");

  await expect(alertIn(page)).toHaveCount(0);
  await expect(page.getByText("Your account was deleted")).toHaveCount(0);
});

test("an account opened with Google is deleted without a password, and Google then opens a new one", async ({
  page,
}) => {
  const person = newPerson();
  await atGoogle(person);
  await page.goto("/login?next=%2Faccount");
  await continueWithGoogle(page);
  await expect(page).toHaveURL("/account");
  await expect(page.getByRole("region", { name: "Details" })).toContainText("With Google");

  // There is no password to ask for: the question alone.
  const deletion = page.getByRole("region", { name: "Delete account" });
  await deletion.getByRole("button", { name: "Delete account" }).click();
  await expect(deletion.getByLabel("Your password")).toHaveCount(0);
  await deletion.getByRole("button", { name: "Delete for good" }).click();
  await expect(page).toHaveURL("/goodbye");
  await expect(page.getByRole("link", { name: "Log in" })).toBeVisible();

  // Google no longer leads to the old account: the same person starts from nothing.
  await atGoogle(person);
  await page.goto("/login?next=%2Fbookings");
  await continueWithGoogle(page);
  await expect(page).toHaveURL("/bookings");
  await expect(page.getByText("No tickets for upcoming showtimes.")).toBeVisible();
  await expect(alertIn(page)).toHaveCount(0);
});
