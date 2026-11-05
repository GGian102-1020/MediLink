import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { Page } from '../lib/types';

export function Alert({ children, tone = 'error' }: { children: ReactNode; tone?: 'error' | 'info' }) {
  return <div role={tone === 'error' ? 'alert' : 'status'} className={`demo-alert ${tone}`}>{children}</div>;
}
export function BackLink({ to, label }: { to: string; label: string }) {
  return <Link className="text-link" to={to}>← {label}</Link>;
}
export function Badge({ value }: { value: string }) {
  return <span className="demo-badge">{value === 'BAJO_RECETA' ? 'Bajo receta' : value === 'VENTA_LIBRE' ? 'Venta libre' : value.replaceAll('_', ' ')}</span>;
}
export function Button({ children, busy = false, disabled, type = 'submit', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { busy?: boolean }) {
  return <button {...props} className={`btn btn-primary ${props.className || ''}`} type={type} disabled={busy || disabled}>{busy ? 'Procesando…' : children}</button>;
}
export function Empty({ title, detail }: { title: string; detail: string }) {
  return <div className="demo-empty"><strong>{title}</strong><p>{detail}</p></div>;
}
export function ErrorState({ error, retry }: { error: string; retry: () => void }) {
  return <div className="demo-empty"><Alert>{error}</Alert><Button type="button" onClick={retry}>Reintentar</Button></div>;
}
export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return <label className="field"><span>{label}</span>{children}{hint && <small>{hint}</small>}</label>;
}
export function Loading() { return <p role="status" className="demo-loading">Cargando…</p>; }
export function PageTitle({ eyebrow, title, subtitle }: { eyebrow: string; title: string; subtitle: string }) {
  return <div className="page-title"><div><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>{subtitle}</p></div></div>;
}
export function Pagination<T>({ data, onChange }: { data: Page<T>; onChange: (page: number) => void }) {
  return data.totalPages < 2 ? null : <nav className="demo-pagination" aria-label="Paginación">
    <button type="button" disabled={data.first} onClick={() => onChange(data.page - 1)}>Anterior</button>
    <span>Página {data.page + 1} de {data.totalPages}</span>
    <button type="button" disabled={data.last} onClick={() => onChange(data.page + 1)}>Siguiente</button>
  </nav>;
}
export function Panel({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return <section className="panel"><div className="panel-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div></div>{children}</section>;
}
