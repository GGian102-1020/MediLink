import { AlertTriangle, ChevronLeft, ChevronRight, CircleAlert, CircleCheck, Inbox, Info, LoaderCircle, RefreshCcw, X } from 'lucide-react';
import { useEffect, useRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { readable } from '../lib/format';
import type { Page } from '../lib/types';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
export function Button({ children, variant = 'primary', size, block, busy, className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: 'sm'; block?: boolean; busy?: boolean }) {
  return <button {...props} disabled={props.disabled || busy} className={`btn btn-${variant} ${size === 'sm' ? 'btn-sm' : ''} ${block ? 'btn-block' : ''} ${className}`}>
    {busy && <LoaderCircle size={15} className="spin"/>}{children}
  </button>;
}
export function IconButton({ label, children, bordered, className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string; bordered?: boolean }) {
  return <button type="button" {...props} aria-label={label} title={label} className={`icon-btn ${bordered ? 'bordered' : ''} ${className}`}>{children}</button>;
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return <div className="page-header"><div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{actions && <div className="page-actions">{actions}</div>}</div>;
}
export function Panel({ title, subtitle, action, children, className = '' }: { title?: string; subtitle?: string; action?: ReactNode; children: ReactNode; className?: string }) {
  return <section className={`panel ${className}`}>{title && <div className="panel-head"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{action}</div>}{children}</section>;
}

export function Alert({ children, tone = 'error' }: { children: ReactNode; tone?: 'error' | 'info' | 'success' }) {
  const Icon = tone === 'success' ? CircleCheck : tone === 'info' ? Info : CircleAlert;
  return <div role={tone === 'error' ? 'alert' : 'status'} className={`alert alert-${tone}`}><Icon size={17}/><span>{children}</span></div>;
}
export function Loading({ text = 'Cargando…' }: { text?: string }) { return <div className="loading"><LoaderCircle size={18} className="spin"/><span>{text}</span></div>; }
export function SkeletonList({ rows = 4 }: { rows?: number }) {
  return <div className="skeleton-list" aria-busy="true" aria-label="Cargando">{Array.from({ length: rows }, (_, i) => <div className="skeleton-row" key={i}><div className="sk" style={{ width: 32, height: 32, borderRadius: 9 }}/><div style={{ flex: 1, display: 'grid', gap: 6 }}><div className="sk" style={{ height: 11, width: `${55 - i * 6}%` }}/><div className="sk" style={{ height: 9, width: `${35 - i * 3}%` }}/></div><div className="sk" style={{ height: 11, width: 60 }}/></div>)}</div>;
}
export function Empty({ title = 'Sin resultados', detail, icon, action, compact }: { title?: string; detail?: string; icon?: ReactNode; action?: ReactNode; compact?: boolean }) {
  return <div className={`empty ${compact ? 'compact' : ''}`}><div className="empty-icon">{icon || <Inbox size={20}/>}</div><strong>{title}</strong>{detail && <p>{detail}</p>}{action}</div>;
}
export function ErrorState({ error, retry }: { error: string; retry?: () => void }) {
  return <div className="error-state"><Alert>{error}</Alert>{retry && <Button variant="secondary" size="sm" onClick={retry}><RefreshCcw size={14}/> Reintentar</Button>}</div>;
}
export function Pagination({ data, onChange }: { data: Pick<Page<unknown>, 'page' | 'totalElements' | 'totalPages'>; onChange: (page: number) => void }) {
  if (data.totalPages < 2) return null;
  return <div className="pagination"><span className="num">Página {data.page + 1} de {data.totalPages} · {data.totalElements} resultados</span><div>
    <IconButton label="Página anterior" bordered disabled={data.page === 0} onClick={() => onChange(data.page - 1)}><ChevronLeft size={16}/></IconButton>
    <IconButton label="Página siguiente" bordered disabled={data.page >= data.totalPages - 1} onClick={() => onChange(data.page + 1)}><ChevronRight size={16}/></IconButton>
  </div></div>;
}

type Tone = 'success' | 'warning' | 'danger' | 'neutral' | 'info';
const tones: Record<string, Tone> = { ACTIVA: 'info', COMPLETADA: 'success', VENCIDA: 'warning', CANCELADA: 'danger', ACTIVO: 'success', HABILITADA: 'success', INACTIVA: 'neutral', DESHABILITADA: 'danger' };
export function Badge({ value, tone, label, dot = true }: { value: string; tone?: Tone; label?: string; dot?: boolean }) {
  return <span className={`badge badge-${tone || tones[value] || 'neutral'}`}>{dot && <span className="badge-dot"/>}{label || readable(value)}</span>;
}
export function Field({ label, hint, children, className = '' }: { label: string; hint?: string; children: ReactNode; className?: string }) {
  return <label className={`field ${className}`}><span className="field-label">{label}</span>{children}{hint && <small className="field-hint">{hint}</small>}</label>;
}
export function Segmented<T extends string>({ options, value, onChange, label }: { options: { value: T; label: string; count?: number }[]; value: T; onChange: (v: T) => void; label: string }) {
  return <div className="segmented" role="tablist" aria-label={label}>{options.map(o => <button key={o.value} type="button" role="tab" aria-selected={value === o.value} className={value === o.value ? 'active' : ''} onClick={() => onChange(o.value)}>{o.label}{o.count !== undefined && <span className="seg-count">{o.count}</span>}</button>)}</div>;
}

/** Bloquea el scroll del fondo y cierra con Escape mientras el modal está abierto. */
function useModal(onClose: () => void, blocked: boolean) {
  const ref = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose); closeRef.current = onClose;
  const blockedRef = useRef(blocked); blockedRef.current = blocked;
  useEffect(() => {
    const prevFocus = document.activeElement as HTMLElement | null;
    ref.current?.focus();
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape' && !blockedRef.current) { e.stopPropagation(); closeRef.current(); } };
    document.addEventListener('keydown', esc);
    const prev = document.body.style.overflow; document.body.style.overflow = 'hidden';
    return () => { document.removeEventListener('keydown', esc); document.body.style.overflow = prev; prevFocus?.focus?.(); };
  }, []);
  return ref;
}
export function Modal({ title, eyebrow, onClose, blocked = false, children, footer, size }: { title: ReactNode; eyebrow?: string; onClose: () => void; blocked?: boolean; children: ReactNode; footer?: ReactNode; size?: 'sm' }) {
  const ref = useModal(onClose, blocked);
  return <div className="modal-backdrop" role="presentation" onMouseDown={e => { if (e.target === e.currentTarget && !blocked) onClose(); }}>
    <div ref={ref} tabIndex={-1} className={`modal ${size || ''}`} role="dialog" aria-modal="true" aria-label={typeof title === 'string' ? title : eyebrow}>
      <div className="modal-head"><div>{eyebrow && <small>{eyebrow}</small>}<h2>{title}</h2></div><IconButton label="Cerrar" onClick={onClose} disabled={blocked}><X size={18}/></IconButton></div>
      <div className="modal-body">{children}</div>
      {footer && <div className="modal-foot">{footer}</div>}
    </div>
  </div>;
}
export function ConfirmDialog({ title, message, confirmText = 'Confirmar', onClose, onConfirm, busy, danger = false }: { title: string; message: string; confirmText?: string; onClose: () => void; onConfirm: () => void; busy?: boolean; danger?: boolean }) {
  const ref = useModal(onClose, !!busy);
  return <div className="modal-backdrop top" role="presentation" onMouseDown={e => { if (e.target === e.currentTarget && !busy) onClose(); }}>
    <div ref={ref} tabIndex={-1} className="modal sm" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title">
      <div className={`modal-confirm ${danger ? 'danger' : ''}`}><div className="confirm-icon">{danger ? <AlertTriangle size={19}/> : <CircleCheck size={19}/>}</div><h2 id="confirm-title">{title}</h2><p>{message}</p></div>
      <div className="modal-foot"><Button variant="secondary" onClick={onClose} disabled={busy}>Volver</Button><Button variant={danger ? 'danger' : 'primary'} busy={busy} onClick={onConfirm}>{confirmText}</Button></div>
    </div>
  </div>;
}

export function Kpi({ label, value, hint, icon, tone = 'accent', to }: { label: string; value: ReactNode; hint?: string; icon: ReactNode; tone?: 'accent' | 'success' | 'danger' | 'warning'; to?: string }) {
  const body = <><div className="kpi-top"><span className="kpi-label">{label}</span><span className="kpi-icon">{icon}</span></div><div className="kpi-value">{value}</div>{hint && <div className="kpi-hint">{hint}</div>}</>;
  return to ? <Link to={to} className={`kpi tone-${tone}`}>{body}</Link> : <div className={`kpi tone-${tone}`}>{body}</div>;
}
