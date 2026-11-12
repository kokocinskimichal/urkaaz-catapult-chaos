import type {
  TutorialCatalog,
  TutorialDefinition,
  TutorialLanguage,
  TutorialPlayerActionKind,
  TutorialScreen,
  TutorialScope,
  TutorialStep,
  TutorialStepCompletion,
  TutorialTrigger,
  TutorialValidationResponse,
} from "@studio/contracts/tutorials";
import { useEffect, useState } from "react";
import { ApiError, fetchTutorialCatalog, saveTutorialCatalog, validateTutorialCatalog } from "../../shared/api";
import { tutorialTargetScreen } from "./targetScreen";

const LANGUAGES: TutorialLanguage[] = ["pl", "en", "es"];
const SCREENS: TutorialScreen[] = ["DASHBOARD", "CAMPAIGN", "LOADOUT", "MATCH", "SHOP"];
const PLAYER_ACTIONS: TutorialPlayerActionKind[] = [
  "FIRE", "DEPLOY_UNIT", "SEND_WAVE", "CAST_SPELL", "SELECT_AMMUNITION",
];

export function TutorialsTab() {
  const [document, setDocument] = useState<{ content: string; hash: string } | null>(null);
  const [catalog, setCatalog] = useState<TutorialCatalog | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [previewLanguage, setPreviewLanguage] = useState<TutorialLanguage>("en");
  const [validation, setValidation] = useState<TutorialValidationResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const reload = () => {
    setBusy(true);
    setError(null);
    fetchTutorialCatalog()
      .then(result => {
        const parsed = JSON.parse(result.content) as TutorialCatalog;
        setDocument(result);
        setCatalog(parsed);
        setSelectedId(current =>
          current && parsed.tutorials.some(item => item.tutorialId === current)
            ? current
            : parsed.tutorials[0]?.tutorialId ?? null,
        );
        setValidation(null);
        setMessage(null);
      })
      .catch((cause: Error) => setError(cause.message))
      .finally(() => setBusy(false));
  };

  useEffect(reload, []);

  const selected = catalog?.tutorials.find(item => item.tutorialId === selectedId) ?? null;
  const targetScreen = selected
    ? tutorialTargetScreen(selected.scope, selected.trigger)
    : null;
  const availableTargets = catalog && targetScreen
    ? catalog.targets.filter(target => target.screen === targetScreen)
    : [];
  const content = catalog ? `${JSON.stringify(catalog, null, 2)}\n` : "";
  const dirty = document !== null && content !== document.content;

  const updateCatalog = (transform: (current: TutorialCatalog) => TutorialCatalog) => {
    setCatalog(current => current ? transform(current) : current);
    setValidation(null);
    setError(null);
    setMessage(null);
  };

  const updateSelected = (patch: Partial<TutorialDefinition>) => {
    if (!selected) return;
    updateCatalog(current => ({
      ...current,
      tutorials: current.tutorials.map(item =>
        item.tutorialId === selected.tutorialId ? { ...item, ...patch } : item,
      ),
    }));
  };

  const validate = async (): Promise<boolean> => {
    if (!catalog) return false;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await validateTutorialCatalog(content);
      setValidation(result);
      setMessage(result.valid ? "Tutorial catalog is valid." : null);
      return result.valid;
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : String(cause));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    if (!document || !catalog || !(await validate())) return;
    setBusy(true);
    setError(null);
    try {
      const result = await saveTutorialCatalog({
        content,
        expectedHash: document.hash,
      });
      const saved = JSON.parse(result.content) as TutorialCatalog;
      setDocument(result);
      setCatalog(saved);
      setValidation({ valid: true, issues: [] });
      setMessage("Saved. Rebuild and restart the game to load the updated tutorial catalog.");
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setError("The tutorial catalog changed outside Studio. Reload it before saving again.");
      } else {
        setError(cause instanceof Error ? cause.message : String(cause));
      }
    } finally {
      setBusy(false);
    }
  };

  const addTutorial = () => {
    if (!catalog) return;
    const tutorialId = nextTutorialId(catalog);
    const step: TutorialStep = {
      stepId: "introduction",
      targetId: null,
      text: { pl: "", en: "", es: "" },
      placementPreference: "AUTO",
      completion: { type: "NEXT" },
      unlocksTargets: [],
      supplementalContent: null,
    };
    const tutorial: TutorialDefinition = {
      tutorialId,
      scope: "SCREEN",
      enabled: true,
      trigger: { type: "SCREEN_ENTERED", screen: "DASHBOARD" },
      requires: [],
      priority: 0,
      introduces: [],
      rules: {
        pauseSimulationOnHint: true,
        suspendAiWhileActive: true,
        restrictAiToIntroducedMechanics: false,
      },
      steps: [step],
    };
    updateCatalog(current => ({ ...current, tutorials: [...current.tutorials, tutorial] }));
    setSelectedId(tutorialId);
  };

  return (
    <section className="tutorial-editor" aria-label="Tutorial editor">
      <header className="tutorial-editor-header">
        <div>
          <h2>Tutorials</h2>
          <p>Edits the bundled, localized tutorial catalog. Game changes require a rebuild and restart.</p>
        </div>
        <div className="tutorial-actions">
          <button type="button" onClick={reload} disabled={busy}>Reload</button>
          <button type="button" onClick={addTutorial} disabled={busy || !catalog}>Add tutorial</button>
          <button type="button" onClick={() => void validate()} disabled={busy || !catalog}>
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
      {document && <p className="tutorial-hash">Catalog SHA-256: <code>{document.hash}</code></p>}
      {error && <p className="error" role="alert">{error}</p>}
      {message && <p className="tutorial-success" role="status">{message}</p>}
      {validation && !validation.valid && (
        <ul className="tutorial-issues" aria-label="Tutorial validation errors">
          {validation.issues.map((item, index) => (
            <li key={`${item.path}-${index}`}><code>{item.path}</code>: {item.message}</li>
          ))}
        </ul>
      )}
      {!catalog && !error && <p className="hint-text">Loading tutorial catalog…</p>}
      {catalog && (
        <div className="tutorial-layout">
          <aside className="tutorial-list" aria-label="Tutorial catalog">
            {catalog.tutorials.map(tutorial => (
              <button
                type="button"
                key={tutorial.tutorialId}
                className={tutorial.tutorialId === selectedId ? "active" : undefined}
                onClick={() => setSelectedId(tutorial.tutorialId)}
              >
                <strong>{tutorial.tutorialId}</strong>
                <small>{tutorial.enabled ? tutorial.scope : "DISABLED"}</small>
              </button>
            ))}
            <details>
              <summary>Registered IDs</summary>
              <p>Mechanics</p>
              <ul>{catalog.registeredMechanicIds.map(id => <li key={id}><code>{id}</code></li>)}</ul>
              <p>Events</p>
              <ul>{catalog.registeredEventKinds.map(id => <li key={id}><code>{id}</code></li>)}</ul>
              <p>Campaign milestones</p>
              <ul>{catalog.registeredMilestoneIds.map(id => <li key={id}><code>{id}</code></li>)}</ul>
            </details>
          </aside>
          {selected ? (
            <div className="tutorial-form">
              <section className="tutorial-card">
                <h3>Definition</h3>
                <label>
                  Tutorial ID
                  <input
                    value={selected.tutorialId}
                    onChange={event => {
                      const nextId = event.target.value;
                      setSelectedId(nextId);
                      updateCatalog(current => ({
                        ...current,
                        tutorials: current.tutorials.map(item =>
                          item.tutorialId === selected.tutorialId
                            ? { ...item, tutorialId: nextId }
                            : item,
                        ),
                      }));
                    }}
                  />
                </label>
                <div className="tutorial-grid">
                  <label>
                    Scope
                    <select
                      value={selected.scope}
                        onChange={event => updateSelected({ scope: event.target.value as TutorialScope })}
                    >
                      <option value="SCREEN">Screen</option>
                      <option value="MATCH">Match</option>
                    </select>
                  </label>
                  <label>
                    Priority
                    <input
                      type="number"
                      value={selected.priority}
                      onChange={event => updateSelected({ priority: Number(event.target.value) })}
                    />
                  </label>
                  <label className="tutorial-checkbox">
                    <input
                      type="checkbox"
                      checked={selected.enabled}
                      onChange={event => updateSelected({ enabled: event.target.checked })}
                    />
                    Enabled
                  </label>
                </div>
                <TriggerEditor
                  catalog={catalog}
                  trigger={selected.trigger}
                  onChange={trigger => updateSelected({ trigger })}
                />
                <label>
                  Requires completed or skipped tutorials
                  <select
                    multiple
                    value={selected.requires}
                    onChange={event => updateSelected({
                      requires: [...event.currentTarget.selectedOptions].map(option => option.value),
                    })}
                  >
                    {catalog.tutorials
                      .filter(item => item.tutorialId !== selected.tutorialId)
                      .map(item => <option key={item.tutorialId} value={item.tutorialId}>{item.tutorialId}</option>)}
                  </select>
                </label>
                <label>
                  Introduces mechanics
                  <select
                    multiple
                    value={selected.introduces}
                    onChange={event => updateSelected({
                      introduces: [...event.currentTarget.selectedOptions].map(option => option.value),
                    })}
                  >
                    {catalog.registeredMechanicIds.map(id => <option key={id} value={id}>{id}</option>)}
                  </select>
                </label>
                <div className="tutorial-rules">
                  <label><input
                    type="checkbox"
                    checked={selected.rules.pauseSimulationOnHint}
                    onChange={event => updateSelected({
                      rules: { ...selected.rules, pauseSimulationOnHint: event.target.checked },
                    })}
                  /> Pause simulation on hints</label>
                  <label><input
                    type="checkbox"
                    checked={selected.rules.suspendAiWhileActive}
                    onChange={event => updateSelected({
                      rules: { ...selected.rules, suspendAiWhileActive: event.target.checked },
                    })}
                  /> Suspend AI while active</label>
                  <label><input
                    type="checkbox"
                    checked={selected.rules.restrictAiToIntroducedMechanics}
                    onChange={event => updateSelected({
                      rules: {
                        ...selected.rules,
                        restrictAiToIntroducedMechanics: event.target.checked,
                      },
                    })}
                  /> Restrict AI to introduced mechanics</label>
                </div>
              </section>
              <section className="tutorial-card">
                <div className="tutorial-section-heading">
                  <h3>Steps</h3>
                  <button type="button" onClick={() => updateSelected({
                    steps: [...selected.steps, emptyStep(`step-${selected.steps.length + 1}`)],
                  })}>Add step</button>
                </div>
                {selected.steps.map((step, index) => (
                  <StepEditor
                    key={step.stepId}
                    catalog={catalog}
                    availableTargets={availableTargets}
                    targetScreen={targetScreen}
                    step={step}
                    index={index}
                    onChange={updated => updateSelected({
                      steps: selected.steps.map((item, stepIndex) =>
                        stepIndex === index ? updated : item,
                      ),
                    })}
                    onRemove={() => updateSelected({
                      steps: selected.steps.filter((_, stepIndex) => stepIndex !== index),
                    })}
                  />
                ))}
              </section>
              <section className="tutorial-preview">
                <h3>Hint text preview</h3>
                <label>
                  Language
                  <select
                    value={previewLanguage}
                    onChange={event => setPreviewLanguage(event.target.value as TutorialLanguage)}
                  >
                    {LANGUAGES.map(language => <option key={language} value={language}>{language.toUpperCase()}</option>)}
                  </select>
                </label>
                <p>{selected.steps[0]?.text[previewLanguage] || "Enter localized hint text in each language."}</p>
              </section>
            </div>
          ) : (
            <p className="hint-text">Select a tutorial or add one.</p>
          )}
        </div>
      )}
    </section>
  );
}

