// M017 - Oferta del prestador, iteración I del plan del 100 % (RF41, RF19, RF48; DEC-311, DEC-313).
//
// Colección nueva «historial_modificaciones» (validador v1): las modificaciones de precio (RF32), cupos, fechas,
// horarios y plantilla (RF19, desde OfertaModificada de ms-booking) de cada servicio, con el valor anterior y el nuevo;
// RF41 muestra los últimos 30 días. idEvento deduplica lo que llega por RabbitMQ.
//
// INMUTABLE: usa su propia copia del validador. Idempotente.

(function () {
  const fs = require("fs");
  const path = require("path");
  const catalogo = db.getSiblingDB("catalog_db");
  const validador = JSON.parse(fs.readFileSync(path.join(__dirname, "M017__historial_modificaciones.v1.schema.json"), "utf8"));
  const existe = catalogo.getCollectionInfos({ name: "historial_modificaciones" }).length > 0;
  const resultado = existe
    ? catalogo.runCommand({ collMod: "historial_modificaciones", validator: validador, validationLevel: "strict", validationAction: "error" })
    : catalogo.runCommand({ create: "historial_modificaciones", validator: validador, validationLevel: "strict", validationAction: "error" });
  if (resultado.ok !== 1) {
    throw new Error("M017: " + (existe ? "collMod" : "create") + " de historial_modificaciones falló: " + JSON.stringify(resultado));
  }
  catalogo.historial_modificaciones.createIndex({ prestadorId: 1, fecha: -1, _id: -1 }, { name: "historial_prestador" });
  catalogo.historial_modificaciones.createIndex({ idEvento: 1 },
    { name: "historial_id_evento", unique: true, partialFilterExpression: { idEvento: { $type: "string" } } });
  print("    M017: historial_modificaciones con su validador e índices");
})();
