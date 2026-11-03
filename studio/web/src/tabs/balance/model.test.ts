import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { beforeAll, describe, expect, it } from "vitest";
import { resolveRepoRoot } from "../../../../server/src/services/repoRoot.ts";
import {
  balanceDocumentMatches,
  createAmmunitionBehaviorDraft,
  materializeBalanceDraft,
  parseBalanceDocument,
  serializeBalanceDocument,
  setBalanceDraftValue,
  toBalanceFormDraft,
  withIncrementedBalanceRevision,
} from "./model";
import type { GameBalanceDocumentV5 } from "./model";
import type { BalanceFormDraft } from "./model";

let originalContent: string;
let original: GameBalanceDocumentV5;

beforeAll(async () => {
  const root = await resolveRepoRoot();
  originalContent = await readFile(
    join(root, "game-domain/src/main/resources/balance/game-balance.json"),
    "utf8",
  );
  original = parseBalanceDocument(originalContent);
});

it("preserves nullable shop prices and materializes an enabled price", () => {
  const ammunitionIndex = original.ammunition.findIndex(
    profile => profile.type === "POWDER_BARREL",
  );
  expect(ammunitionIndex).toBeGreaterThanOrEqual(0);
  const path = `ammunition[${ammunitionIndex}].shopCostGold`;

  const unavailable = setBalanceDraftValue(
    toBalanceFormDraft(original),
    path,
    null,
  );
  const unavailableDocument = materializeBalanceDraft(unavailable);
  expect(unavailableDocument.valid).toBe(true);
  if (!unavailableDocument.valid) return;
  expect(unavailableDocument.document.ammunition[ammunitionIndex]?.shopCostGold).toBe(null);

  const available = setBalanceDraftValue(
    toBalanceFormDraft(original),
    path,
    { __numberInputDraft: true, text: "0" },
  );
  const availableDocument = materializeBalanceDraft(available);
  expect(availableDocument.valid).toBe(true);
  if (!availableDocument.valid) return;
  expect(availableDocument.document.ammunition[ammunitionIndex]?.shopCostGold).toBe(0);
});

describe("Balance form model", () => {
  it("round-trips the complete checked-in schema 5 document", () => {
    const form = toBalanceFormDraft(original);
    const materialized = materializeBalanceDraft(form);

    expect(materialized.valid).toBe(true);
    if (!materialized.valid) return;
    expect(JSON.parse(serializeBalanceDocument(materialized.document))).toEqual(original);
  });

  it("rejects unknown schemas and unsupported root fields", () => {
    expect(() => parseBalanceDocument(JSON.stringify({ schemaVersion: 4 })))
      .toThrow("Unsupported balance schema version");

    const invalid = JSON.parse(originalContent) as Record<string, unknown>;
    invalid.unexpected = true;
    expect(() => parseBalanceDocument(JSON.stringify(invalid)))
      .toThrow("Unsupported balance document field: unexpected");
  });

  it("keeps an empty numeric input invalid instead of converting it to zero", () => {
    const form = setBalanceDraftValue(
      toBalanceFormDraft(original),
      "units[0].supplyCost",
      { __numberInputDraft: true, text: "" },
    );
    const result = materializeBalanceDraft(form);

    expect(result.valid).toBe(false);
    if (result.valid) return;
    expect(result.issues).toContainEqual(expect.objectContaining({
      path: "units[0].supplyCost",
      message: "Enter a complete finite number.",
    }));
  });

  it("requires values after switching an ammunition behavior variant", () => {
    const form = setBalanceDraftValue(
      toBalanceFormDraft(original),
      "ammunition[0].behavior",
      createAmmunitionBehaviorDraft("EXPLOSION"),
    );
    const incomplete = materializeBalanceDraft(form);
    expect(incomplete.valid).toBe(false);

    const complete = setBalanceFormNumbers(form, {
      "ammunition[0].behavior.radius": "100",
      "ammunition[0].behavior.lifetimeSeconds": "0.5",
    });
    const materialized = materializeBalanceDraft(complete);
    expect(materialized.valid).toBe(true);
    if (!materialized.valid) return;
    expect(materialized.document.ammunition[0]?.behavior).toEqual({
      kind: "EXPLOSION",
      radius: 100,
      lifetimeSeconds: 0.5,
    });
  });

  it("can edit an optional lingering-area status when the source omits the key", () => {
    const source = JSON.parse(originalContent) as Record<string, unknown>;
    const ammunition = source.ammunition as Array<Record<string, unknown>>;
    const fireRain = ammunition.find(profile => profile.type === "FIRE_RAIN");
    const behavior = fireRain?.behavior as Record<string, unknown> | undefined;
    expect(behavior).toBeDefined();
    delete behavior!.status;

    const withoutStatus = parseBalanceDocument(JSON.stringify(source));
    const draft = setBalanceDraftValue(
      toBalanceFormDraft(withoutStatus),
      "ammunition[3].behavior.status",
      "BURNING",
    );
    const result = materializeBalanceDraft(draft);

    expect(result.valid).toBe(true);
    if (!result.valid) return;
    expect(result.document.ammunition[3]?.behavior).toMatchObject({ status: "BURNING" });
  });

  it("increments an unchanged balance revision only for a saved edit", () => {
    const edited = {
      ...original,
      units: original.units.map((unit, index) =>
        index === 0 ? { ...unit, supplyCost: unit.supplyCost + 1 } : unit,
      ),
    };
    const revised = withIncrementedBalanceRevision(original, edited);

    expect(revised.balanceRevision).toBe("game-balance-8");
    expect(balanceDocumentMatches(original, original)).toBe(true);
  });
});

function setBalanceFormNumbers(
  form: BalanceFormDraft<GameBalanceDocumentV5>,
  fields: Record<string, string>,
) {
  return Object.entries(fields).reduce(
    (current, [path, text]) => setBalanceDraftValue(
      current,
      path,
      { __numberInputDraft: true, text },
    ),
    form,
  );
}