function TriggerEditor({
  catalog,
  trigger,
  onChange,
}: {
  catalog: TutorialCatalog;
  trigger: TutorialTrigger;
  onChange: (trigger: TutorialTrigger) => void;
}) {
  const setType = (type: TutorialTrigger["type"]) => {
    switch (type) {
      case "SCREEN_ENTERED":
        onChange({ type, screen: "DASHBOARD" });
        break;
      case "MATCH_STARTED":
        onChange({ type, campaignLevelRange: null, requiresMechanicId: null });
        break;
      case "MATCH_EVENT_OBSERVED":
        onChange({ type, eventKind: catalog.registeredEventKinds[0] ?? "" });
        break;
      case "CAMPAIGN_MILESTONE_APPLIED":
        onChange({ type, milestoneId: catalog.registeredMilestoneIds[0] ?? "" });
        break;
    }
  };
  return (
    <div className="tutorial-trigger">
      <label>
        Trigger
        <select value={trigger.type} onChange={event => setType(event.target.value as TutorialTrigger["type"])}>
          <option value="SCREEN_ENTERED">Screen entered</option>
          <option value="MATCH_STARTED">Match started</option>
          <option value="MATCH_EVENT_OBSERVED">Match event</option>
          <option value="CAMPAIGN_MILESTONE_APPLIED">Campaign milestone</option>
        </select>
      </label>
      {trigger.type === "SCREEN_ENTERED" && (
        <label>
          Screen
          <select value={trigger.screen} onChange={event => onChange({
            ...trigger,
            screen: event.target.value as TutorialScreen,
          })}>
            {SCREENS.map(screen => <option key={screen} value={screen}>{screen}</option>)}
          </select>
        </label>
      )}
      {trigger.type === "MATCH_STARTED" && (
        <>
          <label>
            Campaign level range
            <div className="tutorial-range">
              <input
                type="number"
                min={1}
                value={trigger.campaignLevelRange?.first ?? ""}
                placeholder="Any"
                onChange={event => onChange({
                  ...trigger,
                  campaignLevelRange: event.target.value === ""
                    ? null
                    : {
                      first: Number(event.target.value),
                      last: trigger.campaignLevelRange?.last ?? Number(event.target.value),
                    },
                })}
              />
              <span>to</span>
              <input
                type="number"
                min={1}
                value={trigger.campaignLevelRange?.last ?? ""}
                placeholder="Any"
                onChange={event => onChange({
                  ...trigger,
                  campaignLevelRange: event.target.value === ""
                    ? null
                    : {
                      first: trigger.campaignLevelRange?.first ?? Number(event.target.value),
                      last: Number(event.target.value),
                    },
                })}
              />
            </div>
          </label>
          <label>
            Requires available mechanic
            <select
              value={trigger.requiresMechanicId ?? ""}
              onChange={event => onChange({
                ...trigger,
                requiresMechanicId: event.target.value || null,
              })}
            >
              <option value="">Any</option>
              {catalog.registeredMechanicIds.map(id => <option key={id} value={id}>{id}</option>)}
            </select>
          </label>
        </>
      )}
      {trigger.type === "MATCH_EVENT_OBSERVED" && (
        <label>
          Event kind
          <select value={trigger.eventKind} onChange={event => onChange({
            ...trigger,
            eventKind: event.target.value,
          })}>
            {catalog.registeredEventKinds.map(id => <option key={id} value={id}>{id}</option>)}
          </select>
        </label>
      )}
      {trigger.type === "CAMPAIGN_MILESTONE_APPLIED" && (
        <label>
          Milestone
          <select value={trigger.milestoneId} onChange={event => onChange({
            ...trigger,
            milestoneId: event.target.value,
          })}>
            {catalog.registeredMilestoneIds.map(id => <option key={id} value={id}>{id}</option>)}
          </select>
        </label>
      )}
    </div>
  );
}

