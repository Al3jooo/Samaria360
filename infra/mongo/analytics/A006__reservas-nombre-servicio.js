// A006 - analytics_db, revisión previa al redespliegue (DEC-323).
// reservas: «nombreServicio» admite hasta 150 caracteres, el largo del nombre comercial en ms-catalog. Con 120, la
// reserva de un servicio con un nombre más largo no se guardaba: el evento iba a la .dlq y se perdían la notificación
// (RF63), el correo al Prestador (RF30) y la calificación (RF17). No toca los datos.
// INMUTABLE: el validador es su propia copia (A006__reservas.v2.schema.json). Idempotente.
(function () {
  const fs = require("fs");
  const path = require("path");
  const analitica = db.getSiblingDB("analytics_db");
  const validador = JSON.parse(fs.readFileSync(path.join(__dirname, "A006__reservas.v2.schema.json"), "utf8"));
  const resultado = analitica.runCommand({
    collMod: "reservas", validator: validador, validationLevel: "strict", validationAction: "error"
  });
  if (resultado.ok !== 1) {
    throw new Error("A006: collMod de reservas falló: " + JSON.stringify(resultado));
  }
  print("    A006: reservas con el validador v2 (nombreServicio hasta 150)");
})();
