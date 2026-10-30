import { describe, expect, it } from "vitest";
import { tabIdFromHash, TABS } from "./tabs";

const tabs = [{ id: "maps" }, { id: "campaign" }, { id: "animations" }];

describe("tabIdFromHash", () => {
  it("selects a known tab", () => {
    expect(tabIdFromHash("#/animations", tabs)).toBe("animations");
    expect(tabIdFromHash("#animations", tabs)).toBe("animations");
  });

  it("registers the Campaign editor tab", () => {
    expect(TABS.map(tab => tab.id)).toContain("campaign");
    expect(tabIdFromHash("#/campaign")).toBe("campaign");
  });

  it("falls back to the first tab for empty or unknown hashes", () => {
    expect(tabIdFromHash("", tabs)).toBe("maps");
    expect(tabIdFromHash("#/missing", tabs)).toBe("maps");
  });
});
