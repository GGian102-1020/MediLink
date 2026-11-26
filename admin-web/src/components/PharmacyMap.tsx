import { useEffect, useMemo } from 'react';
import L from 'leaflet';
import { CircleMarker, MapContainer, Marker, Polyline, TileLayer, Tooltip, useMap } from 'react-leaflet';
import 'leaflet/dist/leaflet.css';
import { CABA_CENTRO, coords, formatoDistancia, type Encuadre, type Origen, type Ruta } from '../lib/geo';
import type { Disponibilidad } from '../lib/types';

const iconoUsuario = L.divIcon({
  className: 'map-user-icon', iconSize: [22, 22], iconAnchor: [11, 11],
  html: '<span class="map-user-pulse"></span><span class="map-user-dot"></span>',
});
const iconoFarmacia = L.divIcon({
  className: 'map-pin-icon', iconSize: [38, 48], iconAnchor: [19, 46],
  html: '<span class="map-pin"><svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path fill="currentColor" d="M10 3h4v7h7v4h-7v7h-4v-7H3v-4h7z"/></svg></span>',
});

function Encuadrar({ encuadre, conTarjeta, puntosIniciales }: { encuadre: Encuadre | null; conTarjeta: boolean; puntosIniciales: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    if (!encuadre || !encuadre.puntos.length) return;
    const reducido = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    // En escritorio la tarjeta del recorrido flota a la derecha: se deja ese lado libre.
    const tarjetaFlotante = conTarjeta && window.matchMedia('(min-width: 901px)').matches;
    map.flyToBounds(L.latLngBounds(encuadre.puntos), {
      paddingTopLeft: [56, 56], paddingBottomRight: [tarjetaFlotante ? 380 : 56, 56],
      maxZoom: encuadre.puntos.length === 1 ? Math.max(map.getZoom(), 15) : 16, animate: !reducido, duration: 0.7,
    });
    // Solo reacciona a un pedido nuevo de encuadre (cambia la clave).
  }, [encuadre?.clave, map]);
  useEffect(() => {
    if (encuadre?.puntos.length || !puntosIniciales.length) return;
    map.fitBounds(L.latLngBounds(puntosIniciales), { padding: [45, 45], maxZoom: 14, animate: false });
  }, [map, encuadre?.clave, puntosIniciales]);
  return null;
}

export default function PharmacyMap({ farmacias, seleccionada, origen, ruta, encuadre, onSeleccionar }: {
  farmacias: Disponibilidad[]; seleccionada: Disponibilidad | null; origen: Origen | null; ruta: Ruta | null;
  encuadre: Encuadre | null; onSeleccionar: (f: Disponibilidad) => void;
}) {
  const conCoords = useMemo(() => farmacias.flatMap(f => { const c = coords(f); return c ? [{ f, c }] : []; }), [farmacias]);
  const sel = seleccionada ? coords(seleccionada) : null;
  const puntosIniciales = useMemo<[number, number][]>(() => [
    ...(origen ? [[origen.lat, origen.lng] as [number, number]] : []),
    ...conCoords.map(({ c }) => [c.lat, c.lng] as [number, number]),
  ], [conCoords, origen?.lat, origen?.lng]);
  return <MapContainer className="pharmacy-map" center={[CABA_CENTRO.lat, CABA_CENTRO.lng]} zoom={12} minZoom={10} maxZoom={19}
    preferCanvas scrollWheelZoom={false} attributionControl>
    <TileLayer
      url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" maxZoom={19}
      attribution='&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a> contributors'/>
    {ruta && <>
      <Polyline positions={ruta.puntos} pathOptions={{ color: '#ffffff', weight: 9, opacity: 0.9, lineCap: 'round', lineJoin: 'round' }}/>
      <Polyline positions={ruta.puntos} pathOptions={{ color: '#216bd2', weight: 5, opacity: 0.95, lineCap: 'round', lineJoin: 'round' }}/>
    </>}
    {conCoords.map(({ f, c }) => f.farmaciaId === seleccionada?.farmaciaId ? null :
      <CircleMarker key={f.farmaciaId} center={[c.lat, c.lng]} radius={6}
        pathOptions={{ color: '#ffffff', weight: 2, fillColor: f.cantidadDisponible <= 3 ? '#f2a33a' : '#0eb8ac', fillOpacity: 0.95 }}
        eventHandlers={{ click: () => onSeleccionar(f) }}>
        <Tooltip direction="top" offset={[0, -6]} className="map-tooltip">
          <strong>{f.nombre}</strong><br/>{f.cantidadDisponible} {f.cantidadDisponible === 1 ? 'unidad' : 'unidades'}
          {f.distanciaKm != null && <> · a {formatoDistancia(f.distanciaKm * 1000)}</>}
        </Tooltip>
      </CircleMarker>)}
    {seleccionada && sel && <Marker position={[sel.lat, sel.lng]} icon={iconoFarmacia} zIndexOffset={1000} keyboard={false}>
      <Tooltip direction="top" offset={[0, -44]} className="map-tooltip" permanent><strong>{seleccionada.nombre}</strong></Tooltip>
    </Marker>}
    {origen && <Marker position={[origen.lat, origen.lng]} icon={iconoUsuario} zIndexOffset={900} keyboard={false}>
      <Tooltip direction="top" offset={[0, -12]} className="map-tooltip">{origen.fuente === 'gps' ? 'Estás acá' : origen.etiqueta}</Tooltip>
    </Marker>}
    <Encuadrar encuadre={encuadre} conTarjeta={seleccionada !== null} puntosIniciales={puntosIniciales}/>
  </MapContainer>;
}
