import type { ComponentType } from "react";
import { AnimationsTab } from "../tabs/animations/AnimationsTab";
import { MapsTab } from "../tabs/maps/MapsTab";

export interface StudioTab {
  id: string;
  label: string;
  component: ComponentType;
}

export const TABS: readonly StudioTab[] = [
  { id: "maps", label: "Maps", component: MapsTab },
  { id: "animations", label: "Animations", component: AnimationsTab },
];

export function tabIdFromHash(hash: string, tabs: readonly Pick<StudioTab, "id">[] = TABS): string {
  const id = hash.replace(/^#\/?/, "");
  return tabs.some(tab => tab.id === id) ? id : tabs[0].id;
}
