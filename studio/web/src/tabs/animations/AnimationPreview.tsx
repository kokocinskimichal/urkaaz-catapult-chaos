import type { AnimationSequence } from "@studio/contracts/animations";
import { useEffect, useRef, useState } from "react";
import { advancePlayback, frameUrl, stepFrame } from "./model";

type Background = "dark" | "light" | "grid";

interface LoadedFrames {
  sequenceId: string;
  team: string;
  images: HTMLCanvasElement[];
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`Could not load ${url}`));
    image.src = url;
  });
}

export function AnimationPreview({
  sequence,
  team = sequence.variant ?? "blue",
  showMask = false,
}: {
  sequence: AnimationSequence;
  team?: string;
  showMask?: boolean;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [loaded, setLoaded] = useState<LoadedFrames | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [frame, setFrame] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [fps, setFps] = useState(24);
  const [background, setBackground] = useState<Background>("grid");

  useEffect(() => {
    let cancelled = false;
    setError(null);
    setFrame(0);
    const isDefender = sequence.category === "character" && sequence.target === "defender";
    const texturePromise = isDefender
      ? loadImage(`/api/animations/defender-textures/${team}`)
      : Promise.resolve(null);
    Promise.all([
      Promise.all(sequence.frameIndices.map(index => loadImage(frameUrl(sequence.id, index)))),
      isDefender
        ? Promise.all(sequence.frameIndices.map(index => loadImage(`/api/animations/${sequence.id}/masks/${index}`)))
        : Promise.resolve([] as HTMLImageElement[]),
      texturePromise,
    ])
      .then(([frames, masks, texture]) => {
        const images = frames.map((frame, index) =>
          composeAnimationFrame(frame, masks[index], texture, showMask, team === "red" && isDefender),
        );
        if (!cancelled) setLoaded({ sequenceId: sequence.id, team, images });
      })
      .catch((cause: Error) => {
        if (!cancelled) setError(cause.message);
      });
    return () => {
      cancelled = true;
    };
  }, [sequence.id, sequence.frameIndices, sequence.variant, team, showMask]);

  const images = loaded?.sequenceId === sequence.id && loaded.team === team ? loaded.images : null;
  const frameCount = images?.length ?? 0;

  useEffect(() => {
    if (!playing || frameCount < 2) return;
    let handle = 0;
    let last = performance.now();
    let accumulated = 0;
    const tick = (now: number) => {
      const advanced = advancePlayback(accumulated, now - last, fps);
      last = now;
      accumulated = advanced.accumulatedMs;
      if (advanced.steps > 0) setFrame(current => stepFrame(current, frameCount, advanced.steps));
      handle = requestAnimationFrame(tick);
    };
    handle = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(handle);
  }, [playing, fps, frameCount]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const image = images?.[frame];
    if (!canvas || !images || !image) return;
    const width = Math.max(...images.map(item => item.width));
    const height = Math.max(...images.map(item => item.height));
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    const context = canvas.getContext("2d");
    if (!context) return;
    context.clearRect(0, 0, width, height);
    context.drawImage(image, (width - image.width) / 2, (height - image.height) / 2);
  }, [images, frame]);

  const size = images?.[0] ? `${images[0].width} × ${images[0].height}` : "…";
  const shownIndex = sequence.frameIndices[frame] ?? 0;

  return (
    <div className="preview">
      <div className={`preview-stage bg-${background}`}>
        {error ? <p className="error">{error}</p> : images ? <canvas ref={canvasRef} /> : <p className="hint-text">Loading frames…</p>}
      </div>
      <div className="preview-controls">
        <button onClick={() => setPlaying(value => !value)} disabled={frameCount < 2}>
          {playing ? "Pause" : "Play"}
        </button>
        <button onClick={() => { setPlaying(false); setFrame(current => stepFrame(current, frameCount, -1)); }} disabled={frameCount < 2}>
          ◀ Frame
        </button>
        <button onClick={() => { setPlaying(false); setFrame(current => stepFrame(current, frameCount, 1)); }} disabled={frameCount < 2}>
          Frame ▶
        </button>
        <label>
          FPS {fps}
          <input type="range" min={1} max={60} value={fps} onChange={event => setFps(Number(event.target.value))} />
        </label>
        <label>
          Background
          <select value={background} onChange={event => setBackground(event.target.value as Background)}>
            <option value="grid">Grid</option>
            <option value="dark">Dark</option>
            <option value="light">Light</option>
          </select>
        </label>
      </div>
      <p className="preview-info">
        <code>{sequence.id}</code> · frame {String(shownIndex).padStart(3, "0")} · {sequence.frameIndices.length} frames · {size}
      </p>
      {!sequence.hasRuntimeLoader && <p className="warning">This sequence has no runtime loader in the game.</p>}
      {!sequence.targetConfigured && <p className="warning">Target is not listed in studio/config/animations.json.</p>}
      {sequence.issues.map(issue => (
        <p key={issue} className="error">{issue}</p>
      ))}
    </div>
  );
}

