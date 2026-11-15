// M014 - Colección «outbox_eventos» (plan del 100 %, iteración G; ADR-011, DEC-293), en catalog_db.
// Outbox transaccional de ms-catalog: el evento (p. ej. ServicioDecidido, RF21) se inserta en la misma transacción de
// MongoDB que el cambio (réplica rs0, DEC-281) y el relevo lo publica en RabbitMQ con publisher confirms. Colección
// técnica, fuera de la tabla 1 del Entregable 2 (DEC-284).
// Índice outbox_pendientes: el relevo busca los no publicados y vencidos, en orden de _id.
// INMUTABLE: usa su propia copia del validador (M014__outbox_eventos.v1.schema.json). Idempotente.
(function () {
  const fs = require("fs");
  const path = require("path");
  const catalogo = db.getSiblingDB("catalog_db");
  const validador = JSON.parse(fs.readFileSync(path.join(__dirname, "M014__outbox_eventos.v1.schema.json"), "utf8"));
  const existe = catalogo.getCollectionInfos({ name: "outbox_eventos" }).length > 0;
  const resultado = existe
    ? catalogo.runCommand({ collMod: "outbox_eventos", validator: validador, validationLevel: "strict", validationAction: "error" })
    : catalogo.runCommand({ create: "outbox_eventos", validator: validador, validationLevel: "strict", validationAction: "error" });
  if (resultado.ok !== 1) {
    throw new Error("M014: " + (existe ? "collMod" : "create") + " de outbox_eventos falló: " + JSON.stringify(resultado));
  }
  print("    M014: colección outbox_eventos " + (existe ? "ya existía; validador fijado" : "creada con su validador"));
  catalogo.outbox_eventos.createIndex({ publicadoEn: 1, proximoIntento: 1, _id: 1 }, { name: "outbox_pendientes" });
  catalogo.outbox_eventos.createIndex({ idPublico: 1 }, { name: "outbox_id_publico", unique: true });
  print("    M014: índices outbox_pendientes y outbox_id_publico");
})();
