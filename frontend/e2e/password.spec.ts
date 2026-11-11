import { randomUUID } from "node:crypto";

import { expect, test } from "@playwright/test";

import { alertIn, linkInEmailTo, logIn, logOut, newCustomer, register } from "./support";

test("a customer who forgot the password gets a link by email, sets a new one and logs in with it", async ({
  page,
}) => {
  const customer = newCustomer();
  const newPassword = randomUUID();
  await register(page, customer);
  await logOut(page);

  // From the login form to the form that asks for the link.
  await page.goto("/login");
  await page.getByRole("link", { name: "Forgot your password?" }).click();
  await expect(page).toHaveURL("/forgot-password");
  await page.getByLabel("Email").fill(customer.email);
  await page.getByRole("button", { name: "Send the link" }).click();
  await expect(page.getByRole("status")).toContainText(`If there is an account for ${customer.email}`);

  // The API wrote the request to its outbox, Kafka carried it, the notifications service emailed the link.
  const link = new URL(await linkInEmailTo(customer.email, /^Set a new password/));
  expect(link.pathname).toBe("/reset-password");

  await page.goto(link.pathname + link.search);
  await page.getByLabel("New password").fill(newPassword);
  await page.getByRole("button", { name: "Set new password" }).click();
  await expect(page).toHaveURL("/login?reset=done");
  await expect(page.getByRole("status")).toContainText("Your password has been changed.");

  // The old password is no longer any good; the new one is.
  await logIn(page, customer.email, customer.password);
  await expect(alertIn(page)).toContainText("The email or the password is not right.");
  await logIn(page, customer.email, newPassword);
  await expect(page.getByRole("button", { name: "Log out" })).toBeVisible();
  await logOut(page);

  // And the link has done its work: a second visit changes nothing.
  await page.goto(link.pathname + link.search);
  await page.getByLabel("New password").fill(randomUUID());
  await page.getByRole("button", { name: "Set new password" }).click();
  await expect(alertIn(page)).toContainText("This link has expired or has already been used.");
  await expect(page.getByRole("link", { name: "Ask for a new link" })).toBeVisible();
});

test("asking for a link says the same thing for an address that has no account", async ({ page }) => {
  const nobody = `nobody-${randomUUID().slice(0, 8)}@example.com`;

  await page.goto("/forgot-password");
  await page.getByLabel("Email").fill(nobody);
  await page.getByRole("button", { name: "Send the link" }).click();

  await expect(page.getByRole("status")).toContainText(`If there is an account for ${nobody}`);
  await expect(alertIn(page)).toHaveCount(0);
});

test("changing the password logs out a browser that was logged in before", async ({ page, browser }) => {
  const customer = newCustomer();
  // One browser stays logged in: a laptop left open, or somebody who knew the old password.
  await register(page, customer);
  await page.goto("/bookings");
  await expect(page.getByRole("heading", { name: "My bookings" })).toBeVisible();

  // The password is changed from another one.
  const elsewhere = await browser.newContext({ ignoreHTTPSErrors: process.env.E2E_IGNORE_HTTPS_ERRORS === "true" });
  const other = await elsewhere.newPage();
  await other.goto("/forgot-password");
  await other.getByLabel("Email").fill(customer.email);
  await other.getByRole("button", { name: "Send the link" }).click();
  const link = new URL(await linkInEmailTo(customer.email, /^Set a new password/));
  await other.goto(link.pathname + link.search);
  await other.getByLabel("New password").fill(randomUUID());
  await other.getByRole("button", { name: "Set new password" }).click();
  await expect(other).toHaveURL("/login?reset=done");
  await elsewhere.close();

  // The first browser still has its cookie, but the session behind it has ended.
  await page.goto("/bookings");
  await expect(page).toHaveURL(/\/login/);
});

test("a link without its token says so instead of showing a form", async ({ page }) => {
  await page.goto("/reset-password");

  await expect(alertIn(page)).toContainText("This link is not complete.");
  await expect(page.getByLabel("New password")).toHaveCount(0);
});
