export type CoordinateAxis = "x" | "y";
export type CoordinateErrors = Record<CoordinateAxis, string>;

const INVALID_COORDINATE_MESSAGE = "Coordinates must be numbers between 0 and 1.";

export function emptyCoordinateErrors(): CoordinateErrors {
  return { x: "", y: "" };
}

export function coordinateInputError(value: string): string {
  const coordinate = Number(value);
  return value.trim() === "" ||
    !Number.isFinite(coordinate) ||
    coordinate < 0 ||
    coordinate > 1
    ? INVALID_COORDINATE_MESSAGE
    : "";
}

export function updateCoordinateErrors(
  current: CoordinateErrors,
  axis: CoordinateAxis,
  value: string,
): CoordinateErrors {
  return { ...current, [axis]: coordinateInputError(value) };
}
