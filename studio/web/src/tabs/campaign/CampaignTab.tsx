import { useCallback, useEffect, useMemo, useState } from "react";
import type {
  CampaignChapterCatalogEntry,
  CampaignMapLayout,
} from "@studio/contracts/campaign";
import {
  ApiError,
  fetchCampaignAsset,
  fetchCampaignChapters,
  fetchCampaignMapLayout,
  saveCampaignMapLayout,
  validateCampaignMapLayout,
} from "../../shared/api";
import {
  chapterLandmarkKind,
  encodeCampaignMapLayout,
  parseCampaignMapLayout,
} from "./model";
import {
  coordinateInputError,
  emptyCoordinateErrors,
  updateCoordinateErrors,
} from "./coordinateValidation";
import type { CampaignMapImages } from "./CampaignMapCanvas";
import { CampaignMapCanvas } from "./CampaignMapCanvas";
import "./styles.css";

type LoadState = "catalog" | "loading" | "ready" | "missing" | "error";

export function CampaignTab() {
  const [chapters, setChapters] = useState<CampaignChapterCatalogEntry[]>([]);
  const [chapterId, setChapterId] = useState("");
  const [loadState, setLoadState] = useState<LoadState>("catalog");
  const [layout, setLayout] = useState<CampaignMapLayout | null>(null);
  const [images, setImages] = useState<CampaignMapImages | null>(null);
  const [baselineContent, setBaselineContent] = useState<string | null>(null);
  const [savedHash, setSavedHash] = useState<string | null>(null);
  const [selectedLevel, setSelectedLevel] = useState<number | null>(null);
  const [pendingChapterId, setPendingChapterId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [status, setStatus] = useState("Loading campaign chapters…");
  const [chapterRetry, setChapterRetry] = useState(0);
  const [catalogRetry, setCatalogRetry] = useState(0);
  const [needsReload, setNeedsReload] = useState(false);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [overlaps, setOverlaps] = useState<Array<[number, number]>>([]);
  const [xInput, setXInput] = useState("");
  const [yInput, setYInput] = useState("");
  const [coordinateErrors, setCoordinateErrors] = useState(emptyCoordinateErrors);

  const chapter = chapters.find(item => item.id === chapterId);
  const selectedAnchor = layout?.anchors.find(anchor => anchor.level === selectedLevel);
  const coordinateError = coordinateErrors.x || coordinateErrors.y;
  const isDirty = layout !== null &&
    baselineContent !== null &&
    encodeCampaignMapLayout(layout) !== baselineContent;

  useEffect(() => {
    let cancelled = false;
    setLoadState("catalog");
    setStatus("Loading campaign chapters…");
    fetchCampaignChapters()
      .then(result => {
        if (cancelled) return;
        setChapters(result.chapters);
        const initial = result.chapters.find(item => item.mapAvailable) ?? result.chapters[0];
        setChapterId(initial?.id ?? "");
        setStatus(initial ? "Select a campaign chapter" : "No campaign chapters are configured.");
      })
      .catch(error => {
        if (!cancelled) {
          setLoadState("error");
          setStatus(errorMessage(error));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [catalogRetry]);

  useEffect(() => {
    if (!chapterId || !chapter) return;
    let cancelled = false;
    setLayout(null);
    setImages(null);
    setBaselineContent(null);
    setSavedHash(null);
    setSelectedLevel(null);
    setOverlaps([]);
    setValidationErrors([]);
    setCoordinateErrors(emptyCoordinateErrors());
    setNeedsReload(false);

    if (!chapter.mapAvailable) {
      setLoadState("missing");
      setStatus(`Map artwork is not available for ${chapter.title}.`);
      return () => {
        cancelled = true;
      };
    }

    setLoadState("loading");
    setStatus(`Loading ${chapter.title} map…`);
    void (async () => {
      try {
        const document = await fetchCampaignMapLayout(chapterId);
        const parsedLayout = parseCampaignMapLayout(document.content, chapter);
        const loadedImages = await loadCampaignImages(parsedLayout.backgroundAssetKey);
        if (cancelled) return;
        setLayout(parsedLayout);
        setBaselineContent(encodeCampaignMapLayout(parsedLayout));
        setSavedHash(document.hash);
        setSelectedLevel(chapter.firstLevel);
        const firstAnchor = parsedLayout.anchors[0];
        setXInput(firstAnchor.x.toFixed(3));
        setYInput(firstAnchor.y.toFixed(3));
        setImages(loadedImages);
        setLoadState("ready");
        setStatus(`Loaded ${chapter.title}`);
      } catch (error) {
        if (cancelled) return;
        setLoadState("error");
        setStatus(errorMessage(error));
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [chapterId, chapter, chapterRetry]);

  const onOverlapsChange = useCallback((next: Array<[number, number]>) => {
    setOverlaps(current => sameOverlaps(current, next) ? current : next);
  }, []);

  const requestChapterChange = (nextChapterId: string) => {
    if (nextChapterId === chapterId) return;
    if (isDirty) {
      setPendingChapterId(nextChapterId);
    } else {
      setChapterId(nextChapterId);
    }
  };

  const saveDraft = async (): Promise<boolean> => {
    if (!chapter || !layout || !savedHash || saving) return false;
    if (coordinateError) {
      setStatus("Correct the selected marker coordinates before saving.");
      return false;
    }
    setSaving(true);
    setStatus(`Validating ${chapter.title} layout…`);
    setValidationErrors([]);
    try {
      const content = encodeCampaignMapLayout(layout);
      const validation = await validateCampaignMapLayout(chapter.id, content);
      const errors = validation.issues
        .filter(issue => issue.severity === "error")
        .map(issue => `${issue.path}: ${issue.message}`);
      if (!validation.valid || errors.length > 0) {
        setValidationErrors(errors);
        setStatus("Fix the campaign layout errors before saving.");
        return false;
      }
      const saved = await saveCampaignMapLayout(chapter.id, {
        content,
        expectedHash: savedHash,
      });
      setBaselineContent(content);
      setSavedHash(saved.hash);
      setNeedsReload(false);
      setStatus(`Saved ${chapter.title}; rebuild or restart the game to apply it.`);
      return true;
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setNeedsReload(true);
        setStatus("This layout changed outside Studio. Reload it before saving again.");
      } else if (error instanceof ApiError && error.details.length > 0) {
        setValidationErrors(error.details);
        setStatus(error.message);
      } else {
        setStatus(errorMessage(error));
      }
      return false;
    } finally {
      setSaving(false);
    }
  };

  const saveAndSwitch = async () => {
    if (await saveDraft()) {
      setChapterId(pendingChapterId ?? "");
      setPendingChapterId(null);
    }
  };

  const discardAndSwitch = () => {
    if (pendingChapterId) setChapterId(pendingChapterId);
    setPendingChapterId(null);
  };

  const updateAnchor = (level: number, x: number, y: number) => {
    setLayout(current => current
      ? {
        ...current,
        anchors: current.anchors.map(anchor =>
          anchor.level === level ? { ...anchor, x, y } : anchor,
        ),
      }
      : current);
    if (level === selectedLevel) {
      setXInput(x.toFixed(3));
      setYInput(y.toFixed(3));
      setCoordinateErrors(emptyCoordinateErrors());
    }
  };

  const updateCoordinate = (axis: "x" | "y", value: string) => {
    if (axis === "x") setXInput(value);
    else setYInput(value);
    const axisError = coordinateInputError(value);
    setCoordinateErrors(current => updateCoordinateErrors(current, axis, value));
    if (axisError) return;
    const coordinate = Number(value);
    if (!selectedAnchor) return;
    setLayout(current => current
      ? {
        ...current,
        anchors: current.anchors.map(anchor =>
          anchor.level === selectedAnchor.level
            ? {
              ...anchor,
              [axis]: coordinate,
            }
            : anchor,
        ),
      }
      : current);
  };

  const selectLevel = (level: number) => {
    setSelectedLevel(level);
    const anchor = layout?.anchors.find(item => item.level === level);
    if (anchor) {
      setXInput(anchor.x.toFixed(3));
      setYInput(anchor.y.toFixed(3));
      setCoordinateErrors(emptyCoordinateErrors());
    }
  };

  const landmarkKind = useMemo(
    () => chapter && selectedLevel ? chapterLandmarkKind(chapter, selectedLevel) : null,
    [chapter, selectedLevel],
  );

  return (
    <div className="campaign-editor">
      <header className="campaign-editor-toolbar">
        <strong>Campaign</strong>
        <label>
          Chapter
          <select
            value={chapterId}
            onChange={event => requestChapterChange(event.currentTarget.value)}
            disabled={loadState === "catalog" || chapters.length === 0}
          >
            {chapters.map(item => (
              <option key={item.id} value={item.id}>
                {item.title}{item.mapAvailable ? "" : " — map missing"}
              </option>
            ))}
          </select>
        </label>
        <span className="campaign-editor-status" role="status">{status}</span>
        {loadState === "error" && !chapterId && (
          <button type="button" onClick={() => setCatalogRetry(value => value + 1)}>Retry chapters</button>
        )}
        {needsReload && chapterId && (
          <button type="button" onClick={() => setChapterRetry(value => value + 1)}>
            Reload source (discard draft)
          </button>
        )}
        {loadState === "error" && chapterId && !needsReload && (
          <button
            type="button"
            onClick={() => setChapterRetry(value => value + 1)}
          >
            Retry load
          </button>
        )}
        <button
          type="button"
          onClick={() => void saveDraft()}
          disabled={!isDirty || saving || needsReload || Boolean(coordinateError)}
        >
          {saving ? "Saving…" : "Save chapter"}
        </button>
      </header>

      {validationErrors.length > 0 && (
        <div className="campaign-editor-errors" role="alert">
          {validationErrors.map((error, index) => <p key={`${index}-${error}`}>{error}</p>)}
        </div>
      )}

      {loadState === "missing" && chapter && (
        <div className="campaign-editor-missing" role="status">
          <strong>Map artwork missing</strong>
          <p>{chapter.title} has no campaign map image yet. Marker placement is disabled until the map asset is added.</p>
        </div>
      )}

      {loadState === "error" && (
        <div className="campaign-editor-errors" role="alert">{status}</div>
      )}

      {loadState === "ready" && chapter && layout && images && (
        <div className="campaign-editor-layout">
          <aside className="campaign-editor-levels">
            <h2>Level markers</h2>
            <p>Drag a marker on the map or select it to edit exact coordinates.</p>
            <div className="campaign-editor-level-list">
              {layout.anchors.map(anchor => {
                const kind = chapterLandmarkKind(chapter, anchor.level);
                return (
                  <button
                    key={anchor.level}
                    type="button"
                    className={anchor.level === selectedLevel ? "selected" : undefined}
                    onClick={() => selectLevel(anchor.level)}
                    aria-pressed={anchor.level === selectedLevel}
                  >
                    <span>LEVEL {anchor.level}</span>
                    <small>{kind}</small>
                  </button>
                );
              })}
            </div>
          </aside>

          <div className="campaign-editor-preview">
            <CampaignMapCanvas
              chapter={chapter}
              layout={layout}
              images={images}
              selectedLevel={selectedLevel ?? chapter.firstLevel}
              onSelectLevel={selectLevel}
              onAnchorChange={updateAnchor}
              onOverlapsChange={onOverlapsChange}
            />
            <div className="campaign-editor-preview-hint">Drag the numbered marker to reposition it</div>
          </div>

          <aside className="campaign-editor-properties">
            <h2>{selectedLevel ? `LEVEL ${selectedLevel}` : "Marker"}</h2>
            <p>{landmarkKind === "BOSS" ? "Boss landmark" : "Fortress landmark"}</p>
            <label>
              X
              <input
                type="number"
                min="0"
                max="1"
                step="0.001"
                value={xInput}
                onChange={event => updateCoordinate("x", event.currentTarget.value)}
                disabled={!selectedAnchor}
              />
            </label>
            <label>
              Y
              <input
                type="number"
                min="0"
                max="1"
                step="0.001"
                value={yInput}
                onChange={event => updateCoordinate("y", event.currentTarget.value)}
                disabled={!selectedAnchor}
              />
            </label>
            {coordinateError && <p className="campaign-editor-warning">{coordinateError}</p>}
            <p className="campaign-editor-coordinate-note">
              Normalized to the source map image; values range from 0 to 1.
            </p>
            {overlaps.length > 0 && (
              <div className="campaign-editor-warning" role="status">
                <strong>Marker overlap warning</strong>
                {overlaps.map(([first, second]) => (
                  <p key={`${first}-${second}`}>Levels {first} and {second} overlap in this preview.</p>
                ))}
                <small>Overlap does not block saving.</small>
              </div>
            )}
          </aside>
        </div>
      )}

      {pendingChapterId && (
        <div className="campaign-editor-modal-backdrop">
          <section className="campaign-editor-modal" role="dialog" aria-modal="true" aria-labelledby="campaign-switch-title">
            <h2 id="campaign-switch-title">Unsaved marker positions</h2>
            <p>Save your changes to {chapter?.title} before switching chapters?</p>
            <div>
              <button type="button" onClick={() => void saveAndSwitch()} disabled={saving}>Save</button>
              <button type="button" onClick={discardAndSwitch} disabled={saving}>Discard</button>
              <button type="button" onClick={() => setPendingChapterId(null)} disabled={saving}>Cancel</button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

async function loadCampaignImages(backgroundAssetKey: string): Promise<CampaignMapImages> {
  const [background, frame, fortress, boss] = await Promise.all([
    loadImage(backgroundAssetKey),
    loadImage("campaign_map_frame_overlay"),
    loadImage("campaign_map_marker_fortress"),
    loadImage("campaign_map_marker_boss"),
  ]);
  return { background, frame, fortress, boss };
}

async function loadImage(assetKey: string): Promise<HTMLImageElement> {
  const blob = await fetchCampaignAsset(assetKey);
  const url = URL.createObjectURL(blob);
  try {
    return await new Promise((resolve, reject) => {
      const image = new Image();
      image.onload = () => resolve(image);
      image.onerror = () => reject(new Error(`Unable to decode campaign asset '${assetKey}'`));
      image.src = url;
    });
  } finally {
    URL.revokeObjectURL(url);
  }
}

function sameOverlaps(
  current: Array<[number, number]>,
  next: Array<[number, number]>,
): boolean {
  return current.length === next.length &&
    current.every(([first, second], index) =>
      first === next[index][0] && second === next[index][1],
    );
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