function composeAnimationFrame(
  source: HTMLImageElement,
  mask: HTMLImageElement | undefined,
  texture: HTMLImageElement | null,
  showMask: boolean,
  mirror: boolean,
): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  canvas.width = source.naturalWidth;
  canvas.height = source.naturalHeight;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("Canvas 2D context is unavailable");
  if (mirror) {
    context.save();
    context.translate(canvas.width, 0);
    context.scale(-1, 1);
  }
  context.drawImage(source, 0, 0);
  if (mirror) context.restore();
  if (!mask || !texture) return canvas;

  const sourcePixels = context.getImageData(0, 0, canvas.width, canvas.height);
  const maskCanvas = document.createElement("canvas");
  maskCanvas.width = canvas.width;
  maskCanvas.height = canvas.height;
  const maskContext = maskCanvas.getContext("2d");
  if (!maskContext) throw new Error("Canvas 2D context is unavailable");
  if (mirror) {
    maskContext.save();
    maskContext.translate(maskCanvas.width, 0);
    maskContext.scale(-1, 1);
  }
  maskContext.drawImage(mask, 0, 0);
  if (mirror) maskContext.restore();
  const maskPixels = maskContext.getImageData(0, 0, canvas.width, canvas.height);
  const textureCanvas = document.createElement("canvas");
  textureCanvas.width = texture.naturalWidth;
  textureCanvas.height = texture.naturalHeight;
  const textureContext = textureCanvas.getContext("2d");
  if (!textureContext) throw new Error("Canvas 2D context is unavailable");
  textureContext.drawImage(texture, 0, 0);
  const texturePixels = textureContext.getImageData(0, 0, textureCanvas.width, textureCanvas.height);

  for (let y = 0; y < canvas.height; y++) {
    for (let x = 0; x < canvas.width; x++) {
      const index = (y * canvas.width + x) * 4;
      const alpha = sourcePixels.data[index + 3];
      const coverage = (maskPixels.data[index + 3] * alpha) / (255 * 255);
      if (coverage === 0) continue;
      const luminance = showMask
        ? 1
        : (sourcePixels.data[index] * 299 +
          sourcePixels.data[index + 1] * 587 +
          sourcePixels.data[index + 2] * 114) /
        1000 /
        255;
      const lighting = Math.max(0.45, Math.min(1.2, 0.5 + luminance));
      const textureIndex =
        ((y % textureCanvas.height) * textureCanvas.width + (x % textureCanvas.width)) * 4;
      for (let channel = 0; channel < 3; channel++) {
        const textured = showMask
          ? [255, 30, 70][channel]
          : texturePixels.data[textureIndex + channel] * lighting;
        sourcePixels.data[index + channel] =
          sourcePixels.data[index + channel] * (1 - coverage) + textured * coverage;
      }
    }
  }
  context.putImageData(sourcePixels, 0, 0);
  return canvas;
}
