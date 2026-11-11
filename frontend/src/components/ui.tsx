'use client';
import { useEffect, useRef, useState } from 'react';
import { X, LoaderCircle, AlertCircle } from 'lucide-react';
export function ErrorNotice({ error }: { error: unknown }) {
  return error ? (
    <div className="notice error" role="alert">
      <AlertCircle size={17} />
      {error instanceof Error ? error.message : String(error)}
    </div>
  ) : null;
}
export function Loading() {
  return (
    <div className="loading" role="status">
      <LoaderCircle className="spin" size={20} /> Loading workspace…
    </div>
  );
}
export function Empty({ children }: { children: React.ReactNode }) {
  return <div className="empty">{children}</div>;
}
export function Badge({ value }: { value: string }) {
  return (
    <span className={`badge ${value.toLowerCase()}`}>
      <span />
      {value.charAt(0) + value.slice(1).toLowerCase()}
    </span>
  );
}
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: React.ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    ref.current?.showModal();
  }, []);
  return (
    <dialog
      ref={ref}
      onCancel={onClose}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      aria-labelledby="modal-title"
    >
      <div className="dialog-heading">
        <h2 id="modal-title">{title}</h2>
        <button className="icon-button" aria-label="Close dialog" onClick={onClose}>
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function ActionForm({
  children,
  onSubmit,
  submit = 'Save changes',
  onDone,
}: {
  children: React.ReactNode;
  onSubmit: (data: FormData) => Promise<unknown>;
  submit?: string;
  onDone?: () => void;
}) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null),
    [saved, setSaved] = useState(false);
  return (
    <form
      className="form"
      onSubmit={async (e) => {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        setBusy(true);
        setError(null);
        setSaved(false);
        try {
          await onSubmit(data);
          setSaved(true);
          onDone?.();
        } catch (err) {
          setError(err);
        } finally {
          setBusy(false);
        }
      }}
    >
      {children}
      <ErrorNotice error={error} />
      <div className="form-actions">
        {saved && !onDone && <span role="status">Saved</span>}
        <button className="button primary" disabled={busy}>
          {busy ? (
            <>
              <LoaderCircle size={16} className="spin" />
              Saving…
            </>
          ) : (
            submit
          )}
        </button>
      </div>
    </form>
  );
}
export function Field({
  label,
  name,
  type = 'text',
  defaultValue,
  required = true,
  placeholder,
  min,
  max,
  step,
  minLength,
}: {
  label: string;
  name: string;
  type?: string;
  defaultValue?: string | number;
  required?: boolean;
  placeholder?: string;
  min?: string | number;
  max?: string | number;
  step?: string;
  minLength?: number;
}) {
  return (
    <label className="field">
      {label}
      <input
        name={name}
        type={type}
        defaultValue={defaultValue}
        required={required}
        placeholder={placeholder}
        min={min}
        max={max}
        step={step}
        minLength={minLength}
        maxLength={type === 'password' ? 72 : 254}
      />
    </label>
  );
}
export function Pager({
  page,
  count,
  onChange,
}: {
  page: number;
  count: number;
  onChange: (p: number) => void;
}) {
  return (
    <div className="pager">
      <span>
        Page {page + 1} · {count} results
      </span>
      <div>
        <button className="button small" disabled={page === 0} onClick={() => onChange(page - 1)}>
          Previous
        </button>
        <button className="button small" disabled={count < 50} onClick={() => onChange(page + 1)}>
          Next
        </button>
      </div>
    </div>
  );
}
