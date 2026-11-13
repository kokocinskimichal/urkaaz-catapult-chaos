import { describe, expect, it } from "vitest";
import {
  groupTypographyEntries,
  materializeTypographyDraft,
  previewTypographyLabel,
  resetTypographyDraftSize,
  setTypographyDraftSize,
  toTypographyDraft,
  typographyDraftIsDirty,
} from "./model";
import type {
  ButtonTypographyEntry,
} from "@studio/contracts/typography";
import type { TypographyDraftEntry } from "./model";

const entries: ButtonTypographyEntry[] = [{
  controlId: "dashboard_test_mode",
  category: "dashboard",
  stringKey: "test_mode",
  labels: { en: "TEST MODE", pl: "TRYB TESTOWY", es: "MODO DE PRUEBA" },
  fontSizeSp: { en: 14, pl: 11, es: 10 },
}];

describe("button typography form model", () => {
  it("keeps per-language font sizes independent and materializes decimal values", () => {
    const changed = setTypographyDraftSize(
      toTypographyDraft(entries),
      "dashboard_test_mode",
      "es",
      "9.5",
    );
    const result = materializeTypographyDraft(changed);

    expect(result).toEqual({
      valid: true,
      entries: [{
        controlId: "dashboard_test_mode",
        fontSizeSp: { en: 14, pl: 11, es: 9.5 },
      }],
    });
    expect(changed[0]?.labels).toEqual(entries[0]?.labels);
  });

  it("restores one language to its loaded baseline", () => {
    const changed = setTypographyDraftSize(
      toTypographyDraft(entries),
      "dashboard_test_mode",
      "pl",
      "10",
    );
    const restored = resetTypographyDraftSize(
      changed,
      entries,
      "dashboard_test_mode",
      "pl",
    );

    expect(typographyDraftIsDirty(restored, entries)).toBe(false);
  });

  it("rejects incomplete and out-of-range drafts", () => {
    const blank = setTypographyDraftSize(
      toTypographyDraft(entries),
      "dashboard_test_mode",
      "es",
      "",
    );
    const invalid = materializeTypographyDraft(blank);
    expect(invalid.valid).toBe(false);
    if (invalid.valid) return;
    expect(invalid.issues).toContainEqual(expect.objectContaining({
      path: "entries[0].fontSizeSp.es",
    }));

    const oversized = setTypographyDraftSize(
      toTypographyDraft(entries),
      "dashboard_test_mode",
      "en",
      "40",
    );
    expect(materializeTypographyDraft(oversized).valid).toBe(false);
  });

  it("groups shared and screen-specific controls in the configured order", () => {
    const draft = toTypographyDraft([
      { ...entries[0]!, controlId: "dashboard_test_mode" },
      { ...entries[0]!, controlId: "common_loadout_back", category: "common" },
      { ...entries[0]!, controlId: "shop_buy", category: "shop" },
    ]);

    expect(groupTypographyEntries(draft).map(group => [
      group.category,
      group.entries.map(entry => entry.controlId),
    ])).toEqual([
      ["common", ["common_loadout_back"]],
      ["dashboard", ["dashboard_test_mode"]],
      ["shop", ["shop_buy"]],
    ]);
  });

  it("formats Android placeholders for useful locale-specific previews", () => {
    const stockEntry: TypographyDraftEntry = {
      ...toTypographyDraft(entries)[0]!,
      controlId: "loadout_card_gold_cost",
      stringKey: "loadout_prop_gold_cost",
      labels: {
        en: "COST: %1$d GOLD",
        pl: "KOSZT: %1$d SZT. ZŁOTA",
        es: "COSTE: %1$d ORO",
      },
    };
    const reloadEntry: TypographyDraftEntry = {
      ...toTypographyDraft(entries)[0]!,
      controlId: "match_ammo_reload_seconds",
      stringKey: "match_hud_reload_seconds",
      labels: { en: "%1$.1f s", pl: "%1$.1f s", es: "%1$.1f s" },
    };

    expect(previewTypographyLabel(stockEntry, "pl")).toBe("KOSZT: 100 SZT. ZŁOTA");
    expect(previewTypographyLabel(reloadEntry, "es")).toBe("1,5 s");
  });
});
