import { useEffect, useRef, useState, type MouseEvent } from 'react';
import { Check, ClipboardCopy, ExternalLink, QrCode, ShieldCheck, X } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import type { AndroidDownload } from '../download';

type Props = {
  download: AndroidDownload;
  onClose: () => void;
};

/**
 * El QR se genera en el navegador, sin enviar la URL a servicios de terceros.
 * Muestra el mismo destino que el enlace: Releases hasta publicar el APK.
 */
export function AndroidDownloadDialog({ download, onClose }: Props) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const [copyStatus, setCopyStatus] = useState<'idle' | 'copied' | 'failed'>('idle');

  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    closeRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
      }
      if (event.key !== 'Tab') return;

      const elements = dialogRef.current?.querySelectorAll<HTMLElement>('button:not([disabled]), a[href]');
      if (!elements?.length) {
        event.preventDefault();
        dialogRef.current?.focus();
        return;
      }
      const first = elements[0];
      const last = elements[elements.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      } else if (!dialogRef.current?.contains(document.activeElement)) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus();
    };
  }, [onClose]);

  const handleBackdropClick = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) onClose();
  };

  const copyLink = async () => {
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Portapapeles no disponible');
      await navigator.clipboard.writeText(download.href);
      setCopyStatus('copied');
    } catch {
      setCopyStatus('failed');
    }
  };

  return (
    <div className="qr-overlay" onClick={handleBackdropClick}>
      <div
        id="android-download-dialog"
        className="qr-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="android-qr-title"
        aria-describedby="android-qr-description"
        ref={dialogRef}
        tabIndex={-1}
      >
        <button className="qr-close" type="button" ref={closeRef} onClick={onClose} aria-label="Cerrar código QR">
          <X size={22} aria-hidden="true" />
        </button>
        <div className="qr-symbol" aria-hidden="true"><QrCode size={25} /></div>
        <p className="qr-kicker">MEDILINK PARA ANDROID</p>
        <h2 id="android-qr-title">{download.published ? 'Llevá MediLink a tu celular' : 'Consultá las descargas de MediLink'}</h2>
        <p id="android-qr-description" className="qr-intro">
          {download.published
            ? 'Escaneá el QR con tu Android para descargar el APK firmado. Desde el mismo celular, usá el botón de abajo.'
            : 'Escaneá el QR para consultar GitHub Releases. Todavía no pudimos confirmar un APK público disponible desde esta página.'}
        </p>
        <div className="qr-frame">
          <QRCodeSVG
            className="qr-code"
            value={download.href}
            size={220}
            level="M"
            marginSize={4}
            bgColor="#FFFFFF"
            fgColor="#133a58"
            title={download.published ? 'Código QR del enlace de Android' : 'Código QR de GitHub Releases de MediLink'}
          />
        </div>
        <p className="qr-scan-hint">Apuntá la cámara de tu teléfono al código QR</p>
        <div className="qr-actions">
          <a className="button qr-open-link" href={download.href} target="_blank" rel="noopener noreferrer">
            <ExternalLink size={18} aria-hidden="true" />
            {download.published ? 'Descargar APK para Android' : 'Abrir GitHub Releases'}
          </a>
          <button className="button qr-copy-link" type="button" onClick={copyLink}>
            {copyStatus === 'copied' ? <Check size={18} aria-hidden="true" /> : <ClipboardCopy size={18} aria-hidden="true" />}
            {copyStatus === 'copied' ? 'Copiado' : 'Copiar enlace'}
          </button>
        </div>
        <p className="qr-copy-status" role="status" aria-live="polite">
          {copyStatus === 'failed' ? 'No se pudo copiar automáticamente. Abrí el enlace con el botón de arriba.' : copyStatus === 'copied' ? 'Enlace copiado al portapapeles.' : ''}
        </p>
        <p className="qr-security-note"><ShieldCheck size={16} aria-hidden="true" />
          {download.published
            ? 'El QR abre el enlace HTTPS de descarga. Android puede solicitar permiso para instalar aplicaciones de esta fuente.'
            : 'Este QR abre GitHub Releases. Solo se muestra un enlace directo al APK cuando podemos verificarlo.'}
        </p>
      </div>
    </div>
  );
}
