import { useCallback, useEffect, useState } from 'react';
import {
  ArrowDown,
  ArrowRight,
  CalendarCheck2,
  Check,
  ChevronRight,
  HeartPulse,
  MapPin,
  PackageCheck,
  QrCode,
  Pill,
  Search,
  ShieldCheck,
  Smartphone,
} from 'lucide-react';
import { AndroidDownloadDialog } from './components/AndroidDownloadDialog';
import { findPublishedAndroidApk, resolveAndroidDownload, type AndroidDownload } from './download';

const configuredDownload = resolveAndroidDownload(import.meta.env.VITE_ANDROID_DOWNLOAD_URL);

const steps = [
  {
    number: '01',
    icon: Search,
    title: 'Buscá tu medicamento',
    description: 'Encontrá el medicamento que necesitás y seleccioná su presentación desde la app.',
  },
  {
    number: '02',
    icon: MapPin,
    title: 'Elegí dónde encontrarlo',
    description: 'Consultá la disponibilidad en farmacias adheridas y revisá su ubicación.',
  },
  {
    number: '03',
    icon: CalendarCheck2,
    title: 'Reservá y retiralo',
    description: 'Realizá una reserva, consultá su estado y acercate a la farmacia para el retiro.',
  },
];

function Brand({ light = false }: { light?: boolean }) {
  return (
    <a className={`brand${light ? ' brand-light' : ''}`} href="#inicio" aria-label="MediLink, volver al inicio">
      <img src="/medilink-mark.png" width="43" height="43" alt="" aria-hidden="true" />
      <span>Medi<span className="brand-accent">Link</span></span>
    </a>
  );
}

function DownloadButton({ onClick, isOpen, published }: { onClick: () => void; isOpen: boolean; published: boolean }) {
  return (
    <button
      className="button button-white"
      type="button"
      onClick={onClick}
      aria-haspopup="dialog"
      aria-controls={isOpen ? 'android-download-dialog' : undefined}
      aria-expanded={isOpen}
    >
      <QrCode size={20} aria-hidden="true" />
      <span>{published ? 'Descargar Android con QR' : 'Ver QR y versiones'}</span>
      <ArrowRight size={19} aria-hidden="true" />
    </button>
  );
}

function PhonePreview() {
  return (
    <div className="preview-area" aria-hidden="true">
      <div className="preview-orbit preview-orbit-one" />
      <div className="preview-orbit preview-orbit-two" />
      <div className="floating-pill floating-pill-top">
        <span className="pill-icon pill-icon-teal"><Check size={19} /></span>
        <span><b>Más simple</b><small>Desde tu celular</small></span>
      </div>
      <div className="phone">
        <div className="phone-sensor" />
        <div className="phone-screen">
          <div className="phone-topline"><span>9:41</span><span className="phone-signal">● ● ●</span></div>
          <div className="phone-heading"><span className="phone-greeting">TU SALUD, MÁS CERCA</span><div className="phone-profile"><HeartPulse size={18} /></div></div>
          <h2>¿Qué necesitás<br />encontrar hoy?</h2>
          <div className="phone-search"><Search size={17} /><span>Buscar medicamento...</span></div>
          <div className="phone-card-title">Cómo te ayudamos <ChevronRight size={16} /></div>
          <div className="phone-feature"><span className="phone-feature-icon blue"><Pill size={21} /></span><span><b>Buscá medicamentos</b><small>Encontrá presentaciones</small></span><ArrowRight size={16} /></div>
          <div className="phone-feature"><span className="phone-feature-icon green"><MapPin size={21} /></span><span><b>Consultá farmacias</b><small>Revisá la disponibilidad</small></span><ArrowRight size={16} /></div>
          <div className="phone-banner"><span className="phone-banner-icon"><PackageCheck size={23} /></span><span><b>Reservá desde la app</b><small>Y retirá en la farmacia</small></span></div>
          <div className="phone-bottom"><span className="phone-bottom-active" /><span /><span /></div>
        </div>
      </div>
      <div className="floating-pill floating-pill-bottom">
        <span className="pill-icon pill-icon-blue"><MapPin size={20} /></span>
        <span><b>Farmacias adheridas</b><small>Consultá disponibilidad</small></span>
      </div>
      <span className="preview-caption">Vista ilustrativa de la experiencia</span>
    </div>
  );
}

