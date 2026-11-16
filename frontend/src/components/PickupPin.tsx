import type { Reserva } from '../lib/types';

/** El PIN pertenece solo al comprobante del paciente con una reserva activa. */
export function PickupPin({ reserva }: { reserva: Pick<Reserva, 'estado' | 'pinRetiro'> }) {
  if (reserva.estado !== 'ACTIVA' || !reserva.pinRetiro) return null;
  return <div className="reservation-code" aria-label="PIN de retiro">
    <span>PIN DE RETIRO · MOSTRAR EN FARMACIA</span>
    <strong>{reserva.pinRetiro}</strong>
  </div>;
}
