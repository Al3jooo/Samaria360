#!/usr/bin/env bash
# Prueba de tolerancia a fallos (evidencia de diseño para RNF01; no mide la disponibilidad mensual).
# Con el sistema arriba (docker compose up -d), detiene ms-catalog y comprueba que:
#   1. el gateway responde 503 SERVICIO_NO_DISPONIBLE (fallback ProblemDetail, ADR-005) en vez de colgarse;
#   2. ms-booking sigue sirviendo la disponibilidad ya materializada sin consultar ms-catalog (ADR-007);
#   3. ms-auth y el frontend no se afectan;
#   4. al volver ms-catalog, la búsqueda se recupera sola (sin reiniciar el gateway).
# Solo endpoints públicos, sin token. Uso: bash infra/carga/resiliencia.sh
set -euo pipefail

BASE="http://127.0.0.1:${FRONTEND_PORT:-8000}"
fallos=0
hoy=$(TZ=America/Bogota date +%F)
manana=$(TZ=America/Bogota date -d '+1 day' +%F 2>/dev/null || date -v+1d +%F)

comprobar() {
  local descripcion=$1 esperado=$2 ruta=$3 contiene=${4:-}
  local cuerpo codigo inicio fin
  cuerpo=$(mktemp)
  inicio=$(date +%s%N)
  codigo=$(curl -s -m 10 -o "$cuerpo" -w '%{http_code}' "$BASE$ruta" || echo 000)
  fin=$(( ($(date +%s%N) - inicio) / 1000000 ))
  if [ "$codigo" != "$esperado" ] || { [ -n "$contiene" ] && ! grep -q -- "$contiene" "$cuerpo"; }; then
    echo "FALLA  $descripcion: $codigo en ${fin} ms (se esperaba $esperado ${contiene:+con «$contiene»})"
    fallos=$((fallos + 1))
  else
    echo "ok     $descripcion ($codigo, ${fin} ms)"
  fi
  rm -f "$cuerpo"
}

restaurar() { docker start samaria360-ms-catalog >/dev/null 2>&1 || true; }
trap restaurar EXIT

# Un tour de la semilla con su inventario ya materializado (la consulta previa lo materializa).
id=$(curl -s "$BASE/api/v1/catalogo/oferta?categoria=Tour&tamano=1" | grep -o '"id":"[0-9a-f]*"' | head -1 | cut -d'"' -f4)
[ -n "$id" ] || { echo "Sin datos de la semilla en $BASE" >&2; exit 1; }
disp="/api/v1/reservas/disponibilidad?servicio=$id&desde=$hoy&hasta=$manana"

echo "== Antes de la falla"
comprobar "búsqueda"                    200 '/api/v1/catalogo/oferta?tamano=3' '"elementos"'
comprobar "disponibilidad (materializa)" 200 "$disp" '"horarios"'

echo "== ms-catalog detenido"
docker stop samaria360-ms-catalog >/dev/null
comprobar "búsqueda → fallback del gateway"         503 '/api/v1/catalogo/oferta?tamano=3' 'SERVICIO_NO_DISPONIBLE'
comprobar "disponibilidad ya materializada"         200 "$disp" '"horarios"'
comprobar "ms-auth no se afecta (países)"           200 /api/v1/auth/paises '"CO"'
comprobar "SPA servida por nginx"                   200 /explorar '<div id="root">'

echo "== ms-catalog de vuelta"
docker start samaria360-ms-catalog >/dev/null
inicio=$SECONDS
until [ "$(docker inspect -f '{{.State.Health.Status}}' samaria360-ms-catalog)" = healthy ]; do
  (( SECONDS - inicio > 180 )) && { echo "FALLA  ms-catalog no volvió a estar sano en 180 s"; exit 1; }
  sleep 3
done
# El circuit breaker del gateway pasa a semiabierto tras su espera; se reintenta hasta 60 s.
inicio=$SECONDS
until curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/catalogo/oferta?tamano=3" | grep -q 200; do
  (( SECONDS - inicio > 60 )) && break
  sleep 2
done
comprobar "búsqueda recuperada sin reiniciar el gateway" 200 '/api/v1/catalogo/oferta?tamano=3' '"elementos"'

if [ "$fallos" -gt 0 ]; then echo "$fallos comprobaciones fallaron." >&2; exit 1; fi
echo "Prueba de tolerancia a fallos superada."
