import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { PickupPin } from '../src/components/PickupPin';

describe('PIN de retiro en el comprobante del paciente', () => {
  it('muestra el PIN solo mientras la reserva está activa', () => {
    const html = renderToStaticMarkup(<PickupPin reserva={{ estado: 'ACTIVA', pinRetiro: '023456' }}/>);
    expect(html).toContain('023456');
    expect(html).toContain('PIN DE RETIRO');
  });
  it('no muestra el PIN en reservas terminales o sin PIN', () => {
    expect(renderToStaticMarkup(<PickupPin reserva={{ estado: 'COMPLETADA', pinRetiro: '023456' }}/>)).toBe('');
    expect(renderToStaticMarkup(<PickupPin reserva={{ estado: 'ACTIVA', pinRetiro: null }}/>)).toBe('');
  });
});
