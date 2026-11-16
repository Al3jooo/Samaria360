#!/usr/bin/env bash
# RNF02, «carga de página»: primera carga (sin caché) de las 9 pantallas públicas (desde DEC-325, también
# /recuperar, WF-27) con Lighthouse, en un móvil simulado
# (CPU ×4) con red 4G (9 Mbps de bajada, 3 Mbps de subida, 70 ms de latencia; «condiciones normales» definidas por el
# equipo, DEC-276). Criterio: mediana de 3 corridas de LCP (contenido principal visible) e interactivo (TTI) por debajo de 2 s.
# Las teselas de OpenStreetMap se bloquean (sin descargas automatizadas, política de uso de OSM).
#
# Requisitos: el sistema arriba (docker compose up -d) y Chrome o Edge. Lighthouse se ejecuta con npx (no se agrega
# al proyecto). Uso: bash infra/carga/rnf02-carga-pagina.sh [BASE]   (por defecto http://localhost:8000)
#   CHROME_PATH=".../msedge.exe" bash infra/carga/rnf02-carga-pagina.sh   # si no hay Chrome
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash: no convertir las rutas («/agenda») en rutas de Windows

BASE="${1:-http://localhost:${FRONTEND_PORT:-8000}}"
SALIDA="${SALIDA:-$(mktemp -d)}"
mkdir -p "$SALIDA"
LIGHTHOUSE="lighthouse@13.5.0"
RONDAS="${RONDAS:-3}"   # se informa la mediana de cada métrica (la carga varía entre corridas)

# Ids reales de la semilla: un tour y un atractivo.
oferta=$(curl -s "$BASE/api/v1/catalogo/oferta?categoria=Tour&tamano=1")
tour=$(echo "$oferta" | grep -o '"id":"[0-9a-f]*"' | head -1 | cut -d'"' -f4)
atractivo=$(curl -s "$BASE/api/v1/catalogo/oferta?tamano=10&pagina=1" "$BASE/api/v1/catalogo/oferta?tamano=10" \
  | grep -o '"tipo":"Atractivo","id":"[0-9a-f]*"' | head -1 | cut -d'"' -f8)
[ -n "$tour" ] && [ -n "$atractivo" ] || { echo "Sin datos de la semilla en $BASE" >&2; exit 1; }

RUTAS=("/" "/explorar?texto=playa" "/servicios/$tour" "/servicios/$tour/reservar" "/atractivos/$atractivo"
       "/agenda" "/preguntas-frecuentes" "/login" "/recuperar")
fallos=0
i=0
for ruta in "${RUTAS[@]}"; do
  i=$((i + 1))
  for ronda in $(seq 1 "$RONDAS"); do
    # Puerto y perfil fijos: con Edge en Windows, el puerto dinámico de chrome-launcher no siempre se detecta.
    npx -y "$LIGHTHOUSE" "$BASE$ruta" --quiet --port=9333 --only-categories=performance --form-factor=mobile       --throttling-method=devtools --throttling.downloadThroughputKbps=9000 --throttling.uploadThroughputKbps=3000       --throttling.requestLatencyMs=70 --throttling.cpuSlowdownMultiplier=4       --blocked-url-patterns='*tile.openstreetmap.org*'       --chrome-flags="--headless=new --no-first-run --user-data-dir=$SALIDA/perfil"       --output=json --output-path="$SALIDA/pagina-$i-$ronda.json" >/dev/null 2>&1 || true
  done
  node -e "
    const [ruta, ...archivos] = process.argv.slice(1);
    const corridas = archivos.map((f) => require(f).audits);
    const mediana = (k) => { const v = corridas.map((a) => a[k].numericValue).sort((x, y) => x - y);
      return Math.round(v[Math.floor(v.length / 2)]); };
    const lcp = mediana('largest-contentful-paint'), tti = mediana('interactive');
    const maxLcp = Math.round(Math.max(...corridas.map((a) => a['largest-contentful-paint'].numericValue)));
    const ok = lcp < 2000 && tti < 2000;
    console.log((ok ? 'ok     ' : 'FALLA  ') + ruta.replace(/[0-9a-f]{24}/, ':id').padEnd(26) + ' FCP '
      + mediana('first-contentful-paint') + ' ms, LCP ' + lcp + ' ms (máx ' + maxLcp + '), TTI ' + tti + ' ms, '
      + Math.round(corridas[0]['total-byte-weight'].numericValue / 1024) + ' KB');
    process.exit(ok ? 0 : 1);" "$ruta" "$SALIDA"/pagina-$i-*.json || fallos=$((fallos + 1))
done

echo "Reportes JSON en $SALIDA"
if [ "$fallos" -gt 0 ]; then echo "$fallos páginas superan 2 s." >&2; exit 1; fi
echo "Todas las páginas cargan en menos de 2 s (LCP y TTI)."
