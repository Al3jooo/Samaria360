// A004 - analytics_db, iteración K del plan del 100 % (tableros; DEC-308).
// Colección técnica «consultas»: las consultas a las fichas públicas por entidad y día de Santa Marta, sumadas desde
// ConsultasAgregadas de ms-catalog (DEC-283). Base del top 3 de atractivos más consultados (RF14) y de las
// visualizaciones por servicio del Prestador (RF31).
// INMUTABLE: el validador es su propia copia (A004__consultas.v1.schema.json). Idempotente.
(function () {
  const fs = require("fs");
  const path = require("path");
  const analitica = db.getSiblingDB("analytics_db");
  const validador = JSON.parse(fs.readFileSync(path.join(__dirname, "A004__consultas.v1.schema.json"), "utf8"));
  const existe = analitica.getCollectionInfos({ name: "consultas" }).length > 0;
  const resultado = existe
    ? analitica.runCommand({ collMod: "consultas", validator: validador, validationLevel: "strict", validationAction: "error" })
    : analitica.runCommand({ create: "consultas", validator: validador, validationLevel: "strict", validationAction: "error" });
  if (resultado.ok !== 1) {
    throw new Error("A004: " + (existe ? "collMod" : "create") + " de consultas falló: " + JSON.stringify(resultado));
  }
  analitica.consultas.createIndex({ tipoEntidad: 1, dia: 1 }, { name: "consultas_tipo_dia" });
  analitica.consultas.createIndex({ idPrestador: 1, dia: 1 }, { name: "consultas_prestador_dia" });
  analitica.cuentas.createIndex({ rol: 1, pais: 1 }, { name: "cuentas_rol_pais" });
  analitica.ocupaciones.createIndex({ fecha: 1, idAtractivo: 1 }, { name: "ocupaciones_fecha_atractivo" });
  print("    A004: consultas con su validador e índices de los tableros");
})();
