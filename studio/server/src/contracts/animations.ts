export type AnimationCategory = "character" | "spell" | "ammunition";

export interface AnimationSequence {
  /** Sequence stem without the frame index, e.g. `anim_character_defender_move_blue`. */
  id: string;
  category: AnimationCategory;
  target: string;
  /** Animation type; may be compound for spells and ammunition (e.g. `fragment_flight`). */
  type: string;
  /** Team variant (`blue`/`red`) or null for neutral sequences. */
  variant: string | null;
  frameIndices: number[];
  /** False when the target is not listed in the Studio animation config. */
  targetConfigured: boolean;
  /** False for sequences the game does not load (listed in the Studio config). */
  hasRuntimeLoader: boolean;
  issues: string[];
}

export interface AnimationCatalogResponse {
  sequences: AnimationSequence[];
  /** `anim_*` files that do not follow the naming convention. */
  invalidFiles: string[];
}
