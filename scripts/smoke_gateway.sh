#!/usr/bin/env bash
# Smoke local del despliegue HTTPS. Requiere curl y Python 3; no modifica datos.
set -euo pipefail
BASE_URL="${MEDLINK_BASE_URL:-https://localhost:8443}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# -k: exclusivamente para la CA interna de Caddy en el entorno local de demo.
for attempt in $(seq 1 90); do
  if curl -ksS --fail "$BASE_URL/api/medicamentos?page=0&size=1" -o "$TMP_DIR/catalogo.json" 2>/dev/null; then
    break
  fi
  if [ "$attempt" -eq 90 ]; then
    echo "ERROR: API no disponible despues de 180 segundos" >&2
    exit 1
  fi
  sleep 2
done
python3 - "$TMP_DIR/catalogo.json" <<'PYJSON'
import json, sys
with open(sys.argv[1], encoding='utf-8') as stream:
    result = json.load(stream)
assert isinstance(result.get('content'), list), 'API no respondio con PageDTO'
PYJSON
for route in / /ingresar /mis-reservas /admin/usuarios; do
  curl -ksS --fail "$BASE_URL$route" -o "$TMP_DIR/pagina.html"
  if ! grep -q 'id="root"' "$TMP_DIR/pagina.html"; then
    echo "ERROR: React SPA no disponible en $route" >&2
    exit 1
  fi
  echo "PASS: $route"
done
echo 'PASS: API JSON /api/medicamentos y rutas directas SPA por HTTPS'
