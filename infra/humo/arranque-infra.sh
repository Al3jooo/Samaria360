#!/usr/bin/env bash
# QA-01 y QA-03: primeros arranques aislados, sin puertos ni volúmenes de la aplicación.
set -euo pipefail
raiz=$(cd "$(dirname "$0")/../.." && pwd)
temporal=$(mktemp -d)
if command -v cygpath >/dev/null 2>&1; then
  raiz=$(cygpath -m "$raiz")
  temporal=$(cygpath -m "$temporal")
  # Git Bash no debe convertir las rutas internas de los contenedores a rutas de Windows.
  export MSYS_NO_PATHCONV=1
fi
postgres="samaria360-qa-postgres-$$"
rabbit="samaria360-qa-rabbit-$$"
limpiar() {
  docker rm -f "$postgres" "$rabbit" >/dev/null 2>&1 || true
  rm -f "$temporal/esperar.sh"
  rmdir "$temporal"
}
trap limpiar EXIT
# Usar el healthcheck real de Compose: la prueba también debe fallar si se vuelve al chequeo por socket.
# Solo se extrae este comando; la configuración resuelta (que incluye secretos) nunca se imprime ni se guarda.
salud_postgres=$(docker compose -f "$raiz/docker-compose.yml" config --format json | node -e '
  const fs = require("node:fs");
  const test = JSON.parse(fs.readFileSync(0, "utf8")).services.postgres.healthcheck.test;
  if (test[0] !== "CMD-SHELL") process.exit(1);
  process.stdout.write(test[1]);
')

# Mantener abierto initdb: el socket ya acepta conexiones, pero aún no existe el servidor TCP final.
printf '%s\n' 'while [ ! -f /tmp/continuar ]; do sleep 1; done' > "$temporal/esperar.sh"
docker run -d --name "$postgres" --tmpfs /var/lib/postgresql/data -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_USER=postgres \
  --mount "type=bind,source=$temporal/esperar.sh,target=/docker-entrypoint-initdb.d/99-esperar.sh,readonly" \
  --health-cmd "$salud_postgres" --health-interval 1s --health-retries 90 \
  postgres:16-alpine >/dev/null
docker exec "$postgres" sh -c 'i=0; until pg_isready -U postgres >/dev/null; do
  i=$((i+1)); [ "$i" -lt 60 ] || exit 1; sleep 1; done
  if pg_isready -h 127.0.0.1 -U postgres >/dev/null; then exit 1; fi'
test "$(docker inspect -f '{{.State.Health.Status}}' "$postgres")" != healthy
docker exec "$postgres" touch /tmp/continuar
for intento in $(seq 1 60); do
  [ "$(docker inspect -f '{{.State.Health.Status}}' "$postgres")" = healthy ] && break
  sleep 1
done
test "$(docker inspect -f '{{.State.Health.Status}}' "$postgres")" = healthy
docker exec "$postgres" psql -h 127.0.0.1 -U postgres -d postgres -c 'SELECT 1' >/dev/null
echo 'ok     PostgreSQL solo está sano cuando acepta conexiones TCP'

for caso in nuevo cookie-root; do
  preparar=''
  if [ "$caso" = cookie-root ]; then
    preparar='printf "%s" "cookie-aislada-exclusiva-de-prueba" > /var/lib/rabbitmq/.erlang.cookie; chmod 600 /var/lib/rabbitmq/.erlang.cookie;'
  fi
  docker run -d --name "$rabbit" --hostname qa-rabbit --tmpfs /var/lib/rabbitmq \
    --mount "type=bind,source=$raiz/infra/rabbitmq,target=/qa,readonly" \
    --mount "type=bind,source=$raiz/infra/rabbitmq/rabbitmq.conf,target=/etc/rabbitmq/rabbitmq.conf,readonly" \
    --mount "type=bind,source=$raiz/infra/rabbitmq/definiciones.json,target=/etc/rabbitmq/definiciones.json,readonly" \
    -e RABBITMQ_USER=qa-admin -e RABBITMQ_PASSWORD=qa-aislada \
    -e RABBITMQ_AUTH_PASSWORD=qa-aislada -e RABBITMQ_BOOKING_PASSWORD=qa-aislada \
    -e RABBITMQ_CATALOG_PASSWORD=qa-aislada -e RABBITMQ_ANALYTICS_PASSWORD=qa-aislada \
    -e RABBITMQ_CHATBOT_PASSWORD=qa-aislada --entrypoint sh rabbitmq:4.1.4-management-alpine \
    -c "$preparar exec sh /qa/iniciar.sh" >/dev/null
  for intento in $(seq 1 90); do
    docker exec "$rabbit" test -f /tmp/rabbitmq-listo && break
    sleep 2
  done
  docker exec "$rabbit" test -f /tmp/rabbitmq-listo
  test "$(docker exec "$rabbit" stat -c %U /var/lib/rabbitmq/.erlang.cookie)" = rabbitmq
  docker exec --user rabbitmq "$rabbit" rabbitmq-diagnostics -q check_port_connectivity
  echo "ok     RabbitMQ arranca y prepara sus usuarios ($caso)"
  docker rm -f "$rabbit" >/dev/null
done
