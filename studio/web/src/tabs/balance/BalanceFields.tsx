import type { NumericInputDraft } from "./model";
import "./styles.css";

interface BaseFieldProps {
  path: string;
  label: string;
  hint?: string;
  error?: string;
}

interface NumberFieldProps extends BaseFieldProps {
  value: NumericInputDraft;
  unit?: string;
  min?: number;
  max?: number;
  step?: number;
  onChange: (value: NumericInputDraft) => void;
}

export function NumberField({
  path,
  label,
  value,
  unit,
  min,
  max,
  step,
  hint,
  error,
  onChange,
}: NumberFieldProps) {
  const id = fieldId(path);
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;
  const range = [
    min === undefined ? null : `min ${min}`,
    max === undefined ? null : `max ${max}`,
    step === undefined ? null : `step ${step}`,
  ].filter(Boolean).join(", ");

  return (
    <label className="balance-field" htmlFor={id}>
      <span className="balance-field-label">
        {label}
        {unit && <small>{unit}</small>}
      </span>
      <input
        id={id}
        type="text"
        inputMode="decimal"
        value={value.text}
        aria-invalid={error ? "true" : "false"}
        aria-describedby={describedBy}
        onChange={event => onChange({
          __numberInputDraft: true,
          text: event.currentTarget.value,
        })}
      />
      {hint && <small id={hintId} className="balance-field-hint">{hint}</small>}
      {range && <small className="balance-field-range">{range}</small>}
      {error && <small id={errorId} className="balance-field-error">{error}</small>}
    </label>
  );
}

interface TextFieldProps extends BaseFieldProps {
  value: string;
  readOnly?: boolean;
  onChange?: (value: string) => void;
}

export function TextField({
  path,
  label,
  value,
  hint,
  error,
  readOnly = false,
  onChange,
}: TextFieldProps) {
  const id = fieldId(path);
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;

  return (
    <label className="balance-field" htmlFor={id}>
      <span className="balance-field-label">{label}</span>
      <input
        id={id}
        type="text"
        value={value}
        readOnly={readOnly}
        aria-invalid={error ? "true" : "false"}
        aria-describedby={describedBy}
        onChange={event => onChange?.(event.currentTarget.value)}
      />
      {hint && <small id={hintId} className="balance-field-hint">{hint}</small>}
      {error && <small id={errorId} className="balance-field-error">{error}</small>}
    </label>
  );
}

interface SelectFieldProps extends BaseFieldProps {
  value: string;
  options: readonly string[];
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
}

export function SelectField({
  path,
  label,
  value,
  options,
  hint,
  error,
  placeholder = "Choose…",
  disabled = false,
  onChange,
}: SelectFieldProps) {
  const id = fieldId(path);
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;

  return (
    <label className="balance-field" htmlFor={id}>
      <span className="balance-field-label">{label}</span>
      <select
        id={id}
        value={value}
        disabled={disabled}
        aria-invalid={error ? "true" : "false"}
        aria-describedby={describedBy}
        onChange={event => onChange(event.currentTarget.value)}
      >
        {(value === "" || value === null) && <option value="">{placeholder}</option>}
        {options.map(option => (
          <option value={option} key={option}>{enumLabel(option)}</option>
        ))}
      </select>
      {hint && <small id={hintId} className="balance-field-hint">{hint}</small>}
      {error && <small id={errorId} className="balance-field-error">{error}</small>}
    </label>
  );
}

interface BooleanFieldProps extends BaseFieldProps {
  value: boolean | null;
  onChange: (value: boolean | null) => void;
}

export function BooleanField({ path, label, value, hint, error, onChange }: BooleanFieldProps) {
  const id = fieldId(path);
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;

  return (
    <label className="balance-field" htmlFor={id}>
      <span className="balance-field-label">{label}</span>
      <select
        id={id}
        value={value === null ? "" : String(value)}
        aria-invalid={error ? "true" : "false"}
        aria-describedby={describedBy}
        onChange={event => {
          const next = event.currentTarget.value;
          onChange(next === "" ? null : next === "true");
        }}
      >
        <option value="">Choose…</option>
        <option value="true">Yes</option>
        <option value="false">No</option>
      </select>
      {hint && <small id={hintId} className="balance-field-hint">{hint}</small>}
      {error && <small id={errorId} className="balance-field-error">{error}</small>}
    </label>
  );
}

interface MultiSelectFieldProps extends BaseFieldProps {
  value: readonly string[];
  options: readonly string[];
  onChange: (value: string[]) => void;
}

export function MultiSelectField({
  path,
  label,
  value,
  options,
  hint,
  error,
  onChange,
}: MultiSelectFieldProps) {
  const id = fieldId(path);
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;

  return (
    <fieldset
      id={id}
      className="balance-multiselect"
      tabIndex={-1}
      aria-describedby={describedBy}
      aria-invalid={error ? "true" : "false"}
    >
      <legend>{label}</legend>
      <div className="balance-multiselect-options">
        {options.map(option => (
          <label key={option}>
            <input
              type="checkbox"
              checked={value.includes(option)}
              onChange={event => {
                const selected = new Set(value);
                if (event.currentTarget.checked) selected.add(option);
                else selected.delete(option);
                onChange(options.filter(item => selected.has(item)));
              }}
            />
            <span>{enumLabel(option)}</span>
          </label>
        ))}
      </div>
      {hint && <small id={hintId} className="balance-field-hint">{hint}</small>}
      {error && <small id={errorId} className="balance-field-error">{error}</small>}
    </fieldset>
  );
}

export function FieldGroup({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: React.ReactNode;
}) {
  return (
    <fieldset className="balance-field-group">
      <legend>{title}</legend>
      {description && <p>{description}</p>}
      <div className="balance-field-grid">{children}</div>
    </fieldset>
  );
}

function fieldId(path: string): string {
  return `balance-${path.replace(/[^a-zA-Z0-9_-]/g, "-")}`;
}

function enumLabel(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map(word => `${word[0]?.toUpperCase() ?? ""}${word.slice(1)}`)
    .join(" ");
}
