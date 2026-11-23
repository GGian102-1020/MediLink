import { ArrowLeft, ArrowRight, Check, ChevronLeft, ChevronRight, CircleAlert, Inbox, LoaderCircle, RefreshCcw, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useEffect, useRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { readable } from '../lib/format';
import type { Page } from '../lib/types';

export function Button({ children, variant = 'primary', busy, size, block, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' | 'danger'; busy?: boolean; size?: 'sm'; block?: boolean }) {
  return <button {...props} disabled={props.disabled || busy} className={`btn btn-${variant} ${size === 'sm' ? 'btn-sm' : ''} ${block ? 'btn-block' : ''} ${props.className || ''}`}>
    {busy && <LoaderCircle size={16} className="spin" />} {children}
  </button>;
}
export function PageTitle({ eyebrow, title, subtitle, action }: { eyebrow?: string; title: string; subtitle?: string; action?: ReactNode }) {
  return <div className="page-title"><div><div className="eyebrow">{eyebrow || 'MEDIFARM / PORTAL'}</div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{action && <div className="page-action">{action}</div>}</div>;
}
export function Panel({ children, className = '', title, subtitle, action }: { children: ReactNode; className?: string; title?: string; subtitle?: string; action?: ReactNode }) {
  return <section className={`panel ${className}`}>{title && <div className="panel-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{action}</div>}{children}</section>;
}
export function Alert({ children, tone = 'error' }: { children: ReactNode; tone?: 'error' | 'info' | 'success' }) { return <div role="alert" className={`alert alert-${tone}`}><CircleAlert size={18} /><span>{children}</span></div>; }
export function Loading({ text = 'Cargando información…' }: { text?: string }) { return <div className="loading"><LoaderCircle size={23} className="spin" /><span>{text}</span></div>; }
export function Empty({ title = 'Todavía no hay resultados', detail = 'Probá cambiar los filtros o volver más tarde.', icon, action, compact }: { title?: string; detail?: string; icon?: ReactNode; action?: ReactNode; compact?: boolean }) { return <div className={`empty ${compact ? 'compact' : ''}`}><div className="empty-icon">{icon || <Inbox size={28}/>}</div><strong>{title}</strong><p>{detail}</p>{action}</div>; }
export function ErrorState({ error, retry }: { error: string; retry?: () => void }) { return <div className="error-state"><Alert>{error}</Alert>{retry && <Button variant="secondary" onClick={retry}><RefreshCcw size={15} /> Reintentar</Button>}</div>; }
export function Pagination({ data, onChange }: { data: Pick<Page<unknown>, 'page' | 'totalElements' | 'totalPages'>; onChange: (page: number) => void }) {
  if (data.totalPages < 2) return null;
  return <div className="pagination"><span>{data.totalElements} resultados · Página {data.page + 1} de {data.totalPages}</span><div><Button variant="secondary" disabled={data.page === 0} onClick={() => onChange(data.page - 1)} aria-label="Página anterior"><ChevronLeft size={17}/></Button><Button variant="secondary" disabled={data.page >= data.totalPages - 1} onClick={() => onChange(data.page + 1)} aria-label="Página siguiente"><ChevronRight size={17}/></Button></div></div>;
}
export function Badge({ value, tone, label, dot = true }: { value: string; tone?: 'success' | 'warning' | 'danger' | 'neutral' | 'info'; label?: string; dot?: boolean }) {
  const t = tone || ({ PENDIENTE: 'warning', ACTIVA: 'success', ACTIVO: 'success', COMPLETADA: 'info', HABILITADA: 'success', VENCIDA: 'warning', CANCELADA: 'danger', INACTIVA: 'neutral', DESHABILITADA: 'danger', ANULADA: 'danger', BAJO_RECETA: 'warning', VENTA_LIBRE: 'info' } as Record<string, 'success'|'warning'|'danger'|'neutral'|'info'>)[value] || 'neutral';
  return <span className={`badge badge-${t}`}>{dot && <span className="badge-dot" />}{label || readable(value)}</span>;
}
export function Field({ label, hint, children, className = '' }: { label: string; hint?: string; children: ReactNode; className?: string }) { return <label className={`field ${className}`}><span className="field-label">{label}</span>{children}{hint && <small className="field-hint">{hint}</small>}</label>; }
export function ConfirmDialog({ title, message, confirmText = 'Confirmar', onClose, onConfirm, busy, danger = false }: { title: string; message: string; confirmText?: string; onClose: () => void; onConfirm: () => void; busy?: boolean; danger?: boolean }) {
  return <div className="modal-backdrop" role="presentation" onMouseDown={e => { if (e.target === e.currentTarget && !busy) onClose(); }}><div className="modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title"><button className="icon-button modal-close" aria-label="Cerrar" onClick={onClose} disabled={busy}><X size={20}/></button><div className="modal-symbol"><CircleAlert size={23}/></div><h2 id="confirm-title">{title}</h2><p>{message}</p><div className="modal-actions"><Button variant="secondary" onClick={onClose} disabled={busy}>Volver</Button><Button variant={danger ? 'danger' : 'primary'} busy={busy} onClick={onConfirm}><Check size={16}/>{confirmText}</Button></div></div></div>;
}

export function IconButton({ label, children, bordered, className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string; bordered?: boolean }) {
  return <button type="button" {...props} aria-label={label} title={label} className={`icon-btn ${bordered ? 'bordered' : ''} ${className}`}>{children}</button>;
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return <div className="page-header"><div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{actions && <div className="page-actions">{actions}</div>}</div>;
}

export function SkeletonList({ rows = 4 }: { rows?: number }) {
  return <div className="skeleton-list" aria-busy="true" aria-label="Cargando">{Array.from({ length: rows }, (_, i) => <div className="skeleton-row" key={i}><div className="sk" style={{ width: 32, height: 32, borderRadius: 9 }}/><div style={{ flex: 1, display: 'grid', gap: 6 }}><div className="sk" style={{ height: 11, width: `${55 - i * 6}%` }}/><div className="sk" style={{ height: 9, width: `${35 - i * 3}%` }}/></div><div className="sk" style={{ height: 11, width: 60 }}/></div>)}</div>;
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

export function Kpi({ label, value, hint, icon, tone = 'accent', to }: { label: string; value: ReactNode; hint?: string; icon: ReactNode; tone?: 'accent' | 'success' | 'danger' | 'warning'; to?: string }) {
  const body = <><div className="kpi-top"><span className="kpi-label">{label}</span><span className="kpi-icon">{icon}</span></div><div className="kpi-value">{value}</div>{hint && <div className="kpi-hint">{hint}</div>}</>;
  return to ? <Link to={to} className={`kpi tone-${tone}`}>{body}</Link> : <div className={`kpi tone-${tone}`}>{body}</div>;
}