function StepEditor({
  catalog,
  availableTargets,
  targetScreen,
  step,
  index,
  onChange,
  onRemove,
}: {
  catalog: TutorialCatalog;
  availableTargets: TutorialCatalog["targets"];
  targetScreen: TutorialScreen | null;
  step: TutorialStep;
  index: number;
  onChange: (step: TutorialStep) => void;
  onRemove: () => void;
}) {
  const updateText = (language: TutorialLanguage, text: string) =>
    onChange({ ...step, text: { ...step.text, [language]: text } });
  const completion = step.completion;
  const targetIsCompatible = availableTargets.some(target => target.targetId === step.targetId);
  return (
    <article className="tutorial-step-editor">
      <div className="tutorial-section-heading">
        <h4>Step {index + 1}</h4>
        <button type="button" onClick={onRemove}>Remove</button>
      </div>
      <div className="tutorial-grid">
        <label>
          Step ID
          <input value={step.stepId} onChange={event => onChange({ ...step, stepId: event.target.value })} />
        </label>
        <label>
          Target{targetScreen ? ` (${targetScreen})` : ""}
          <select value={step.targetId ?? ""} onChange={event => onChange({
            ...step,
            targetId: event.target.value || null,
          })}>
            <option value="">No spotlight</option>
            {step.targetId && !targetIsCompatible && (
              <option value={step.targetId} disabled>
                {step.targetId} · incompatible with this tutorial screen
              </option>
            )}
            {availableTargets.map(target => (
              <option key={target.targetId} value={target.targetId}>
                {target.targetId} · {target.screen} · {target.kind}
              </option>
            ))}
          </select>
        </label>
        <label>
          Hint placement
          <select
            value={step.placementPreference}
            onChange={event => onChange({
              ...step,
              placementPreference: event.target.value as TutorialStep["placementPreference"],
            })}
          >
            {["AUTO", "ABOVE", "BELOW", "LEFT", "RIGHT"].map(value => (
              <option key={value} value={value}>{value}</option>
            ))}
          </select>
        </label>
      </div>
      {LANGUAGES.map(language => (
        <label key={language}>
          {language.toUpperCase()} text
          <textarea
            rows={2}
            value={step.text[language]}
            onChange={event => updateText(language, event.target.value)}
          />
        </label>
      ))}
      <div className="tutorial-grid">
        <label>
          Completion
          <select
            value={completion.type}
            onChange={event => onChange({
              ...step,
              completion: createCompletion(
                event.target.value,
                catalog.registeredEventKinds[0] ?? "",
              ),
            })}
          >
            <option value="NEXT">Next button</option>
            <option value="PLAYER_ACTION">Player action count</option>
            <option value="MATCH_EVENT_COUNT">Match event count</option>
          </select>
        </label>
        {completion.type === "PLAYER_ACTION" && (
          <>
            <label>
              Action
              <select value={completion.kind} onChange={event => onChange({
                ...step,
                completion: { ...completion, kind: event.target.value as TutorialPlayerActionKind },
              })}>
                {PLAYER_ACTIONS.map(action => <option key={action} value={action}>{action}</option>)}
              </select>
            </label>
            <label>
              Count
              <input type="number" min={1} value={completion.count} onChange={event => onChange({
                ...step,
                completion: { ...completion, count: Number(event.target.value) },
              })} />
            </label>
          </>
        )}
        {completion.type === "MATCH_EVENT_COUNT" && (
          <>
            <label>
              Event kind
              <select value={completion.eventKind} onChange={event => onChange({
                ...step,
                completion: { ...completion, eventKind: event.target.value },
              })}>
                {catalog.registeredEventKinds.map(id => <option key={id} value={id}>{id}</option>)}
              </select>
            </label>
            <label>
              Count
              <input type="number" min={1} value={completion.count} onChange={event => onChange({
                ...step,
                completion: { ...completion, count: Number(event.target.value) },
              })} />
            </label>
          </>
        )}
      </div>
      <label>
        Unlock targets at this step
        <select
          multiple
          value={step.unlocksTargets}
          onChange={event => onChange({
            ...step,
            unlocksTargets: [...event.currentTarget.selectedOptions].map(option => option.value),
          })}
        >
          {catalog.targets.map(target => (
            <option key={target.targetId} value={target.targetId}>{target.targetId}</option>
          ))}
        </select>
      </label>
      <label>
        Supplemental content
        <select value={step.supplementalContent ?? ""} onChange={event => onChange({
          ...step,
          supplementalContent: event.target.value === ""
            ? null
            : event.target.value as TutorialStep["supplementalContent"],
        })}>
          <option value="">None</option>
          <option value="WIND_STRENGTH_LEGEND">Wind strength legend</option>
        </select>
      </label>
    </article>
  );
}

function createCompletion(type: string, defaultEventKind: string): TutorialStepCompletion {
  switch (type) {
    case "PLAYER_ACTION":
      return { type, kind: "FIRE", count: 1 };
    case "MATCH_EVENT_COUNT":
      return { type, eventKind: defaultEventKind, count: 1 };
    default:
      return { type: "NEXT" };
  }
}

function emptyStep(stepId: string): TutorialStep {
  return {
    stepId,
    targetId: null,
    text: { pl: "", en: "", es: "" },
    placementPreference: "AUTO",
    completion: { type: "NEXT" },
    unlocksTargets: [],
    supplementalContent: null,
  };
}

function nextTutorialId(catalog: TutorialCatalog): string {
  let suffix = catalog.tutorials.length + 1;
  let candidate = `tutorial.new_${suffix}`;
  while (catalog.tutorials.some(item => item.tutorialId === candidate)) {
    suffix += 1;
    candidate = `tutorial.new_${suffix}`;
  }
  return candidate;
}
