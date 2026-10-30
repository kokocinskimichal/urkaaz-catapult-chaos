import { useEffect, useRef } from "react";
import type { PointerEvent } from "react";
import type { CampaignChapterCatalogEntry, CampaignMapLayout } from "@studio/contracts/campaign";
import {
  anchorToScreen,
  chapterLandmarkKind,
  coverMapBounds,
  findOverlappingMarkers,
  markerDisplaySize,
  markerVisualBounds,
  screenToAnchor,
} from "./model";
import type { CampaignMarkerBounds } from "./model";

export interface CampaignMapImages {
  background: HTMLImageElement;
  frame: HTMLImageElement;
  fortress: HTMLImageElement;
  boss: HTMLImageElement;
}

interface Props {
  chapter: CampaignChapterCatalogEntry;
  layout: CampaignMapLayout;
  images: CampaignMapImages;
  selectedLevel: number;
  onSelectLevel: (level: number) => void;
  onAnchorChange: (level: number, x: number, y: number) => void;
  onOverlapsChange: (overlaps: Array<[number, number]>) => void;
}

interface PointerDrag {
  pointerId: number;
  level: number;
}

export function CampaignMapCanvas({
  chapter,
  layout,
  images,
  selectedLevel,
  onSelectLevel,
  onAnchorChange,
  onOverlapsChange,
}: Props) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const drag = useRef<PointerDrag | null>(null);

  useEffect(() => {
    const target = canvas.current;
    if (!target) return;
    const context = target.getContext("2d");
    if (!context) return;

    const draw = () => {
      const { width, height } = target.getBoundingClientRect();
      if (width <= 0 || height <= 0) return;
      const pixelRatio = window.devicePixelRatio || 1;
      const backingWidth = Math.round(width * pixelRatio);
      const backingHeight = Math.round(height * pixelRatio);
      if (target.width !== backingWidth || target.height !== backingHeight) {
        target.width = backingWidth;
        target.height = backingHeight;
      }
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      context.clearRect(0, 0, width, height);

      const mapBounds = coverMapBounds(
        width,
        height,
        layout.imageWidthPx,
        layout.imageHeightPx,
      );
      context.drawImage(
        images.background,
        mapBounds.left,
        mapBounds.top,
        mapBounds.width,
        mapBounds.height,
      );

      const markerBounds: CampaignMarkerBounds[] = [];
      for (const anchor of layout.anchors) {
        const kind = chapterLandmarkKind(chapter, anchor.level);
        const markerSize = markerDisplaySize(kind);
        const iconSize = kind === "BOSS" ? 48 : 72;
        const center = anchorToScreen(mapBounds, anchor);
        const markerTop = center.y - markerSize / 2;
        const iconLeft = center.x - iconSize / 2;
        const plateTop = markerTop + iconSize - 12;

        context.drawImage(
          kind === "BOSS" ? images.boss : images.fortress,
          iconLeft,
          markerTop,
          iconSize,
          iconSize,
        );
        context.fillStyle = "rgba(34, 24, 16, 0.94)";
        context.strokeStyle = "#d4a654";
        context.lineWidth = 2;
        context.fillRect(center.x - 28, plateTop, 56, 22);
        context.strokeRect(center.x - 28, plateTop, 56, 22);
        context.fillStyle = "#fff0ce";
        context.font = "bold 13px sans-serif";
        context.textAlign = "center";
        context.textBaseline = "middle";
        context.fillText(String(anchor.level), center.x, plateTop + 11);

        markerBounds.push(markerVisualBounds(anchor.level, center, kind));
      }

      context.drawImage(images.frame, 0, 0, width, height);
      context.strokeStyle = "#fff0a0";
      context.lineWidth = 2;
      context.setLineDash([5, 4]);
      const selectedAnchor = layout.anchors.find(anchor => anchor.level === selectedLevel);
      if (selectedAnchor) {
        const kind = chapterLandmarkKind(chapter, selectedLevel);
        const size = markerDisplaySize(kind);
        const center = anchorToScreen(mapBounds, selectedAnchor);
        context.strokeRect(center.x - size / 2, center.y - size / 2, size, size);
      }
      context.setLineDash([]);
      onOverlapsChange(findOverlappingMarkers(markerBounds));
    };

    const observer = new ResizeObserver(draw);
    observer.observe(target);
    draw();
    return () => observer.disconnect();
  }, [chapter, images, layout, onOverlapsChange, selectedLevel]);

  const pointerPosition = (event: PointerEvent<HTMLCanvasElement>) => {
    const bounds = event.currentTarget.getBoundingClientRect();
    const mapBounds = coverMapBounds(
      bounds.width,
      bounds.height,
      layout.imageWidthPx,
      layout.imageHeightPx,
    );
    return {
      mapBounds,
      point: { x: event.clientX - bounds.left, y: event.clientY - bounds.top },
    };
  };

  const onPointerDown = (event: PointerEvent<HTMLCanvasElement>) => {
    const { mapBounds, point } = pointerPosition(event);
    const anchor = [...layout.anchors].reverse().find(candidate => {
      const kind = chapterLandmarkKind(chapter, candidate.level);
      const center = anchorToScreen(mapBounds, candidate);
      const size = markerDisplaySize(kind);
      return Math.abs(point.x - center.x) <= size / 2 &&
        Math.abs(point.y - center.y) <= size / 2;
    });
    if (!anchor) return;
    drag.current = { pointerId: event.pointerId, level: anchor.level };
    event.currentTarget.setPointerCapture(event.pointerId);
    onSelectLevel(anchor.level);
    event.preventDefault();
  };

  const onPointerMove = (event: PointerEvent<HTMLCanvasElement>) => {
    const activeDrag = drag.current;
    if (!activeDrag || activeDrag.pointerId !== event.pointerId) return;
    const { mapBounds, point } = pointerPosition(event);
    const position = screenToAnchor(mapBounds, point);
    onAnchorChange(activeDrag.level, position.x, position.y);
  };

  const onPointerUp = (event: PointerEvent<HTMLCanvasElement>) => {
    if (drag.current?.pointerId === event.pointerId) drag.current = null;
  };

  return (
    <canvas
      ref={canvas}
      className="campaign-map-canvas"
      aria-label="Campaign chapter map editor. Drag a marker to change its position."
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
    />
  );
}
