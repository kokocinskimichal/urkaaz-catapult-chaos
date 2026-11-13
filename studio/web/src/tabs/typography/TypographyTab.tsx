import { useEffect, useState } from "react";
import type {
  ButtonTypographyEntry,
  TypographyCategory,
  TypographyLanguage,
  TypographyValidationIssue,
} from "@studio/contracts/typography";
import {
  ApiError,
  fetchButtonTypography,
  saveButtonTypography,
  validateButtonTypography,
} from "../../shared/api";
import {
  groupTypographyEntries,
  materializeTypographyDraft,
  previewTypographyLabel,
  resetTypographyDraftSize,
  setTypographyDraftSize,
  toTypographyDraft,
  typographyDraftIsDirty,
  TYPOGRAPHY_CATEGORY_LABELS,
} from "./model";
import type { TypographyDraftEntry, TypographyDraftIssue } from "./model";
import "./styles.css";

const LOCALES: readonly {
  id: TypographyLanguage;
  label: string;
}[] = [
  { id: "en", label: "English" },
  { id: "pl", label: "Polski" },
  { id: "es", label: "Español" },
];

type TypographyIssue = TypographyValidationIssue | TypographyDraftIssue;

export function TypographyTab() {
  const [baseline, setBaseline] = useState<ButtonTypographyEntry[] | null>(null);
  const [expandedCategories, setExpandedCategories] = useState<Set<TypographyCategory>>(
    () => new Set(["common"]),
  );
  const [hash, setHash] = useState<string | null>(null);
  const [draft, setDraft] = useState<TypographyDraftEntry[] | null>(null);
  const [validation, setValidation] = useState<{
    valid: boolean;
    issues: TypographyIssue[];
  } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const dirty = draft !== null && baseline !== null && typographyDraftIsDirty(draft, baseline);
  const groups = draft ? groupTypographyEntries(draft) : [];

  const reload = async (confirmDiscard = true) => {
    if (
      confirmDiscard &&
      dirty &&
      !window.confirm("Discard your unsaved button typography changes and reload?")
    ) return;

    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const loaded = await fetchButtonTypography();
      setBaseline(loaded.entries);
      setHash(loaded.hash);
      setDraft(toTypographyDraft(loaded.entries));
      setValidation(null);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    void reload(false);
  }, []);

  const errorFor = (controlId: string, language: TypographyLanguage): string | undefined => {
    const index = draft?.findIndex(entry => entry.controlId === controlId) ?? -1;
    if (index < 0) return undefined;
    const path = `entries[${index}].fontSizeSp.${language}`;
    return validation?.issues.find(issue => issue.path === path)?.message;
  };

  const update = (controlId: string, language: TypographyLanguage, value: string) => {
    setDraft(current => current
      ? setTypographyDraftSize(current, controlId, language, value)
      : current);
    setValidation(null);
    setError(null);
    setMessage(null);
  };

  const validate = async (): Promise<boolean> => {
    if (!draft) return false;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const materialized = materializeTypographyDraft(draft);
      if (!materialized.valid) {
        setValidation({ valid: false, issues: materialized.issues });
        return false;
      }
      const result = await validateButtonTypography(materialized.entries);
      setValidation(result);
      setMessage(result.valid ? "Button typography is valid." : null);
      return result.valid;
    } catch (cause) {
      setError(errorMessage(cause));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    if (!baseline || !draft || !hash || !dirty || validation?.valid !== true) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const materialized = materializeTypographyDraft(draft);
      if (!materialized.valid) {
        setValidation({ valid: false, issues: materialized.issues });
        return;
      }
      const saved = await saveButtonTypography({
        entries: materialized.entries,
        expectedHash: hash,
      });
      setBaseline(saved.entries);
      setHash(saved.hash);
      setDraft(toTypographyDraft(saved.entries));
      setValidation({ valid: true, issues: [] });
      setMessage("Saved. Rebuild and restart the app to apply typography resources.");
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setError("Typography resources changed outside Studio. Reload before saving.");
      } else {
        setError(errorMessage(cause));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="typography-editor" aria-label="Interactive control typography editor">
      <header className="typography-editor-header">
        <div>
          <h2>Interactive control typography</h2>
          <p>
            Tune localized interactive labels independently for English, Polish
            and Spanish. Translation text and control layout are read-only.
          </p>
        </div>
        <div className="typography-actions">
          <button type="button" onClick={() => void reload()} disabled={busy}>
            Reload
          </button>
          <button
            type="button"
            onClick={() => void validate()}
            disabled={busy || !draft}
          >
            Validate
          </button>
          <button
            type="button"
            className="primary"
            onClick={() => void save()}
            disabled={busy || !dirty || validation?.valid !== true}
          >
            Save
          </button>
        </div>
      </header>
      {hash && (
        <p className="typography-hash">Resources SHA-256: <code>{hash}</code></p>
      )}
      {error && <p className="error" role="alert">{error}</p>}
      {message && <p className="typography-success" role="status">{message}</p>}
      {validation && !validation.valid && (
        <ul className="typography-issues" aria-label="Typography validation errors">
          {validation.issues.map((issue, index) => (
            <li key={`${issue.path}-${index}`}>
              <code>{issue.path}</code>: {issue.message}
            </li>
          ))}
        </ul>
      )}
      {!draft && !error && <p className="hint-text">Loading button typography…</p>}
      {draft && baseline && (
        <fieldset className="typography-form" disabled={busy}>
          {groups.map(group => (
            <details
              className="typography-category"
              key={group.category}
              open={expandedCategories.has(group.category)}
              onToggle={event => {
                const isOpen = event.currentTarget.open;
                setExpandedCategories(current => {
                  const next = new Set(current);
                  if (isOpen) next.add(group.category);
                  else next.delete(group.category);
                  return next;
                });
              }}
            >
              <summary>
                <span>{TYPOGRAPHY_CATEGORY_LABELS[group.category]}</span>
                <small>{group.entries.length} controls</small>
              </summary>
              <div
                className="typography-table"
                role="table"
                aria-label={`${TYPOGRAPHY_CATEGORY_LABELS[group.category]} label sizes`}
              >
                <div className="typography-table-header" role="row">
                  <span role="columnheader">Control / string resource</span>
                  {LOCALES.map(locale => (
                    <span key={locale.id} role="columnheader">{locale.label}</span>
                  ))}
                </div>
                {group.entries.map(entry => (
                  <div className="typography-row" role="row" key={entry.controlId}>
                    <span className="typography-control-id" role="rowheader">
                      <code>{entry.controlId}</code>
                      <small>{entry.stringKey}</small>
                    </span>
                    {LOCALES.map(locale => {
                      const baselineEntry = baseline.find(
                        item => item.controlId === entry.controlId,
                      );
                      const currentSize = Number(entry.fontSizeSp[locale.id]);
                      const previewSize = Number.isFinite(currentSize) ? currentSize : 14;
                      const error = errorFor(entry.controlId, locale.id);
                      return (
                        <div className="typography-locale-cell" role="cell" key={locale.id}>
                          <button
                            type="button"
                            className="typography-preview-button"
                            style={{ fontSize: `${previewSize}px` }}
                            disabled
                          >
                            {previewTypographyLabel(entry, locale.id)}
                          </button>
                          <label>
                            <span>Font size (sp)</span>
                            <input
                              type="number"
                              inputMode="decimal"
                              min={8}
                              max={32}
                              step={0.1}
                              value={entry.fontSizeSp[locale.id]}
                              aria-label={`${entry.controlId} ${locale.label} font size`}
                              aria-invalid={error ? "true" : "false"}
                              onChange={event => update(
                                entry.controlId,
                                locale.id,
                                event.currentTarget.value,
                              )}
                            />
                          </label>
                          {error && (
                            <small className="typography-error" role="alert">{error}</small>
                          )}
                          {baselineEntry &&
                            entry.fontSizeSp[locale.id] !== String(baselineEntry.fontSizeSp[locale.id]) && (
                              <button
                                type="button"
                                className="typography-reset"
                                onClick={() => {
                                  setDraft(current => current
                                    ? resetTypographyDraftSize(
                                      current,
                                      baseline,
                                      entry.controlId,
                                      locale.id,
                                    )
                                    : current);
                                  setValidation(null);
                                  setError(null);
                                  setMessage(null);
                                }}
                              >
                                Use baseline
                              </button>
                            )}
                        </div>
                      );
                    })}
                  </div>
                ))}
              </div>
            </details>
          ))}
          <p className="typography-preview-note">
            Browser preview is approximate; Android `sp` sizing is authoritative.
          </p>
        </fieldset>
      )}
    </section>
  );
}

function errorMessage(cause: unknown): string {
  return cause instanceof Error ? cause.message : String(cause);
}
