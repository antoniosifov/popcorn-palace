import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { DeleteAccountState } from "@/actions/auth";

import { DeleteAccount } from "./delete-account";

// The Server Action is the edge of this component: it is replaced, everything else is real.
const actions = vi.hoisted(() => ({
  deleteAccount: vi.fn<(previous: DeleteAccountState, form: FormData) => Promise<DeleteAccountState>>(),
}));

vi.mock("@/actions/auth", () => actions);

beforeEach(() => {
  actions.deleteAccount.mockReset();
});

describe("DeleteAccount", () => {
  it("asks once more before anything is sent, and can be backed out of", async () => {
    const user = userEvent.setup();
    render(<DeleteAccount hasPassword />);

    expect(screen.queryByLabelText("Your password")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Delete account" }));

    expect(screen.getByText("Delete this account for good? It cannot be brought back.")).toBeDefined();
    expect(screen.getByLabelText("Your password")).toBeDefined();
    await user.click(screen.getByRole("button", { name: "Keep it" }));

    expect(screen.getByRole("button", { name: "Delete account" })).toBeDefined();
    expect(actions.deleteAccount).not.toHaveBeenCalled();
  });

  it("sends the password, and shows the refusal when it is wrong", async () => {
    actions.deleteAccount.mockResolvedValue({
      error: { code: "PASSWORD_INCORRECT", message: "That is not the password of this account." },
    });
    const user = userEvent.setup();
    render(<DeleteAccount hasPassword />);

    await user.click(screen.getByRole("button", { name: "Delete account" }));
    await user.type(screen.getByLabelText("Your password"), "not-the-password");
    await user.click(screen.getByRole("button", { name: "Delete for good" }));

    expect((await screen.findByRole("alert")).textContent).toContain("That is not the password of this account.");
    expect(actions.deleteAccount).toHaveBeenCalledTimes(1);
    expect(actions.deleteAccount.mock.calls[0][1].get("password")).toBe("not-the-password");
    // Still the question, with the form ready for another try.
    expect(screen.getByRole("button", { name: "Delete for good" })).toBeDefined();
  });

  it("does not ask an account opened with Google for a password it does not have", async () => {
    actions.deleteAccount.mockResolvedValue({});
    const user = userEvent.setup();
    render(<DeleteAccount hasPassword={false} />);

    await user.click(screen.getByRole("button", { name: "Delete account" }));

    expect(screen.queryByLabelText("Your password")).toBeNull();
    await user.click(screen.getByRole("button", { name: "Delete for good" }));
    expect(actions.deleteAccount).toHaveBeenCalledTimes(1);
    expect(actions.deleteAccount.mock.calls[0][1].get("password")).toBeNull();
  });
});
