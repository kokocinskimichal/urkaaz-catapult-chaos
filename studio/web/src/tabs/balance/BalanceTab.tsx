import type {
  BalanceDocumentResponse,
  BalanceValidationResponse,
} from "@studio/contracts/balance";
import { useEffect, useState } from "react";
import { ApiError, fetchBalance, saveBalance, validateBalance } from "../../shared/api";

export function BalanceTab() {
  const [document, setDocument] = useState<BalanceDocumentResponse | null>(null);
  const [draft, setDraft] = useState("");
  const [validation, setValidation] = useState<BalanceValidationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const reload = () => {
    setBusy(true);
    setError(null);
    fetchBalance()
      .then(result => {
        setDocument(result);
        setDraft(result.content);
        setValidation(null);
        setMessage(null);
      })
      .catch((cause: Error) => setError(cause.message))
      .finally(() => setBusy(false));
  };

  useEffect(reload, []);

  const runValidation = async (): Promise<boolean> => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await validateBalance(draft);
      setValidation(result);
      setMessage(result.valid ? "Balance JSON is valid." : null);
      return result.valid;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    if (!document || !(await runValidation())) return;
    const content = withIncrementedRevision(document.content, draft);
    if (content !== draft) setDraft(content);
    setBusy(true);
    setError(null);
    try {
      const saved = await saveBalance({
        content,
        expectedHash: document.hash,
      });
      setDocument(saved);
      setDraft(saved.content);
      setValidation({ valid: true, issues: [] });
      setMessage("Saved. Rebuild and restart the game to apply the balance.");
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setError("The balance file changed on disk. Reload it before saving to avoid overwriting newer edits.");
      } else {
        setError(cause instanceof Error ? cause.message : String(cause));
      }
    } finally {
      setBusy(false);
    }
  };

  const dirty = document !== null && draft !== document.content;

  return (
    <section className="balance-editor" aria-label="Game balance editor">
      <header className="balance-editor-header">
        <div>
          <h2>Game balance</h2>
          <p>Edits the repository JSON. Changes apply after rebuilding and restarting the app.</p>
        </div>
        <div className="balance-actions">
          <button type="button" onClick={reload} disabled={busy}>Reload</button>
          <button type="button" onClick={() => void runValidation()} disabled={busy || !document}>
            Validate
          </button>
          <button
            type="button"
            className="primary"
            onClick={() => void save()}
            disabled={busy || !document || !dirty || validation?.valid !== true}
          >
            Save
          </button>
        </div>
      </header>
      {document && <p className="balance-revision">Current file SHA-256: <code>{document.hash}</code></p>}
      {error && <p className="error" role="alert">{error}</p>}
      {message && <p className="balance-success" role="status">{message}</p>}
      {validation && !validation.valid && (
        <ul className="balance-issues" aria-label="Balance validation errors">
          {validation.issues.map((item, index) => (
            <li key={`${item.path}-${index}`}><code>{item.path}</code>: {item.message}</li>
          ))}
        </ul>
      )}
      {!document && !error && <p className="hint-text">Loading game balance…</p>}
      {document && (
        <textarea
          aria-label="game-balance.json"
          className="balance-json"
          spellCheck={false}
          value={draft}
          onChange={event => {
            setDraft(event.target.value);
            setValidation(null);
            setMessage(null);
          }}
        />
      )}
    </section>
  );
}

function withIncrementedRevision(currentContent: string, draft: string): string {
  const current: unknown = JSON.parse(currentContent);
  const next: unknown = JSON.parse(draft);
  if (!isRecord(current) || !isRecord(next)) return draft;
  if (
    typeof current.balanceRevision !== "string" ||
    typeof next.balanceRevision !== "string" ||
    current.balanceRevision !== next.balanceRevision
  ) return draft;
  const match = /^(.*?)([-.])(\d+)$/.exec(next.balanceRevision);
  next.balanceRevision = match
    ? `${match[1]}${match[2]}${Number(match[3]) + 1}`
    : `${next.balanceRevision}-2`;
  return `${JSON.stringify(next, null, 2)}\n`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
