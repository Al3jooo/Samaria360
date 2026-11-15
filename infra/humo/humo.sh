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
  local servicios="rabbitmq ms-auth ms-catalog ms-booking ms-analytics ms-chatbot api-gateway frontend"
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

# comprobar_post <descripción> <código esperado> <ruta> <cuerpo JSON> [texto que debe contener la respuesta]
comprobar_post() {
  local descripcion=$1 esperado=$2 ruta=$3 json=$4 contiene=${5:-}
  local cuerpo codigo
  cuerpo=$(mktemp)
  codigo=$(curl -s -o "$cuerpo" -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d "$json" "$BASE$ruta" \
    || echo 000)
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
comprobar "notificaciones sin token (ms-analytics)"  401 /api/v1/analytics/notificaciones   'NO_AUTENTICADO'
comprobar "asistente sin token (ms-chatbot)"        401 /api/v1/chatbot/itinerarios     'NO_AUTENTICADO'
# RF17 (DEC-300): las calificaciones visibles de una entidad son públicas; calificar exige un token.
comprobar "calificaciones públicas (ms-analytics)"   200 '/api/v1/analytics/calificaciones?tipo=servicio&id=000000000000000000000000' '"cantidad":0'
# RF39 (DEC-304): la afluencia de un atractivo es pública; uno inexistente es 404.
comprobar "afluencia de un atractivo inexistente"     404 /api/v1/catalogo/atractivos/000000000000000000000000/afluencia 'ATRACTIVO_NO_ENCONTRADO'
# RF14 (DEC-308): los tableros exigen un token.
comprobar "tableros sin token (ms-analytics)"           401 /api/v1/analytics/tableros/general 'NO_AUTENTICADO'
# RF41 y RF42 (DEC-311, DEC-312): el historial de modificaciones y los asistentes exigen un token.
comprobar "historial de modificaciones sin token"     401 /api/v1/catalogo/prestador/historial-modificaciones 'NO_AUTENTICADO'
comprobar "asistentes sin token (ms-booking)"         401 '/api/v1/reservas/prestador/asistentes?servicio=000000000000000000000000&fecha=2030-01-01' 'NO_AUTENTICADO'
comprobar "ruta no normalizada → 400 RUTA_INVALIDA"  400 '/api/v1/catalogo/%2e%2e/oferta'    'RUTA_INVALIDA'
# RF34 (DEC-285): la respuesta uniforme con un correo sin cuenta (no envía nada) y un token inválido.
comprobar_post "recuperación, correo sin cuenta → 202"  202 /api/v1/auth/recuperacion \
  '{"correo":"humo-sin-cuenta@samaria360.test"}' 'RECIBIDO'
comprobar_post "restablecer con token inválido → 400"  400 /api/v1/auth/recuperacion/restablecimiento \
  '{"token":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","contrasena":"ClaveDeHumo1"}' 'TOKEN_INVALIDO'
encabezado "Content-Security-Policy en nginx"        Content-Security-Policy
encabezado "Permissions-Policy en nginx (DEC-266)"   Permissions-Policy

if [ "$fallos" -gt 0 ]; then
  echo "$fallos comprobaciones fallaron." >&2
  exit 1
fi
echo "Prueba de humo superada."
