import { useEffect, useState } from "react";
import { fetchHealth } from "../shared/api";
import { tabIdFromHash, TABS } from "./tabs";

function useActiveTabId(): string {
  const [tabId, setTabId] = useState(() => tabIdFromHash(window.location.hash));
  useEffect(() => {
    const onChange = () => setTabId(tabIdFromHash(window.location.hash));
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return tabId;
}

function StatusBar() {
  const [text, setText] = useState("Connecting to Studio server…");
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    fetchHealth()
      .then(health => {
        const tree = health.clean === null ? "git state unknown" : health.clean ? "clean tree" : `${health.changedFiles} changed files`;
        setText(`${health.branch ?? "no branch"} · ${tree} · ${health.repoRoot}`);
      })
      .catch(() => {
        setFailed(true);
        setText("Studio server unreachable");
      });
  }, []);
  return <footer className={failed ? "error" : undefined}>{text}</footer>;
}

export function App() {
  const activeId = useActiveTabId();
  const active = TABS.find(tab => tab.id === activeId) ?? TABS[0];
  const ActiveTab = active.component;
  return (
    <main>
      <header>
        <strong>Urkaaaz Studio</strong>
        <nav>
          {TABS.map(tab => (
            <a key={tab.id} href={`#/${tab.id}`} className={tab.id === active.id ? "active" : undefined}>
              {tab.label}
            </a>
          ))}
        </nav>
      </header>
      <section className="tab-content">
        <ActiveTab />
      </section>
      <StatusBar />
    </main>
  );
}
