import type {
  BalanceDocumentResponse,
  BalanceValidationResponse,
} from "@studio/contracts/balance";
import type { CampaignChapterCatalogEntry } from "@studio/contracts/campaign";
import { useEffect, useState } from "react";
import {
  ApiError,
  fetchBalance,
  fetchCampaignChapters,
  saveBalance,
  validateBalance,
} from "../../shared/api";
import { BalanceForm } from "./BalanceForm";
import {
  balanceDocumentMatches,
  materializeBalanceDraft,
  parseBalanceDocument,
  serializeBalanceDocument,
  toBalanceFormDraft,
  withIncrementedBalanceRevision,
} from "./model";
import type {
  BalanceFormDraft,
  GameBalanceDocumentV7,
} from "./model";
import "./styles.css";

export function BalanceTab() {
  const [document, setDocument] = useState<BalanceDocumentResponse | null>(null);
  const [baseline, setBaseline] = useState<GameBalanceDocumentV7 | null>(null);
  const [draft, setDraft] = useState<BalanceFormDraft<GameBalanceDocumentV7> | null>(null);
  const [validation, setValidation] = useState<BalanceValidationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [needsReload, setNeedsReload] = useState(false);
  const [chapters, setChapters] = useState<CampaignChapterCatalogEntry[]>([]);
  const [chapterCatalogError, setChapterCatalogError] = useState<string | null>(null);

  const dirty = draft !== null && baseline !== null && isDraftDirty(draft, baseline);

  const reload = async (confirmDiscard = true) => {
    if (confirmDiscard && dirty &&
      !window.confirm("Discard your unsaved balance changes and reload the repository file?")
    ) return;

    setBusy(true);
    setError(null);
    setMessage(null);
    setNeedsReload(false);
    try {
      const loaded = await fetchBalance();
      const checked = await validateBalance(loaded.content);
      if (!checked.valid) {
        setValidation(checked);
        setNeedsReload(true);
        setError("The repository balance file is invalid. The current form was left unchanged.");
        return;
      }

      const parsed = parseBalanceDocument(loaded.content);
      setDocument(loaded);
      setBaseline(parsed);
      setDraft(toBalanceFormDraft(parsed));
      setValidation(null);
      setMessage("Loaded schema 7 balance.");
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    void reload(false);
  }, []);

  useEffect(() => {
    let cancelled = false;
    fetchCampaignChapters()
      .then(result => {
        if (cancelled) return;
        setChapters(result.chapters);
        setChapterCatalogError(null);
      })
      .catch(cause => {
        if (!cancelled) {
          setChapters([]);
          setChapterCatalogError(errorMessage(cause));
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const createSaveCandidate = () => {
    if (!draft || !baseline) return null;
    const materialized = materializeBalanceDraft(draft);
    if (!materialized.valid) {
      setValidation({ valid: false, issues: materialized.issues });
      return null;
    }
    const revised = withIncrementedBalanceRevision(baseline, materialized.document);
    return {
      document: revised,
      content: serializeBalanceDocument(revised),
    };
  };

  const validateDraft = async (): Promise<string | null> => {
    if (!document || !baseline || !draft || needsReload) return null;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const candidate = createSaveCandidate();
      if (!candidate) return null;
      const result = await validateBalance(candidate.content);
      setValidation(result);
      setMessage(result.valid ? "Balance is valid." : null);
      return result.valid ? candidate.content : null;
    } catch (cause) {
      setValidation(null);
      setError(errorMessage(cause));
      return null;
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    if (!document || !baseline || !draft || !dirty || needsReload) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const candidate = createSaveCandidate();
      if (!candidate) return;
      const checked = await validateBalance(candidate.content);
      setValidation(checked);
      if (!checked.valid) return;

      const saved = await saveBalance({
        content: candidate.content,
        expectedHash: document.hash,
      });
      const parsed = parseBalanceDocument(saved.content);
      setDocument(saved);
      setBaseline(parsed);
      setDraft(toBalanceFormDraft(parsed));
      setValidation({ valid: true, issues: [] });
      setNeedsReload(false);
      setMessage("Saved. Rebuild and restart the game to apply the balance.");
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setNeedsReload(true);
        setError(
          "The balance file changed on disk. Reload it before saving to avoid overwriting newer edits.",
        );
      } else {
        setError(errorMessage(cause));
      }
    } finally {
      setBusy(false);
    }
  };

  const onDraftChange = (next: BalanceFormDraft<GameBalanceDocumentV7>) => {
    setDraft(next);
    setValidation(null);
    setError(null);
    setMessage(null);
  };

  const validationButtonLabel = busy ? "Working…" : "Validate";

  return (
    <section className="balance-editor" aria-label="Game balance editor">
      <header className="balance-editor-header">
        <div>
          <h2>Game balance</h2>
          <p>
            Edit schema 7 profiles using the form. Changes apply after rebuilding
            and restarting the app.
          </p>
        </div>
        <div className="balance-actions">
          <button type="button" onClick={() => void reload()} disabled={busy}>
            Reload
          </button>
          <button
            type="button"
            onClick={() => void validateDraft()}
            disabled={busy || !document || !draft || needsReload}
          >
            {validationButtonLabel}
          </button>
          <button
            type="button"
            className="primary"
            onClick={() => void save()}
            disabled={
              busy ||
              !document ||
              !draft ||
              !dirty ||
              needsReload ||
              validation?.valid !== true
            }
          >
            Save
          </button>
        </div>
      </header>

      {document && baseline && (
        <div className="balance-metadata" aria-label="Balance file metadata">
          <span>Schema version <strong>{baseline.schemaVersion}</strong></span>
          <span>Revision <code>{baseline.balanceRevision}</code></span>
          <span>SHA-256 <code>{document.hash}</code></span>
          {dirty && <span className="balance-dirty-indicator">Unsaved changes</span>}
        </div>
      )}
      {error && <p className="error" role="alert">{error}</p>}
      {message && <p className="balance-success" role="status">{message}</p>}
      {!document && !error && <p className="hint-text">Loading game balance…</p>}
      {validation && !validation.valid && !draft && (
        <ul className="balance-issues" aria-label="Balance file validation errors">
          {validation.issues.map((issue, index) => (
            <li key={`${issue.path}-${index}`}>
              <code>{issue.path || "gameBalance"}</code>: {issue.message}
            </li>
          ))}
        </ul>
      )}
      {draft && (
        <fieldset className="balance-form-lock" disabled={busy || needsReload}>
          <BalanceForm
            key={document?.hash ?? "balance-form"}
            draft={draft}
            validation={validation}
            chapters={chapters}
            chapterCatalogError={chapterCatalogError}
            onChange={onDraftChange}
          />
        </fieldset>
      )}
    </section>
  );
}

function isDraftDirty(
  draft: BalanceFormDraft<GameBalanceDocumentV7>,
  baseline: GameBalanceDocumentV7,
): boolean {
  const result = materializeBalanceDraft(draft);
  return !result.valid || !balanceDocumentMatches(result.document, baseline);
}

function errorMessage(cause: unknown): string {
  return cause instanceof Error ? cause.message : String(cause);
}
