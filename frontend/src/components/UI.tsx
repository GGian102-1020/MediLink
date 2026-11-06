import { ArrowLeft, ArrowRight, Check, ChevronLeft, ChevronRight, CircleAlert, Inbox, LoaderCircle, RefreshCcw, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { readable } from '../lib/format';
import type { Page } from '../lib/types';

export function Button({ children, variant = 'primary', busy, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' | 'danger'; busy?: boolean }) {
  return <button {...props} disabled={props.disabled || busy} className={`btn btn-${variant} ${props.className || ''}`}>
    {busy && <LoaderCircle size={16} className="spin" />} {children}
  </button>;
}
export function PageTitle({ eyebrow, title, subtitle, action }: { eyebrow?: string; title: string; subtitle?: string; action?: ReactNode }) {
  return <div className="page-title"><div><div className="eyebrow">{eyebrow || 'MEDILINK / PORTAL'}</div><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{action && <div className="page-action">{action}</div>}</div>;
}
export function Panel({ children, className = '', title, subtitle, action }: { children: ReactNode; className?: string; title?: string; subtitle?: string; action?: ReactNode }) {
  return <section className={`panel ${className}`}>{title && <div className="panel-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div>{action}</div>}{children}</section>;
}
export function Alert({ children, tone = 'error' }: { children: ReactNode; tone?: 'error' | 'info' | 'success' }) { return <div role="alert" className={`alert alert-${tone}`}><CircleAlert size={18} /><span>{children}</span></div>; }
export function Loading({ text = 'Cargando información…' }: { text?: string }) { return <div className="loading"><LoaderCircle size={23} className="spin" /><span>{text}</span></div>; }
export function Empty({ title = 'Todavía no hay resultados', detail = 'Probá cambiar los filtros o volver más tarde.' }: { title?: string; detail?: string }) { return <div className="empty"><div className="empty-icon"><Inbox size={28}/></div><strong>{title}</strong><p>{detail}</p></div>; }
export function ErrorState({ error, retry }: { error: string; retry?: () => void }) { return <div className="error-state"><Alert>{error}</Alert>{retry && <Button variant="secondary" onClick={retry}><RefreshCcw size={15} /> Reintentar</Button>}</div>; }
export function Pagination({ data, onChange }: { data: Pick<Page<unknown>, 'page' | 'totalElements' | 'totalPages'>; onChange: (page: number) => void }) {
  if (data.totalPages < 2) return null;
  return <div className="pagination"><span>{data.totalElements} resultados · Página {data.page + 1} de {data.totalPages}</span><div><Button variant="secondary" disabled={data.page === 0} onClick={() => onChange(data.page - 1)} aria-label="Página anterior"><ChevronLeft size={17}/></Button><Button variant="secondary" disabled={data.page >= data.totalPages - 1} onClick={() => onChange(data.page + 1)} aria-label="Página siguiente"><ChevronRight size={17}/></Button></div></div>;
}
export function Badge({ value, tone }: { value: string; tone?: 'success' | 'warning' | 'danger' | 'neutral' | 'info' }) {
  const t = tone || ({ ACTIVA: 'success', ACTIVO: 'success', COMPLETADA: 'info', HABILITADA: 'success', VENCIDA: 'warning', CANCELADA: 'danger', INACTIVA: 'neutral', DESHABILITADA: 'danger', ANULADA: 'danger', BAJO_RECETA: 'warning', VENTA_LIBRE: 'info' } as Record<string, 'success'|'warning'|'danger'|'neutral'|'info'>)[value] || 'neutral';
  return <span className={`badge badge-${t}`}><span className="badge-dot" />{readable(value)}</span>;
}
export function Field({ label, hint, children, className = '' }: { label: string; hint?: string; children: ReactNode; className?: string }) { return <label className={`field ${className}`}><span className="field-label">{label}</span>{children}{hint && <small>{hint}</small>}</label>; }
export function ConfirmDialog({ title, message, confirmText = 'Confirmar', onClose, onConfirm, busy, danger = false }: { title: string; message: string; confirmText?: string; onClose: () => void; onConfirm: () => void; busy?: boolean; danger?: boolean }) {
  return <div className="modal-backdrop" role="presentation" onMouseDown={e => { if (e.target === e.currentTarget && !busy) onClose(); }}><div className="modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title"><button className="icon-button modal-close" aria-label="Cerrar" onClick={onClose} disabled={busy}><X size={20}/></button><div className="modal-symbol"><CircleAlert size={23}/></div><h2 id="confirm-title">{title}</h2><p>{message}</p><div className="modal-actions"><Button variant="secondary" onClick={onClose} disabled={busy}>Volver</Button><Button variant={danger ? 'danger' : 'primary'} busy={busy} onClick={onConfirm}><Check size={16}/>{confirmText}</Button></div></div></div>;
}
export function BackLink({ to, label = 'Volver' }: { to: string; label?: string }) { return <Link className="back-link" to={to}><ArrowLeft size={15}/>{label}</Link>; }
export function TextLink({ to, children }: { to: string; children: ReactNode }) { return <Link to={to} className="text-link">{children}<ArrowRight size={15}/></Link>; }
export function Stat({ label, value, icon, detail }: { label: string; value: string | number; icon: ReactNode; detail?: string }) { return <div className="stat-card"><div className="stat-icon">{icon}</div><div className="stat-label">{label}</div><div className="stat-value">{value}</div>{detail && <div className="stat-detail">{detail}</div>}</div>; }
export function notify(setter: (v: string) => void, message = 'Los cambios se guardaron correctamente.') { setter(message); }
