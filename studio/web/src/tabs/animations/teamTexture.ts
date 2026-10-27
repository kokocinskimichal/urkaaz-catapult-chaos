export const TEAM_TEXTURE_SATURATION = 0.8;

export function desaturateTeamTexture(
  red: number,
  green: number,
  blue: number,
): readonly [number, number, number] {
  const luminance = (red * 299 + green * 587 + blue * 114) / 1000;
  return [
    luminance + (red - luminance) * TEAM_TEXTURE_SATURATION,
    luminance + (green - luminance) * TEAM_TEXTURE_SATURATION,
    luminance + (blue - luminance) * TEAM_TEXTURE_SATURATION,
  ];
}
