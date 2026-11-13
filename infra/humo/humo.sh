#!/usr/bin/env bash
# Prueba de humo del sistema completo levantado con Docker Compose (H17 de la revisión del 2026-11-12).
# La usan el workflow .github/workflows/compose-humo.yml y cualquier integrante en local, con el sistema arriba:
#   bash infra/humo/humo.sh            # espera a que todo esté sano y prueba http://127.0.0.1:${FRONTEND_PORT:-8000}
# Solo usa endpoints públicos o respuestas sin token: no inicia sesión con los usuarios de la semilla.
set -euo pipefail

BASE="http://127.0.0.1:${FRONTEND_PORT:-8000}"
ESPERA_MAXIMA="${ESPERA_MAXIMA:-420}"   # segundos
fallos=0

esperar_sanos() {
  local inicio=$SECONDS
  local servicios="ms-auth ms-catalog ms-booking api-gateway frontend"
  while true; do
    local pendientes=""
    for s in $servicios; do
      estado=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "samaria360-$s" 2>/dev/null || echo ausente)
      [ "$estado" = "healthy" ] || pendientes="$pendientes $s($estado)"
    done
    [ -z "$pendientes" ] && { echo "Todos los servicios están sanos ($((SECONDS - inicio)) s)."; return 0; }
    if (( SECONDS - inicio > ESPERA_MAXIMA )); then
      echo "Tiempo agotado; siguen sin estar sanos:$pendientes" >&2
      return 1
    fi
    sleep 5
  done
}

# comprobar <descripción> <código esperado> <ruta> [texto que debe contener la respuesta]
comprobar() {
  local descripcion=$1 esperado=$2 ruta=$3 contiene=${4:-}
  local cuerpo codigo
  cuerpo=$(mktemp)
  # --path-as-is: sin él, curl normaliza «%2e%2e» antes de enviar y no se prueba RutaSeguraWebFilter.
  codigo=$(curl -s --path-as-is -o "$cuerpo" -w '%{http_code}' "$BASE$ruta" || echo 000)
  if [ "$codigo" != "$esperado" ]; then
    echo "FALLA  $descripcion: $ruta respondió $codigo (se esperaba $esperado)"; fallos=$((fallos + 1))
  elif [ -n "$contiene" ] && ! grep -q -- "$contiene" "$cuerpo"; then
    echo "FALLA  $descripcion: $ruta no contiene «$contiene»"; fallos=$((fallos + 1))
  else
    echo "ok     $descripcion ($codigo)"
  fi
  rm -f "$cuerpo"
}

encabezado() {
  local descripcion=$1 nombre=$2
  if curl -s -D - -o /dev/null "$BASE/" | grep -qi "^$nombre:"; then
    echo "ok     $descripcion"
  else
    echo "FALLA  $descripcion: falta el encabezado $nombre"; fallos=$((fallos + 1))
  fi
}

esperar_sanos

comprobar "SPA servida por nginx"                    200 /                                   '<div id="root">'
comprobar "ruta de la SPA (recarga)"                 200 /explorar                           '<div id="root">'
comprobar "países (ms-auth, público)"                200 /api/v1/auth/paises                 '"CO"'
comprobar "búsqueda pública (ms-catalog)"            200 '/api/v1/catalogo/oferta?tamano=3'  '"elementos"'
comprobar "preguntas frecuentes (ms-catalog)"        200 /api/v1/catalogo/faq                '"elementos"'
comprobar "agenda pública (ms-catalog)"              200 /api/v1/catalogo/eventos            '"elementos"'
comprobar "detalle inexistente → 404"                404 /api/v1/catalogo/servicios/000000000000000000000000 'OFERTA_NO_DISPONIBLE'
comprobar "endpoint del Administrador sin token"     401 /api/v1/catalogo/admin/avisos       'NO_AUTENTICADO'
comprobar "mis reservas sin token (ms-booking)"      401 /api/v1/reservas/mias               'NO_AUTENTICADO'
comprobar "ruta no normalizada → 400 RUTA_INVALIDA"  400 '/api/v1/catalogo/%2e%2e/oferta'    'RUTA_INVALIDA'
encabezado "Content-Security-Policy en nginx"        Content-Security-Policy
encabezado "Permissions-Policy en nginx (DEC-266)"   Permissions-Policy

if [ "$fallos" -gt 0 ]; then
  echo "$fallos comprobaciones fallaron." >&2
  exit 1
fi
echo "Prueba de humo superada."
