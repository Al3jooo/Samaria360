#!/bin/sh
# Samaria360 - arranque del contenedor de RabbitMQ (ADR-011, DEC-284).
# Arranca el servidor con la topología de definiciones.json y crea o actualiza, en cada arranque, un usuario por
# servicio con permisos mínimos (como DEC-275): los productores solo escriben en el exchange y cada consumidor solo lee
# su cola. Las contraseñas llegan por variables de entorno (.env), nunca en el repositorio.
set -eu

# Los comandos rabbitmqctl corren como root: usan la cookie de Erlang del servidor (la del usuario rabbitmq).
export HOME=/var/lib/rabbitmq
VHOST=samaria360
EXCHANGE='^samaria360\.eventos\.v1$'

rm -f /tmp/rabbitmq-listo
docker-entrypoint.sh rabbitmq-server &
PID=$!
# docker stop envía SIGTERM al sh (PID 1): se reenvía al servidor para que cierre limpio.
trap 'kill -TERM "$PID"; wait "$PID"; exit 0' TERM INT

# El nodo tarda unos segundos en existir; recién entonces await_startup espera a que termine de arrancar.
intento=0
until rabbitmq-diagnostics -q ping >/dev/null 2>&1; do
  intento=$((intento + 1))
  if [ "$intento" -ge 60 ]; then
    echo "RabbitMQ no arrancó en 120 s" >&2
    exit 1
  fi
  sleep 2
done
rabbitmqctl --timeout 120 await_startup >/dev/null

usuario() { # nombre clave
  if rabbitmqctl -q list_users | cut -f1 | grep -qx "$1"; then
    rabbitmqctl -q change_password "$1" "$2"
  else
    rabbitmqctl -q add_user "$1" "$2"
  fi
}

# Administrador de la consola (solo 127.0.0.1, solo desarrollo).
usuario "$RABBITMQ_USER" "$RABBITMQ_PASSWORD"
rabbitmqctl -q set_user_tags "$RABBITMQ_USER" administrator
rabbitmqctl -q set_permissions -p "$VHOST" "$RABBITMQ_USER" '.*' '.*' '.*'

# Productores: configurar nada, escribir solo en el exchange, leer nada.
for p in "AUTH:ms-auth" "BOOKING:ms-booking"; do
  clave=$(printenv "RABBITMQ_${p%%:*}_PASSWORD")
  usuario "${p#*:}" "$clave"
  rabbitmqctl -q set_permissions -p "$VHOST" "${p#*:}" '^$' "$EXCHANGE" '^$'
done

# ms-catalog publica (ConsultasAgregadas) y consume su cola; ms-analytics, igual con la suya; ms-chatbot solo consume.
usuario ms-catalog "$RABBITMQ_CATALOG_PASSWORD"
rabbitmqctl -q set_permissions -p "$VHOST" ms-catalog '^$' "$EXCHANGE" '^ms-catalog\.eventos\.v1$'
usuario ms-analytics "$RABBITMQ_ANALYTICS_PASSWORD"
rabbitmqctl -q set_permissions -p "$VHOST" ms-analytics '^$' "$EXCHANGE" '^ms-analytics\.eventos\.v1$'
usuario ms-chatbot "$RABBITMQ_CHATBOT_PASSWORD"
rabbitmqctl -q set_permissions -p "$VHOST" ms-chatbot '^$' '^$' '^ms-chatbot\.eventos\.v1$'

touch /tmp/rabbitmq-listo
echo "RabbitMQ listo: vhost $VHOST y usuarios de los servicios"
wait "$PID"
