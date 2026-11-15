#!/bin/sh
# Samaria360 - servicio de un solo uso mongo-migraciones (docker-compose.yml).
# Espera a que MongoDB acepte conexiones por la red (durante la inicialización, mongod solo escucha en localhost y
# el healthcheck puede pasar antes), inicia la réplica de un nodo si hace falta (DEC-281) y aplica las migraciones con
# aplicar.js.
set -eu

# directConnection: antes de rs.initiate no hay primario que descubrir.
conexion="mongodb://mongo:27017/catalog_db?authSource=admin&directConnection=true"

intento=1
until mongosh "$conexion" -u "$MONGO_USER" -p "$MONGO_PASSWORD" --quiet --eval 'db.adminCommand({ ping: 1 }).ok' >/dev/null 2>&1; do
  if [ "$intento" -ge 30 ]; then
    echo "MongoDB no respondió después de 30 intentos" >&2
    exit 1
  fi
  intento=$((intento + 1))
  sleep 2
done

# Réplica rs0 de un solo nodo (DEC-281), para las transacciones del outbox (ADR-011). Idempotente: solo inicia si la
# réplica no existe; un volumen con datos anteriores los conserva (mongod crea el oplog y no borra nada).
mongosh "$conexion" -u "$MONGO_USER" -p "$MONGO_PASSWORD" --quiet --eval '
  try {
    rs.status();
  } catch (e) {
    if (e.codeName !== "NotYetInitialized") { throw e; }
    rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "mongo:27017" }] });
    print("Réplica rs0 iniciada");
  }
  let intentos = 0;
  while (!db.hello().isWritablePrimary) {
    if (++intentos > 60) { throw new Error("La réplica rs0 no eligió primario"); }
    sleep(1000);
  }'

mongosh "$conexion" -u "$MONGO_USER" -p "$MONGO_PASSWORD" --quiet /migraciones/aplicar.js
# Usuario propio de ms-catalog (DEC-275), después de las migraciones.
mongosh "$conexion" -u "$MONGO_USER" -p "$MONGO_PASSWORD" --quiet /migraciones/usuario-servicio.js

# analytics_db de ms-analytics (ADR-011, DEC-292): sus migraciones y su usuario propio.
mongosh "$conexion" -u "$MONGO_USER" -p "$MONGO_PASSWORD" --quiet /analytics/aplicar.js
mongosh "$conexion" -u "$MONGO_USER" -p "$MONGO_PASSWORD" --quiet /analytics/usuario-servicio.js

# chatbot_db de ms-chatbot (DEC-297): sus migraciones y su usuario propio.
mongosh "$conexion" -u "$MONGO_USER" -p "$MONGO_PASSWORD" --quiet /chatbot/aplicar.js
exec mongosh "$conexion" -u "$MONGO_USER" -p "$MONGO_PASSWORD" --quiet /chatbot/usuario-servicio.js
