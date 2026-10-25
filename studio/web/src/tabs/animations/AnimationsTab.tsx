import type { AnimationCatalogResponse } from "@studio/contracts/animations";
import { useEffect, useMemo, useState } from "react";
import { fetchAnimationCatalog } from "../../shared/api";
import { AnimationPreview } from "./AnimationPreview";
import {
  buildTree,
  CATEGORY_LABELS,
  findTypeNode,
  firstSelection,
  pickSequence,
  variantsOf,
  type Selection,
} from "./model";

export function AnimationsTab() {
  const [catalog, setCatalog] = useState<AnimationCatalogResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selection, setSelection] = useState<Selection | null>(null);
  const [variant, setVariant] = useState<string | null>(null);
  const [team, setTeam] = useState<"blue" | "red">("blue");
  const [showMask, setShowMask] = useState(false);

  useEffect(() => {
    fetchAnimationCatalog()
      .then(response => {
        setCatalog(response);
        setSelection(current => current ?? firstSelection(buildTree(response.sequences)));
      })
      .catch((cause: Error) => setError(cause.message));
  }, []);

  const tree = useMemo(() => buildTree(catalog?.sequences ?? []), [catalog]);
  const node = selection ? findTypeNode(tree, selection) : null;
  const sequence = node ? pickSequence(node, variant) : null;
  const variants = node ? variantsOf(node) : [];
  const isDefender = sequence?.category === "character" && sequence.target === "defender";

  if (error) return <p className="error">Could not load animations: {error}</p>;
  if (!catalog) return <p className="hint-text">Loading animation catalog…</p>;

  return (
    <div className="animations-layout">
      <aside>
        {tree.map(category => (
          <section key={category.category} className="animation-category">
            <h4>{CATEGORY_LABELS[category.category]}</h4>
            {category.targets.map(target => (
              <div key={target.target} className="animation-target">
                <strong>{target.target}</strong>
                {target.types.map(type => {
                  const active =
                    selection?.category === category.category && selection.target === target.target && selection.type === type.type;
                  const flagged = type.sequences.some(item => !item.hasRuntimeLoader || !item.targetConfigured || item.issues.length > 0);
                  return (
                    <button
                      key={type.type}
                      className={active ? "active" : undefined}
                      onClick={() => setSelection({ category: category.category, target: target.target, type: type.type })}
                    >
                      {type.type}
                      {flagged && <span className="badge" title="Has warnings">!</span>}
                    </button>
                  );
                })}
              </div>
            ))}
          </section>
        ))}
        {catalog.invalidFiles.length > 0 && (
          <section className="animation-category">
            <h4>Nonconforming files ({catalog.invalidFiles.length})</h4>
            <ul>
              {catalog.invalidFiles.map(name => (
                <li key={name} className="error">{name}</li>
              ))}
            </ul>
          </section>
        )}
      </aside>
      <div className="animation-main">
        {isDefender ? (
          <div className="variant-switch" aria-label="Defender team texture">
            {(["blue", "red"] as const).map(name => (
              <button key={name} className={team === name ? "active" : undefined} onClick={() => setTeam(name)}>
                {name}
              </button>
            ))}
            <button className={showMask ? "active" : undefined} onClick={() => setShowMask(value => !value)}>
              {showMask ? "Hide mask" : "Show mask"}
            </button>
          </div>
        ) : variants.length > 0 && (
          <div className="variant-switch">
            {variants.map(name => (
              <button key={name} className={sequence?.variant === name ? "active" : undefined} onClick={() => setVariant(name)}>
                {name}
              </button>
            ))}
          </div>
        )}
        {sequence ? <AnimationPreview sequence={sequence} team={team} showMask={isDefender && showMask} /> : <p className="hint-text">No animations found.</p>}
      </div>
    </div>
  );
}
