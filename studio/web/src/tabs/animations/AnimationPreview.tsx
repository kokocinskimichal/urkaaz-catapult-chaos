import type { AnimationSequence } from "@studio/contracts/animations";
import { useEffect, useRef, useState } from "react";
import { advancePlayback, frameUrl, stepFrame } from "./model";

type Background = "dark" | "light" | "grid";

interface LoadedFrames {
  sequenceId: string;
  images: HTMLImageElement[];
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error(`Could not load ${url}`));
    image.src = url;
  });
}

export function AnimationPreview({ sequence }: { sequence: AnimationSequence }) {
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
    Promise.all(sequence.frameIndices.map(index => loadImage(frameUrl(sequence.id, index))))
      .then(images => {
        if (!cancelled) setLoaded({ sequenceId: sequence.id, images });
      })
      .catch((cause: Error) => {
        if (!cancelled) setError(cause.message);
      });
    return () => {
      cancelled = true;
    };
  }, [sequence.id, sequence.frameIndices]);

  const images = loaded?.sequenceId === sequence.id ? loaded.images : null;
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
    const width = Math.max(...images.map(item => item.naturalWidth));
    const height = Math.max(...images.map(item => item.naturalHeight));
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    const context = canvas.getContext("2d");
    if (!context) return;
    context.clearRect(0, 0, width, height);
    context.drawImage(image, (width - image.naturalWidth) / 2, (height - image.naturalHeight) / 2);
  }, [images, frame]);

  const size = images?.[0] ? `${images[0].naturalWidth} × ${images[0].naturalHeight}` : "…";
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
