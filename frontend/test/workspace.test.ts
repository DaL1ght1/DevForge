import { describe, expect, it } from "vitest";
import { githubCallbackMessage } from "../src/lib/githubCallback";
import { hasRole } from "../src/lib/workspaceRoles";

describe("hasRole", () => {
  it("orders roles OWNER > ADMIN > MEMBER", () => {
    expect(hasRole({ role: "OWNER" }, "ADMIN")).toBe(true);
    expect(hasRole({ role: "ADMIN" }, "ADMIN")).toBe(true);
    expect(hasRole({ role: "MEMBER" }, "ADMIN")).toBe(false);
    expect(hasRole({ role: "ADMIN" }, "OWNER")).toBe(false);
    expect(hasRole({ role: "MEMBER" }, "MEMBER")).toBe(true);
  });

  it("denies when there is no workspace", () => {
    expect(hasRole(null, "MEMBER")).toBe(false);
    expect(hasRole(undefined, "MEMBER")).toBe(false);
  });
});

describe("githubCallbackMessage", () => {
  it("ignores pages without a GitHub result", () => {
    expect(githubCallbackMessage(null, null)).toBeNull();
    expect(githubCallbackMessage("something-else", null)).toBeNull();
  });

  it("reports success results", () => {
    expect(githubCallbackMessage("linked", null)?.tone).toBe("success");
    expect(githubCallbackMessage("authorized", null)?.tone).toBe("success");
  });

  it("explains every backend error reason", () => {
    const reasons = [
      "invalid_state",
      "access_denied",
      "authorization_required",
      "conflict",
      "forbidden",
      "wrong_account_type",
      "authorization_failed",
    ];
    const generic = githubCallbackMessage("error", "unknown_reason")?.text;
    for (const reason of reasons) {
      const message = githubCallbackMessage("error", reason);
      expect(message?.tone).toBe("error");
      expect(message?.text).not.toBe(generic);
    }
  });
});
