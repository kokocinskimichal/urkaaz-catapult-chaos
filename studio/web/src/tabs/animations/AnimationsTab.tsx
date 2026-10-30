import type { AnimationCategory, AnimationCatalogResponse } from "@studio/contracts/animations";
import { useEffect, useMemo, useState } from "react";
import { fetchAnimationCatalog } from "../../shared/api";
import { AnimationPreview } from "./AnimationPreview";
import {
  buildTree,
  CATEGORY_ORDER,
  CATEGORY_LABELS,
  firstSelectionForCategory,
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
  const [activeCategory, setActiveCategory] = useState<AnimationCategory | null>(null);
  const [variant, setVariant] = useState<string | null>(null);
  const [team, setTeam] = useState<"blue" | "red">("blue");
  const [showMask, setShowMask] = useState(false);

  useEffect(() => {
    fetchAnimationCatalog()
      .then(response => {
        setCatalog(response);
        const nextTree = buildTree(response.sequences);
        const initialSelection = firstSelection(nextTree);
        setSelection(current => current ?? initialSelection);
        setActiveCategory(current =>
          current ?? initialSelection?.category ?? CATEGORY_ORDER[0],
        );
      })
      .catch((cause: Error) => setError(cause.message));
  }, []);

  const tree = useMemo(() => buildTree(catalog?.sequences ?? []), [catalog]);
  const selectedCategory = activeCategory ?? CATEGORY_ORDER[0];
  const categoryNode = tree.find(node => node.category === selectedCategory);
  const node = selection ? findTypeNode(tree, selection) : null;
  const sequence = node ? pickSequence(node, variant) : null;
  const variants = node ? variantsOf(node) : [];
  const usesTeamTexture = sequence?.supportsTeamTexture === true;

  const selectCategory = (category: AnimationCategory) => {
    setActiveCategory(category);
    setSelection(firstSelectionForCategory(tree, category));
  };

  if (error) return <p className="error">Could not load animations: {error}</p>;
  if (!catalog) return <p className="hint-text">Loading animation catalog…</p>;

  return (
    <div className="animations-view">
      <div className="animation-type-tabs" role="group" aria-label="Animation categories">
        {CATEGORY_ORDER.map(category => (
          <button
            key={category}
            type="button"
            aria-pressed={selectedCategory === category}
            onClick={() => selectCategory(category)}
          >
            {CATEGORY_LABELS[category]}
          </button>
        ))}
      </div>
      <div className="animations-layout">
        <aside aria-label={`${CATEGORY_LABELS[selectedCategory]} animations`}>
          {categoryNode ? categoryNode.targets.map(target => (
            <section key={target.target} className="animation-category">
              <h4>{target.target}</h4>
              <div className="animation-target">
                {target.types.map(type => {
                  const active =
                    selection?.category === selectedCategory &&
                    selection.target === target.target &&
                    selection.type === type.type;
                  const flagged = type.sequences.some(item => !item.hasRuntimeLoader || !item.targetConfigured || item.issues.length > 0);
                  return (
                    <button
                      key={type.type}
                      className={active ? "active" : undefined}
                      onClick={() => setSelection({ category: selectedCategory, target: target.target, type: type.type })}
                    >
                      {type.type}
                      {flagged && <span className="badge" title="Has warnings">!</span>}
                    </button>
                  );
                })}
              </div>
            </section>
          )) : (
            <p className="hint-text">No animations found in this category.</p>
          )}
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
          {catalog.catalogIssues.length > 0 && (
            <section className="animation-category">
              <h4>Boss presentation issues ({catalog.catalogIssues.length})</h4>
              <ul>
                {catalog.catalogIssues.map(issue => (
                  <li key={issue.path} className="error"><code>{issue.path}</code>: {issue.message}</li>
                ))}
              </ul>
            </section>
          )}
        </aside>
        <div className="animation-main">
          {usesTeamTexture ? (
            <div className="variant-switch" aria-label="Team texture">
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
          {sequence ? <AnimationPreview sequence={sequence} team={team} showMask={usesTeamTexture && showMask} /> : <p className="hint-text">No animations found.</p>}
        </div>
      </div>
    </div>
  );
}
