// A003 - analytics_db, iteración J del plan del 100 % (afluencia; DEC-304).
// Colección técnica «ocupaciones»: el historial de OcupacionRegistrada de ms-catalog, con el que la iteración K arma los
// reportes de afluencia del Gestor (RF64). ms-analytics no lee catalog_db (decisión 6 del equipo).
// INMUTABLE: el validador es su propia copia (A003__ocupaciones.v1.schema.json). Idempotente.
(function () {
  const fs = require("fs");
  const path = require("path");
  const analitica = db.getSiblingDB("analytics_db");
  const validador = JSON.parse(fs.readFileSync(path.join(__dirname, "A003__ocupaciones.v1.schema.json"), "utf8"));
  const existe = analitica.getCollectionInfos({ name: "ocupaciones" }).length > 0;
  const resultado = existe
    ? analitica.runCommand({ collMod: "ocupaciones", validator: validador, validationLevel: "strict", validationAction: "error" })
    : analitica.runCommand({ create: "ocupaciones", validator: validador, validationLevel: "strict", validationAction: "error" });
  if (resultado.ok !== 1) {
    throw new Error("A003: " + (existe ? "collMod" : "create") + " de ocupaciones falló: " + JSON.stringify(resultado));
  }
  analitica.ocupaciones.createIndex({ idEvento: 1 }, { name: "ocupaciones_id_evento", unique: true });
  analitica.ocupaciones.createIndex({ idAtractivo: 1, fecha: -1 }, { name: "ocupaciones_atractivo" });
  analitica.ocupaciones.createIndex({ fecha: -1 }, { name: "ocupaciones_fecha" });
  print("    A003: ocupaciones con su validador e índices");
})();
