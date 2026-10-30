import type { AnimationCategory, AnimationSequence } from "@studio/contracts/animations";

export const CATEGORY_ORDER: readonly AnimationCategory[] = ["character", "spell", "ammunition", "boss"];

export const CATEGORY_LABELS: Record<AnimationCategory, string> = {
  character: "Units",
  spell: "Spells",
  ammunition: "Ammunition",
  boss: "Bosses",
};

export interface TypeNode {
  type: string;
  sequences: AnimationSequence[];
}

export interface TargetNode {
  target: string;
  types: TypeNode[];
}

export interface CategoryNode {
  category: AnimationCategory;
  targets: TargetNode[];
}

export interface Selection {
  category: AnimationCategory;
  target: string;
  type: string;
}

export function buildTree(sequences: readonly AnimationSequence[]): CategoryNode[] {
  return CATEGORY_ORDER.flatMap(category => {
    const inCategory = sequences.filter(sequence => sequence.category === category);
    if (inCategory.length === 0) return [];
    const targets = groupBy(inCategory, sequence => sequence.target).map(([target, ofTarget]) => ({
      target,
      types: groupBy(ofTarget, sequence => sequence.type).map(([type, ofType]) => ({
        type,
        sequences: [...ofType].sort((a, b) => (a.variant ?? "").localeCompare(b.variant ?? "")),
      })),
    }));
    return [{ category, targets }];
  });
}

function groupBy<T>(items: readonly T[], key: (item: T) => string): [string, T[]][] {
  const groups = new Map<string, T[]>();
  for (const item of items) {
    const name = key(item);
    groups.set(name, [...(groups.get(name) ?? []), item]);
  }
  return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
}

export function variantsOf(node: TypeNode): string[] {
  return node.sequences.flatMap(sequence => (sequence.variant === null ? [] : [sequence.variant]));
}

export function findTypeNode(tree: readonly CategoryNode[], selection: Selection): TypeNode | null {
  return (
    tree
      .find(node => node.category === selection.category)
      ?.targets.find(node => node.target === selection.target)
      ?.types.find(node => node.type === selection.type) ?? null
  );
}

export function firstSelection(tree: readonly CategoryNode[]): Selection | null {
  const category = tree[0];
  const target = category?.targets[0];
  const type = target?.types[0];
  return category && target && type ? { category: category.category, target: target.target, type: type.type } : null;
}

export function firstSelectionForCategory(
  tree: readonly CategoryNode[],
  category: AnimationCategory,
): Selection | null {
  return firstSelection(tree.filter(node => node.category === category));
}

/** Keeps the preferred team variant when the type has it, otherwise falls back to the first sequence. */
export function pickSequence(node: TypeNode, preferredVariant: string | null): AnimationSequence {
  return node.sequences.find(sequence => sequence.variant === preferredVariant) ?? node.sequences[0];
}

export function frameUrl(sequenceId: string, frameIndex: number): string {
  return `/api/animations/${sequenceId}/frames/${frameIndex}`;
}

export function advancePlayback(accumulatedMs: number, deltaMs: number, fps: number): { steps: number; accumulatedMs: number } {
  const frameMs = 1000 / fps;
  const total = accumulatedMs + deltaMs;
  const steps = Math.floor(total / frameMs);
  return { steps, accumulatedMs: total - steps * frameMs };
}

export function stepFrame(current: number, frameCount: number, delta: number): number {
  return (((current + delta) % frameCount) + frameCount) % frameCount;
}
