import { describe, expect, it } from "vitest";
import {
  coordinateInputError,
  emptyCoordinateErrors,
  updateCoordinateErrors,
} from "./coordinateValidation.ts";

describe("campaign coordinate validation", () => {
  it("keeps an invalid X error when Y is corrected", () => {
    const invalidX = updateCoordinateErrors(emptyCoordinateErrors(), "x", "1.2");
    const correctedY = updateCoordinateErrors(invalidX, "y", "0.5");

    expect(correctedY.x).toBe(coordinateInputError("1.2"));
    expect(correctedY.y).toBe("");
  });

  it("clears only the corrected axis error", () => {
    const invalidBoth = updateCoordinateErrors(
      updateCoordinateErrors(emptyCoordinateErrors(), "x", "-0.1"),
      "y",
      "not-a-number",
    );
    const correctedX = updateCoordinateErrors(invalidBoth, "x", "0.25");

    expect(correctedX.x).toBe("");
    expect(correctedX.y).toBe(coordinateInputError("not-a-number"));
  });
});
