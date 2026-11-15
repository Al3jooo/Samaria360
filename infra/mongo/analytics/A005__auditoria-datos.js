// A005 - analytics_db, revisión previa al redespliegue (DEC-322).
// logs_auditoria suma el campo opcional «datos»: los valores del detalle por separado (identificadores, nombre del
// servicio y motivo de RF29), para que la interfaz arme la frase en el idioma activo (RF03, RNF08). El «detalle» en
// español se conserva: las entradas anteriores no tienen «datos» y se siguen mostrando con él. No toca los datos.
// INMUTABLE: el validador es su propia copia (A005__logs_auditoria.v2.schema.json). Idempotente.
(function () {
  const fs = require("fs");
  const path = require("path");
  const analitica = db.getSiblingDB("analytics_db");
  const validador = JSON.parse(fs.readFileSync(path.join(__dirname, "A005__logs_auditoria.v2.schema.json"), "utf8"));
  const resultado = analitica.runCommand({
    collMod: "logs_auditoria", validator: validador, validationLevel: "strict", validationAction: "error"
  });
  if (resultado.ok !== 1) {
    throw new Error("A005: collMod de logs_auditoria falló: " + JSON.stringify(resultado));
  }
  print("    A005: logs_auditoria con el validador v2 (datos del detalle)");
})();
