import { describe, expect, it } from "vitest";
import { tabIdFromHash } from "./tabs";

const tabs = [{ id: "maps" }, { id: "animations" }];

describe("tabIdFromHash", () => {
  it("selects a known tab", () => {
    expect(tabIdFromHash("#/animations", tabs)).toBe("animations");
    expect(tabIdFromHash("#animations", tabs)).toBe("animations");
  });

  it("falls back to the first tab for empty or unknown hashes", () => {
    expect(tabIdFromHash("", tabs)).toBe("maps");
    expect(tabIdFromHash("#/missing", tabs)).toBe("maps");
  });
});
