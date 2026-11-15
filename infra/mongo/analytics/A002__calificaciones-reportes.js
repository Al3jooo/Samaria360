// A002 - analytics_db, iteración H del plan del 100 % (calificaciones y reportes; DEC-300, DEC-301).
// Colecciones del Entregable 2 (§2.4, «Análisis y notificaciones»): calificaciones (RF17, RF29, RF56; + estado de
// moderación reversible y su motivo, decisión 1 del equipo) y reportes (RF57; motivos de la lista del equipo). Técnicas:
// promedios (último promedio visible y versión monótona por entidad) y outbox_eventos (PromedioActualizado, ADR-011).
// INMUTABLE: cada validador es su propia copia (A002__*.v1.schema.json). Idempotente.
(function () {
  const fs = require("fs");
  const path = require("path");
  const analitica = db.getSiblingDB("analytics_db");

  function coleccion(nombre) {
    const validador = JSON.parse(fs.readFileSync(path.join(__dirname, "A002__" + nombre + ".v1.schema.json"), "utf8"));
    const existe = analitica.getCollectionInfos({ name: nombre }).length > 0;
    const resultado = existe
      ? analitica.runCommand({ collMod: nombre, validator: validador, validationLevel: "strict", validationAction: "error" })
      : analitica.runCommand({ create: nombre, validator: validador, validationLevel: "strict", validationAction: "error" });
    if (resultado.ok !== 1) {
      throw new Error("A002: " + (existe ? "collMod" : "create") + " de " + nombre + " falló: " + JSON.stringify(resultado));
    }
    print("    A002: " + nombre + (existe ? " ya existía; validador fijado" : " creada con su validador"));
  }

  ["calificaciones", "promedios", "reportes", "outbox_eventos"].forEach(coleccion);

  analitica.calificaciones.createIndex({ turistaId: 1, tipoEntidad: 1, idEntidad: 1 },
    { name: "calificaciones_turista_entidad", unique: true });
  analitica.calificaciones.createIndex({ tipoEntidad: 1, idEntidad: 1, estado: 1, fecha: -1 },
    { name: "calificaciones_entidad" });
  analitica.calificaciones.createIndex({ idPrestador: 1, estado: 1, fecha: -1 }, { name: "calificaciones_prestador" });
  analitica.calificaciones.createIndex({ estado: 1, fecha: -1 }, { name: "calificaciones_moderacion" });
  analitica.reportes.createIndex({ estado: 1, fecha: 1 }, { name: "reportes_estado" });
  analitica.reportes.createIndex({ turistaId: 1, tipoEntidad: 1, idEntidad: 1, estado: 1 }, { name: "reportes_turista" });
  analitica.outbox_eventos.createIndex({ publicadoEn: 1, proximoIntento: 1, _id: 1 }, { name: "outbox_pendientes" });
  analitica.outbox_eventos.createIndex({ idPublico: 1 }, { name: "outbox_id_publico", unique: true });
  print("    A002: índices de analytics_db");
})();
