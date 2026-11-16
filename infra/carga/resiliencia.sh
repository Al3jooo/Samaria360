#!/usr/bin/env bash
# Prueba de tolerancia a fallos (evidencia de diseño para RNF01; no mide la disponibilidad mensual).
# Con el sistema completo arriba (docker compose up -d), detiene un componente a la vez y comprueba que:
#   1. ms-catalog: el gateway responde 503 SERVICIO_NO_DISPONIBLE (fallback ProblemDetail, ADR-005) en vez de colgarse,
#      ms-booking sigue sirviendo la disponibilidad ya materializada (ADR-007) y ms-auth y el frontend no se afectan;
#   2. ms-analytics: las calificaciones dan 503 con el servicio, pero la ficha y la búsqueda del catálogo siguen;
#   3. ms-chatbot: el asistente da 503 y el resto de la plataforma sigue;
#   4. RabbitMQ: la lectura sigue (los eventos esperan en los outbox, ADR-011) y, al volver, los consumidores se
#      reconectan solos;
#   5. cada componente, al volver, se recupera sin reiniciar el gateway ni los demás servicios.
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

esperar_sano() {
  local contenedor=$1 inicio=$SECONDS
  until [ "$(docker inspect -f '{{.State.Health.Status}}' "$contenedor")" = healthy ]; do
    (( SECONDS - inicio > 180 )) && { echo "FALLA  $contenedor no volvió a estar sano en 180 s"; fallos=$((fallos + 1)); return; }
    sleep 3
  done
  echo "ok     $contenedor sano de nuevo ($(( SECONDS - inicio )) s)"
}

# El circuit breaker del gateway pasa a semiabierto tras su espera; se reintenta hasta 60 s.
esperar_200() {
  local ruta=$1 inicio=$SECONDS
  until curl -s -o /dev/null -w '%{http_code}' "$BASE$ruta" | grep -q 200; do
    (( SECONDS - inicio > 60 )) && break
    sleep 2
  done
}

COMPONENTES=(samaria360-ms-catalog samaria360-ms-analytics samaria360-ms-chatbot samaria360-rabbitmq)
restaurar() { for c in "${COMPONENTES[@]}"; do docker start "$c" >/dev/null 2>&1 || true; done; }
trap restaurar EXIT

# Un tour de la semilla con su inventario ya materializado (la consulta previa lo materializa) y un atractivo.
id=$(curl -s "$BASE/api/v1/catalogo/oferta?categoria=Tour&tamano=1" | grep -o '"id":"[0-9a-f]*"' | head -1 | cut -d'"' -f4)
atractivo=$(curl -s "$BASE/api/v1/catalogo/oferta?tamano=10" | grep -o '"tipo":"Atractivo","id":"[0-9a-f]*"' | head -1 | cut -d'"' -f8)
[ -n "$id" ] && [ -n "$atractivo" ] || { echo "Sin datos de la semilla en $BASE" >&2; exit 1; }
disp="/api/v1/reservas/disponibilidad?servicio=$id&desde=$hoy&hasta=$manana"
calif="/api/v1/analytics/calificaciones?tipo=servicio&id=$id"

echo "== Antes de las fallas"
comprobar "búsqueda"                     200 '/api/v1/catalogo/oferta?tamano=3' '"elementos"'
comprobar "disponibilidad (materializa)"  200 "$disp" '"horarios"'
comprobar "calificaciones (ms-analytics)" 200 "$calif" '"cantidad"'
comprobar "asistente sin token (ms-chatbot)" 401 /api/v1/chatbot/itinerarios 'NO_AUTENTICADO'

echo "== ms-catalog detenido"
docker stop samaria360-ms-catalog >/dev/null
comprobar "búsqueda → fallback del gateway"         503 '/api/v1/catalogo/oferta?tamano=3' 'SERVICIO_NO_DISPONIBLE'
comprobar "disponibilidad ya materializada"         200 "$disp" '"horarios"'
comprobar "ms-auth no se afecta (países)"           200 /api/v1/auth/paises '"CO"'
comprobar "ms-analytics no se afecta"               200 "$calif" '"cantidad"'
comprobar "SPA servida por nginx"                   200 /explorar '<div id="root">'
docker start samaria360-ms-catalog >/dev/null
esperar_sano samaria360-ms-catalog
esperar_200 '/api/v1/catalogo/oferta?tamano=3'
comprobar "búsqueda recuperada sin reiniciar el gateway" 200 '/api/v1/catalogo/oferta?tamano=3' '"elementos"'

