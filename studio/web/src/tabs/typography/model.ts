import type {
  ButtonTypographyEntry,
  ButtonTypographyUpdate,
  TypographyCategory,
  TypographyLanguage,
} from "@studio/contracts/typography";
import { TYPOGRAPHY_CATEGORIES } from "@studio/contracts/typography";

export interface TypographyDraftEntry {
  controlId: string;
  category: TypographyCategory;
  stringKey: string;
  labels: Record<TypographyLanguage, string>;
  fontSizeSp: Record<TypographyLanguage, string>;
}

export interface TypographyDraftIssue {
  severity: "error";
  path: string;
  message: string;
}

export type TypographyDraftMaterialization =
  | { valid: true; entries: ButtonTypographyUpdate[] }
  | { valid: false; issues: TypographyDraftIssue[] };

export const TYPOGRAPHY_LANGUAGES: readonly TypographyLanguage[] = ["en", "pl", "es"];
export const TYPOGRAPHY_CATEGORY_LABELS: Record<TypographyCategory, string> = {
  common: "Common",
  dashboard: "Dashboard",
  campaign: "Campaign",
  loadout: "Loadout",
  shop: "Shop",
  match: "Match",
  tutorial: "Tutorial",
  test: "Test mode",
};

export function toTypographyDraft(entries: readonly ButtonTypographyEntry[]): TypographyDraftEntry[] {
  return entries.map(entry => ({
    controlId: entry.controlId,
    category: entry.category,
    stringKey: entry.stringKey,
    labels: { ...entry.labels },
    fontSizeSp: {
      en: String(entry.fontSizeSp.en),
      pl: String(entry.fontSizeSp.pl),
      es: String(entry.fontSizeSp.es),
    },
  }));
}

export function setTypographyDraftSize(
  draft: readonly TypographyDraftEntry[],
  controlId: string,
  language: TypographyLanguage,
  value: string,
): TypographyDraftEntry[] {
  let updated = false;
  const next = draft.map(entry => {
    if (entry.controlId !== controlId) return entry;
    updated = true;
    return {
      ...entry,
      fontSizeSp: { ...entry.fontSizeSp, [language]: value },
    };
  });
  if (!updated) throw new RangeError(`Unknown button typography control: ${controlId}.`);
  return next;
}

export function resetTypographyDraftSize(
  draft: readonly TypographyDraftEntry[],
  baseline: readonly ButtonTypographyEntry[],
  controlId: string,
  language: TypographyLanguage,
): TypographyDraftEntry[] {
  const baselineEntry = baseline.find(entry => entry.controlId === controlId);
  if (!baselineEntry) throw new RangeError(`Unknown button typography control: ${controlId}.`);
  return setTypographyDraftSize(
    draft,
    controlId,
    language,
    String(baselineEntry.fontSizeSp[language]),
  );
}

export function materializeTypographyDraft(
  draft: readonly TypographyDraftEntry[],
): TypographyDraftMaterialization {
  const issues: TypographyDraftIssue[] = [];
  const entries = draft.map((entry, index) => {
    const fontSizeSp = {} as Record<TypographyLanguage, number>;
    for (const language of TYPOGRAPHY_LANGUAGES) {
      const path = `entries[${index}].fontSizeSp.${language}`;
      const value = Number(entry.fontSizeSp[language].trim());
      if (
        entry.fontSizeSp[language].trim() === "" ||
        !Number.isFinite(value) ||
        value < 8 ||
        value > 32
      ) {
        issues.push({
          severity: "error",
          path,
          message: "Enter a finite font size between 8 and 32 sp.",
        });
      } else {
        fontSizeSp[language] = value;
      }
    }
    return { controlId: entry.controlId, fontSizeSp };
  });
  return issues.length > 0
    ? { valid: false, issues }
    : { valid: true, entries };
}

export function typographyDraftIsDirty(
  draft: readonly TypographyDraftEntry[],
  baseline: readonly ButtonTypographyEntry[],
): boolean {
  return JSON.stringify(draft) !== JSON.stringify(toTypographyDraft(baseline));
}

export function groupTypographyEntries(
  entries: readonly TypographyDraftEntry[],
): Array<{ category: TypographyCategory; entries: TypographyDraftEntry[] }> {
  return TYPOGRAPHY_CATEGORIES
    .map(category => ({
      category,
      entries: entries.filter(entry => entry.category === category),
    }))
    .filter(group => group.entries.length > 0);
}

export function previewTypographyLabel(
  entry: Pick<TypographyDraftEntry, "controlId" | "labels">,
  language: TypographyLanguage,
): string {
  const argumentsByControl: Partial<Record<string, readonly (number | string)[]>> = {
    test_level: [1],
    test_boss_level: [1],
    loadout_card_stock: [3],
    loadout_card_gold_cost: [100],
    shop_offer_price: [100],
    shop_offer_stock: [3],
    match_ammo_stock: [3],
    match_ammo_reload_seconds: [1.5],
    match_debug_recruit_enemy: [{
      en: "DEFENDER",
      pl: "OBROŃCA",
      es: "DEFENSOR",
    }[language]],
  };
  const args = argumentsByControl[entry.controlId] ?? [];
  let nextArgument = 0;
  const locale = { en: "en-US", pl: "pl-PL", es: "es-ES" }[language];
  return entry.labels[language].replace(
    /%%|%(?:(\d+)\$)?(?:\.(\d+))?([sdf])/g,
    (placeholder, position: string | undefined, precision: string | undefined, type: string) => {
      if (placeholder === "%%") return "%";
      const index = position ? Number(position) - 1 : nextArgument++;
      const value = args[index] ?? (type === "s" ? "" : 1);
      if (type === "s") return String(value);
      if (type === "d") return String(Math.round(Number(value)));
      const digits = precision === undefined ? 1 : Number(precision);
      return new Intl.NumberFormat(locale, {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
      }).format(Number(value));
    },
  );
}
