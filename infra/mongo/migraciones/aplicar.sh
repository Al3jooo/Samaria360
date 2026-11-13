#!/bin/sh
# Samaria360 - servicio de un solo uso mongo-migraciones (docker-compose.yml).
# Espera a que MongoDB acepte conexiones por la red (durante la inicialización, mongod solo escucha en localhost y
# el healthcheck puede pasar antes) y aplica las migraciones con aplicar.js.
set -eu

conexion="mongodb://mongo:27017/catalog_db?authSource=admin"

intento=1
until mongosh "$conexion" -u "$MONGO_USER" -p "$MONGO_PASSWORD" --quiet --eval 'db.adminCommand({ ping: 1 }).ok' >/dev/null 2>&1; do
  if [ "$intento" -ge 30 ]; then
    echo "MongoDB no respondió después de 30 intentos" >&2
    exit 1
  fi
  intento=$((intento + 1))
  sleep 2
done

mongosh "$conexion" -u "$MONGO_USER" -p "$MONGO_PASSWORD" --quiet /migraciones/aplicar.js
# Usuario propio de ms-catalog (DEC-275), después de las migraciones.
exec mongosh "$conexion" -u "$MONGO_USER" -p "$MONGO_PASSWORD" --quiet /migraciones/usuario-servicio.js
