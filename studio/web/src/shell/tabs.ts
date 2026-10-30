import type { ComponentType } from "react";
import { AnimationsTab } from "../tabs/animations/AnimationsTab";
import { BalanceTab } from "../tabs/balance/BalanceTab";
import { CampaignTab } from "../tabs/campaign/CampaignTab";
import { ProgressTab } from "../tabs/progress/ProgressTab";
import { MapsTab } from "../tabs/maps/MapsTab";

export interface StudioTab {
  id: string;
  label: string;
  component: ComponentType;
}

export const TABS: readonly StudioTab[] = [
  { id: "maps", label: "Maps", component: MapsTab },
  { id: "campaign", label: "Campaign", component: CampaignTab },
  { id: "balance", label: "Balance", component: BalanceTab },
  { id: "progress", label: "Progress", component: ProgressTab },
  { id: "animations", label: "Animations", component: AnimationsTab },
];

export function tabIdFromHash(hash: string, tabs: readonly Pick<StudioTab, "id">[] = TABS): string {
  const id = hash.replace(/^#\/?/, "");
  return tabs.some(tab => tab.id === id) ? id : tabs[0].id;
}
