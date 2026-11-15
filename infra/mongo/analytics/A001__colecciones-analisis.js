// A001 - analytics_db, iteración G del plan del 100 % (ms-analytics; ADR-011, DEC-292).
// Colecciones del Entregable 2 (§2.4, «Análisis y notificaciones») con las ampliaciones de DEC-292:
//   logs_auditoria (RF13) y notificaciones (RF63);
// y colecciones técnicas (DEC-284, DEC-292): cuentas (proyección, DEC-110 y RF14), reservas (hechos para RF14, RF31 y
// RF17), correos_pendientes (RF30) y eventos_procesados (deduplicación, TTL de 30 días).
// calificaciones y reportes llegan con la iteración H, en su propia migración.
// INMUTABLE: cada validador es su propia copia (A001__*.v1.schema.json). Idempotente.
(function () {
  const fs = require("fs");
  const path = require("path");
  const analitica = db.getSiblingDB("analytics_db");

  function coleccion(nombre) {
    const validador = JSON.parse(fs.readFileSync(path.join(__dirname, "A001__" + nombre + ".v1.schema.json"), "utf8"));
    const existe = analitica.getCollectionInfos({ name: nombre }).length > 0;
    const resultado = existe
      ? analitica.runCommand({ collMod: nombre, validator: validador, validationLevel: "strict", validationAction: "error" })
      : analitica.runCommand({ create: nombre, validator: validador, validationLevel: "strict", validationAction: "error" });
    if (resultado.ok !== 1) {
      throw new Error("A001: " + (existe ? "collMod" : "create") + " de " + nombre + " falló: " + JSON.stringify(resultado));
    }
    print("    A001: " + nombre + (existe ? " ya existía; validador fijado" : " creada con su validador"));
  }

  ["logs_auditoria", "notificaciones", "cuentas", "reservas", "correos_pendientes", "eventos_procesados"]
    .forEach(coleccion);

  analitica.logs_auditoria.createIndex({ idEvento: 1 }, { name: "auditoria_id_evento", unique: true });
  analitica.logs_auditoria.createIndex({ fecha: -1, _id: -1 }, { name: "auditoria_fecha" });
  analitica.logs_auditoria.createIndex({ accion: 1, fecha: -1 }, { name: "auditoria_accion" });
  analitica.notificaciones.createIndex({ usuarioId: 1, fecha: -1, _id: -1 }, { name: "notificaciones_usuario" });
  analitica.notificaciones.createIndex({ usuarioId: 1, leida: 1 }, { name: "notificaciones_no_leidas" });
  analitica.reservas.createIndex({ idPrestador: 1, confirmadaEn: -1 }, { name: "reservas_prestador" });
  analitica.reservas.createIndex({ confirmadaEn: -1 }, { name: "reservas_confirmadas" });
  analitica.reservas.createIndex({ idTurista: 1, idServicio: 1, estado: 1 }, { name: "reservas_turista_servicio" });
  analitica.correos_pendientes.createIndex({ enviadoEn: 1, proximoIntento: 1, _id: 1 }, { name: "correos_pendientes" });
  analitica.eventos_procesados.createIndex({ procesadoEn: 1 },
    { name: "eventos_procesados_ttl", expireAfterSeconds: 30 * 24 * 3600 });
  print("    A001: índices de analytics_db");
})();
