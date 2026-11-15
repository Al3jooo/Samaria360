// Samaria360 - chatbot_db (ms-chatbot): aplica sus migraciones (C001, C002…) en orden, una sola vez cada una, con el
// mismo criterio que catalog_db (infra/mongo/migraciones/aplicar.js): registro en la colección «migraciones», nunca se
// edita una migración aplicada y cada una es idempotente. Sin semilla: los datos llegan con los eventos (ADR-011).
const fs = require("fs");
const path = require("path");

const conversacion = db.getSiblingDB("chatbot_db");
const migraciones = fs.readdirSync(__dirname)
  .filter((archivo) => /^C\d{3}__[a-z0-9-]+\.js$/.test(archivo))
  .map((archivo) => ({ nombre: archivo.slice(0, -3), ruta: path.join(__dirname, archivo) }))
  .sort((a, b) => a.nombre.localeCompare(b.nombre));

print("Migraciones de chatbot_db");
for (const migracion of migraciones) {
  if (conversacion.migraciones.findOne({ _id: migracion.nombre }) !== null) {
    print("  ya aplicada: " + migracion.nombre);
    continue;
  }
  print("  aplicando:   " + migracion.nombre);
  load(migracion.ruta);
  conversacion.migraciones.insertOne({ _id: migracion.nombre, aplicadaEn: new Date() });
}
print("Migraciones de chatbot_db al día.");
