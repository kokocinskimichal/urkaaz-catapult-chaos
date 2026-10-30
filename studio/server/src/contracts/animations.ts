export type AnimationCategory = "character" | "spell" | "ammunition" | "boss";

export interface AnimationSequence {
  /** Sequence stem without the frame index, e.g. `anim_character_defender_move`. */
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
  /** True when the sequence is recolored from shared team-texture assets. */
  supportsTeamTexture: boolean;
  issues: string[];
}

export interface AnimationCatalogResponse {
  sequences: AnimationSequence[];
  /** `anim_*` files that do not follow the naming convention. */
  invalidFiles: string[];
  /** Boss profiles whose configured presentation key has no animation or static frame. */
  catalogIssues: { path: string; message: string }[];
}