export default function App() {
  const [isQrOpen, setQrOpen] = useState(false);
  const [download, setDownload] = useState<AndroidDownload>(configuredDownload);
  const [checkingRelease, setCheckingRelease] = useState(!configuredDownload.published);
  const closeQr = useCallback(() => setQrOpen(false), []);

  useEffect(() => {
    if (configuredDownload.published) return;
    let active = true;
    void findPublishedAndroidApk().then((found) => {
      if (!active) return;
      if (found) setDownload(found);
      setCheckingRelease(false);
    });
    return () => { active = false; };
  }, []);

  return (
    <>
      <a className="skip-link" href="#contenido">Ir al contenido</a>
      <header className="site-header">
        <div className="container header-inner">
          <Brand />
          <nav aria-label="Navegación principal">
            <a href="#inicio">Inicio</a>
            <a href="#como-funciona">Cómo funciona</a>
          </nav>
          <a className="header-download" href="#descargar"><Smartphone size={17} aria-hidden="true" /> Descargar app <ArrowRight size={16} aria-hidden="true" /></a>
        </div>
      </header>

      <main id="contenido">
        <section id="inicio" className="hero" aria-labelledby="hero-title">
          <div className="container hero-grid">
            <div className="hero-content">
              <p className="eyebrow"><span className="eyebrow-dot" /> LA SALUD, UN POCO MÁS CERCA</p>
              <h1 id="hero-title">Encontrá tus medicamentos <span>sin dar tantas vueltas.</span></h1>
              <p className="hero-description">Buscá medicamentos, consultá farmacias con disponibilidad y gestioná tus reservas. Todo desde una sola app para Android.</p>
              <div className="hero-actions">
                <a className="button button-primary" href="#descargar"><Smartphone size={21} aria-hidden="true" /> Conseguir la app <ArrowRight size={19} aria-hidden="true" /></a>
                <a className="button button-outline" href="#como-funciona">Cómo funciona <ArrowDown size={18} aria-hidden="true" /></a>
              </div>
              <div className="hero-benefits" aria-label="Beneficios de MediLink">
                <span><Check size={17} aria-hidden="true" /> Buscá en un lugar</span>
                <span><Check size={17} aria-hidden="true" /> Consultá disponibilidad</span>
                <span><Check size={17} aria-hidden="true" /> Reservá desde Android</span>
              </div>
            </div>
            <PhonePreview />
          </div>
        </section>

        <section id="como-funciona" className="how-section" aria-labelledby="how-title">
          <div className="container">
            <div className="section-intro">
              <p className="section-kicker">SIMPLE, COMO DEBERÍA SER</p>
              <h2 id="how-title">Tu medicamento, en <span>3 pasos.</span></h2>
              <p>Menos tiempo buscando, más claridad para saber dónde ir. MediLink te acompaña desde la búsqueda hasta el retiro.</p>
            </div>
            <ol className="steps-grid">
              {steps.map(({ number, icon: Icon, title, description }) => (
                <li className="step-card" key={number}>
                  <div className="step-top"><span className="step-icon"><Icon size={25} strokeWidth={1.9} aria-hidden="true" /></span><span className="step-number">{number}</span></div>
                  <h3>{title}</h3>
                  <p>{description}</p>
                </li>
              ))}
            </ol>
            <p className="how-note"><ShieldCheck size={19} aria-hidden="true" /> La disponibilidad está sujeta al stock informado por cada farmacia. Los medicamentos que requieren receta pueden necesitar validación.</p>
          </div>
        </section>

        <section id="descargar" className="download-section" aria-labelledby="download-title">
          <div className="container">
            <div className="download-panel">
              <div className="download-copy">
                <p className="download-kicker"><Smartphone size={17} aria-hidden="true" /> PENSADA PARA ANDROID</p>
                <h2 id="download-title">Todo MediLink.<br /><span>En tu bolsillo.</span></h2>
                <p>La experiencia completa está en la app: buscá, consultá disponibilidad y administrá tus reservas desde tu teléfono.</p>
                <DownloadButton onClick={() => setQrOpen(true)} isOpen={isQrOpen} published={download.published} />
                {download.published ? (
                  <p className="download-status"><Check size={17} aria-hidden="true" /> APK disponible para descargar e instalar. Las funciones de login, stock y reservas necesitan un servidor activo.</p>
                ) : (
                  <p className="download-status" role="status"><ShieldCheck size={17} aria-hidden="true" /> {checkingRelease
                    ? 'Comprobando si ya hay una versión Android para descargar…'
                    : 'No hay un APK público verificado por la landing. Consultá GitHub Releases para ver las versiones.'}</p>
                )}
              </div>
              <div className="download-art" aria-hidden="true">
                <div className="download-halo" />
                <div className="download-phone"><Smartphone size={100} strokeWidth={1.1} /><span className="download-phone-mark"><HeartPulse size={40} /></span></div>
                <div className="download-orbit-dot dot-one" /><div className="download-orbit-dot dot-two" />
              </div>
            </div>
          </div>
        </section>
      </main>

      <footer className="site-footer">
        <div className="container footer-inner">
          <Brand />
          <p>Encontrar lo que necesitás, más fácil.</p>
          <a href="#inicio">Volver arriba ↑</a>
        </div>
      </footer>
      {isQrOpen && <AndroidDownloadDialog download={download} onClose={closeQr} />}
    </>
  );
}
