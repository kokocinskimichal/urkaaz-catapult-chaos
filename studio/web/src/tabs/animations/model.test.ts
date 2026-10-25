import type { AnimationSequence } from "@studio/contracts/animations";
import { describe, expect, it } from "vitest";
import { advancePlayback, buildTree, findTypeNode, firstSelection, frameUrl, pickSequence, stepFrame, variantsOf } from "./model";

function sequence(id: string, category: AnimationSequence["category"], target: string, type: string, variant: string | null): AnimationSequence {
  return { id, category, target, type, variant, frameIndices: [0, 1], targetConfigured: true, hasRuntimeLoader: true, supportsTeamTexture: false, issues: [] };
}

const sequences = [
  sequence("anim_ammunition_rock_flight", "ammunition", "rock", "flight", null),
  sequence("anim_character_defender_move_red", "character", "defender", "move", "red"),
  sequence("anim_character_defender_move_blue", "character", "defender", "move", "blue"),
  sequence("anim_character_defender_idle_blue", "character", "defender", "idle", "blue"),
  sequence("anim_spell_haste_orbiting_chevron", "spell", "haste", "orbiting_chevron", null),
];

describe("buildTree", () => {
  const tree = buildTree(sequences);

  it("orders categories as characters, spells, ammunition", () => {
    expect(tree.map(node => node.category)).toEqual(["character", "spell", "ammunition"]);
  });

  it("groups by target and type with team variants sorted", () => {
    const defender = tree[0].targets[0];
    expect(defender.target).toBe("defender");
    expect(defender.types.map(type => type.type)).toEqual(["idle", "move"]);
    expect(defender.types[1].sequences.map(item => item.variant)).toEqual(["blue", "red"]);
  });

  it("omits categories without sequences", () => {
    expect(buildTree(sequences.filter(item => item.category === "spell")).map(node => node.category)).toEqual(["spell"]);
    expect(buildTree([])).toEqual([]);
  });
});

describe("selection helpers", () => {
  const tree = buildTree(sequences);

  it("selects the first type of the first target", () => {
    expect(firstSelection(tree)).toEqual({ category: "character", target: "defender", type: "idle" });
    expect(firstSelection([])).toBeNull();
  });

  it("finds a type node and lists its variants", () => {
    const node = findTypeNode(tree, { category: "character", target: "defender", type: "move" });
    expect(node && variantsOf(node)).toEqual(["blue", "red"]);
    expect(findTypeNode(tree, { category: "character", target: "defender", type: "nope" })).toBeNull();
  });

  it("keeps the preferred variant when available and falls back otherwise", () => {
    const move = findTypeNode(tree, { category: "character", target: "defender", type: "move" })!;
    expect(pickSequence(move, "red").id).toBe("anim_character_defender_move_red");
    const idle = findTypeNode(tree, { category: "character", target: "defender", type: "idle" })!;
    expect(pickSequence(idle, "red").id).toBe("anim_character_defender_idle_blue");
    const neutral = findTypeNode(tree, { category: "ammunition", target: "rock", type: "flight" })!;
    expect(pickSequence(neutral, "blue").id).toBe("anim_ammunition_rock_flight");
  });
});

describe("playback", () => {
  it("advances whole frames and carries the remainder", () => {
    expect(advancePlayback(0, 100, 10)).toEqual({ steps: 1, accumulatedMs: 0 });
    const next = advancePlayback(30, 90, 10);
    expect(next.steps).toBe(1);
    expect(next.accumulatedMs).toBeCloseTo(20);
    expect(advancePlayback(0, 40, 10)).toEqual({ steps: 0, accumulatedMs: 40 });
  });

  it("wraps frame stepping in both directions", () => {
    expect(stepFrame(2, 3, 1)).toBe(0);
    expect(stepFrame(0, 3, -1)).toBe(2);
    expect(stepFrame(1, 3, 7)).toBe(2);
  });

  it("builds frame URLs", () => {
    expect(frameUrl("anim_spell_haste_orbiting_chevron", 4)).toBe("/api/animations/anim_spell_haste_orbiting_chevron/frames/4");
  });
});
