import type {
  DeveloperProgressField,
  DeveloperProgressPreset,
  ProgressDocumentResponse,
  ProgressValidationResponse,
} from "@studio/contracts/progress";
import { useEffect, useState } from "react";
import { ApiError, fetchProgress, saveProgress, validateProgress } from "../../shared/api";

const LAST_COMPLETE_LEVEL = 54;

export function ProgressTab() {
  const [document, setDocument] = useState<ProgressDocumentResponse | null>(null);
  const [preset, setPreset] = useState<DeveloperProgressPreset | null>(null);
  const [validation, setValidation] = useState<ProgressValidationResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const reload = () => {
    setBusy(true);
    setError(null);
    fetchProgress()
      .then(result => {
        const parsed = JSON.parse(result.content) as DeveloperProgressPreset;
        setDocument(result);
        setPreset(parsed);
        setValidation(null);
        setMessage(null);
      })
      .catch((cause: Error) => setError(cause.message))
      .finally(() => setBusy(false));
  };

  useEffect(reload, []);

  const update = (patch: Partial<DeveloperProgressPreset>) => {
    setPreset(current => current ? { ...current, ...patch } : current);
    setValidation(null);
    setError(null);
    setMessage(null);
  };

  const updateValue = (
    field: DeveloperProgressField,
    value: number,
  ) => {
    if (!preset) return;
    update({
      [field]: value,
      fieldsToApply: preset.fieldsToApply.includes(field)
        ? preset.fieldsToApply
        : [...preset.fieldsToApply, field],
      applyOnNextLaunch: true,
    });
  };

  const updateFieldSelection = (
    field: DeveloperProgressField,
    selected: boolean,
  ) => {
    if (!preset) return;
    const fields = new Set(preset.fieldsToApply);
    if (selected) fields.add(field);
    else fields.delete(field);
    update({
      fieldsToApply: [...fields],
      applyOnNextLaunch: selected
        ? true
        : fields.size > 0 && preset.applyOnNextLaunch,
    });
  };

  const content = preset ? `${JSON.stringify(preset, null, 2)}\n` : "";
  const dirty = document !== null && content !== document.content;

  const validate = async (): Promise<boolean> => {
    if (!preset) return false;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await validateProgress(content);
      setValidation(result);
      setMessage(result.valid ? "Progress preset is valid." : null);
      return result.valid;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    if (!document || !preset || !(await validate())) return;
    const nextContent = withIncrementedRevision(document.content, content);
    setBusy(true);
    setError(null);
    try {
      const result = await saveProgress({
        content: nextContent,
        expectedHash: document.hash,
      });
      setDocument(result);
      setPreset(JSON.parse(result.content) as DeveloperProgressPreset);
      setValidation({ valid: true, issues: [] });
      setMessage(
        preset.applyOnNextLaunch
          ? "Saved. The debug app applies this preset after rebuild and restart."
          : "Saved. It will not be applied until the next debug launch is queued.",
      );
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setError("The progress preset changed outside Studio. Reload it before saving again.");
      } else {
        setError(cause instanceof Error ? cause.message : String(cause));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="progress-editor" aria-label="Developer progress editor">
      <header className="progress-editor-header">
        <div>
          <h2>Player progress</h2>
          <p>Edits a debug preset. Applying it requires rebuilding and restarting the debug app.</p>
        </div>
        <div className="progress-actions">
          <button type="button" onClick={reload} disabled={busy}>Reload</button>
          <button type="button" onClick={() => void validate()} disabled={busy || !preset}>
            Validate
          </button>
          <button
            type="button"
            className="primary"
            onClick={() => void save()}
            disabled={busy || !preset || !dirty || validation?.valid !== true}
          >
            Save
          </button>
        </div>
      </header>
      {document && <p className="progress-hash">Preset SHA-256: <code>{document.hash}</code></p>}
      {error && <p className="error" role="alert">{error}</p>}
      {message && <p className="progress-success" role="status">{message}</p>}
      {validation && !validation.valid && (
        <ul className="progress-issues" aria-label="Progress validation errors">
          {validation.issues.map((item, index) => (
            <li key={`${item.path}-${index}`}><code>{item.path}</code>: {item.message}</li>
          ))}
        </ul>
      )}
      {!preset && !error && <p className="hint-text">Loading developer progress…</p>}
      {preset && (
        <div className="progress-fields">
          <div className="progress-value">
            <label htmlFor="progress-current-level">Current campaign level</label>
            <input
              id="progress-current-level"
              type="number"
              min={1}
              max={LAST_COMPLETE_LEVEL}
              value={preset.currentLevel}
              onChange={event => updateValue("currentLevel", Number(event.target.value))}
            />
            <small>Playable preset range: 1–{LAST_COMPLETE_LEVEL}. The selected level remains incomplete.</small>
            <label className="progress-field-apply">
              <input
                type="checkbox"
                checked={preset.fieldsToApply.includes("currentLevel")}
                onChange={event => updateFieldSelection("currentLevel", event.target.checked)}
              />
              Apply this value
            </label>
          </div>
          <div className="progress-value">
            <label htmlFor="progress-lives">Lives</label>
            <input
              id="progress-lives"
              type="number"
              min={0}
              value={preset.lives}
              onChange={event => updateValue("lives", Number(event.target.value))}
            />
            <small>Stored and displayed only; life consumption and regeneration are not implemented yet.</small>
            <label className="progress-field-apply">
              <input
                type="checkbox"
                checked={preset.fieldsToApply.includes("lives")}
                onChange={event => updateFieldSelection("lives", event.target.checked)}
              />
              Apply this value
            </label>
          </div>
          <div className="progress-value">
            <label htmlFor="progress-gold">Gold</label>
            <input
              id="progress-gold"
              type="number"
              min={0}
              value={preset.gold}
              onChange={event => updateValue("gold", Number(event.target.value))}
            />
            <label className="progress-field-apply">
              <input
                type="checkbox"
                checked={preset.fieldsToApply.includes("gold")}
                onChange={event => updateFieldSelection("gold", event.target.checked)}
              />
              Apply this value
            </label>
          </div>
          <label className="progress-apply">
            <input
              type="checkbox"
              checked={preset.applyOnNextLaunch}
              disabled={preset.fieldsToApply.length === 0 && !preset.applyOnNextLaunch}
              onChange={event => update({ applyOnNextLaunch: event.target.checked })}
            />
            Apply once on next debug app launch
          </label>
          {preset.fieldsToApply.length === 0 && (
            <small>Select at least one value above before queuing the preset.</small>
          )}
          <p className="progress-revision">Revision: <code>{preset.revision}</code></p>
        </div>
      )}
    </section>
  );
}

function withIncrementedRevision(currentContent: string, nextContent: string): string {
  const current = JSON.parse(currentContent) as DeveloperProgressPreset;
  const next = JSON.parse(nextContent) as DeveloperProgressPreset;
  if (current.revision !== next.revision) return nextContent;
  const match = /^(.*?)([-.])(\d+)$/.exec(next.revision);
  next.revision = match
    ? `${match[1]}${match[2]}${Number(match[3]) + 1}`
    : `${next.revision}-2`;
  return `${JSON.stringify(next, null, 2)}\n`;
}