echo "== ms-analytics detenido"
docker stop samaria360-ms-analytics >/dev/null
comprobar "calificaciones → fallback del gateway"   503 "$calif" 'SERVICIO_NO_DISPONIBLE'
comprobar "ficha del servicio sigue"                200 "/api/v1/catalogo/servicios/$id" '"nombre'
comprobar "afluencia del atractivo sigue"           200 "/api/v1/catalogo/atractivos/$atractivo/afluencia"
comprobar "búsqueda sigue"                          200 '/api/v1/catalogo/oferta?tamano=3' '"elementos"'
docker start samaria360-ms-analytics >/dev/null
esperar_sano samaria360-ms-analytics
esperar_200 "$calif"
comprobar "calificaciones recuperadas"              200 "$calif" '"cantidad"'

echo "== ms-chatbot detenido"
docker stop samaria360-ms-chatbot >/dev/null
comprobar "asistente → fallback del gateway"        503 /api/v1/chatbot/itinerarios 'SERVICIO_NO_DISPONIBLE'
comprobar "búsqueda sigue"                          200 '/api/v1/catalogo/oferta?tamano=3' '"elementos"'
comprobar "ms-auth sigue"                           200 /api/v1/auth/paises '"CO"'
docker start samaria360-ms-chatbot >/dev/null
esperar_sano samaria360-ms-chatbot
inicio=$SECONDS
until curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/chatbot/itinerarios" | grep -q 401; do
  (( SECONDS - inicio > 60 )) && break
  sleep 2
done
comprobar "asistente recuperado (401 sin token)"    401 /api/v1/chatbot/itinerarios 'NO_AUTENTICADO'

echo "== RabbitMQ detenido"
docker stop samaria360-rabbitmq >/dev/null
comprobar "búsqueda sigue"                          200 '/api/v1/catalogo/oferta?tamano=3' '"elementos"'
comprobar "ficha y calificaciones siguen"           200 "$calif" '"cantidad"'
comprobar "disponibilidad sigue"                    200 "$disp" '"horarios"'
sleep 15
for s in ms-auth ms-catalog ms-booking ms-analytics ms-chatbot; do
  estado=$(docker inspect -f '{{.State.Health.Status}}' "samaria360-$s")
  if [ "$estado" = healthy ]; then echo "ok     $s sigue sano sin el bróker"; else echo "FALLA  $s: $estado sin el bróker"; fallos=$((fallos + 1)); fi
done
docker start samaria360-rabbitmq >/dev/null
esperar_sano samaria360-rabbitmq
# Los listeners de Spring AMQP reintentan la conexión: cada cola de consumidor debe volver a tener su consumidor.
inicio=$SECONDS
while :; do
  colas=$(docker exec samaria360-rabbitmq rabbitmqctl -q -p samaria360 list_queues name consumers 2>/dev/null \
    | awk '$1 !~ /\.dlq$/ && $1 ~ /\.eventos\.v1$/ {print $1"="$2}')
  sin=$(echo "$colas" | grep -c '=0$' || true)
  [ -n "$colas" ] && [ "$sin" -eq 0 ] && break
  (( SECONDS - inicio > 90 )) && break
  sleep 3
done
if [ -n "$colas" ] && [ "$sin" -eq 0 ]; then
  echo "ok     consumidores reconectados ($(echo $colas | tr '\n' ' '))"
else
  echo "FALLA  colas sin consumidor tras 90 s: $(echo $colas | tr '\n' ' ')"
  fallos=$((fallos + 1))
fi

if [ "$fallos" -gt 0 ]; then echo "$fallos comprobaciones fallaron." >&2; exit 1; fi
echo "Prueba de tolerancia a fallos superada."
