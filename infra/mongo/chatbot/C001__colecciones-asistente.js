// C001 - chatbot_db, iteración L del plan del 100 % (ms-chatbot; DEC-279, DEC-297).
// Colecciones del Entregable 2 (§2.4, «Chatbot IA»): itinerarios_sugeridos (RF35; + nombre obligatorio, diaSugerido
// de 1 a 7 y fechaActualizacion, DEC-297) y base_conocimiento_turistico (RF05). Técnicas: uso_diario (20 mensajes por
// turista y día, DEC-297) y cuentas (proyección para el claim ver, DEC-280).
// INMUTABLE: cada validador es su propia copia (C001__*.v1.schema.json). Idempotente. Sin semilla.
(function () {
  const fs = require("fs");
  const path = require("path");
  const conversacion = db.getSiblingDB("chatbot_db");

  function coleccion(nombre) {
    const validador = JSON.parse(fs.readFileSync(path.join(__dirname, "C001__" + nombre + ".v1.schema.json"), "utf8"));
    const existe = conversacion.getCollectionInfos({ name: nombre }).length > 0;
    const resultado = existe
      ? conversacion.runCommand({ collMod: nombre, validator: validador, validationLevel: "strict", validationAction: "error" })
      : conversacion.runCommand({ create: nombre, validator: validador, validationLevel: "strict", validationAction: "error" });
    if (resultado.ok !== 1) {
      throw new Error("C001: " + (existe ? "collMod" : "create") + " de " + nombre + " falló: " + JSON.stringify(resultado));
    }
    print("    C001: " + nombre + (existe ? " ya existía; validador fijado" : " creada con su validador"));
  }

  ["itinerarios_sugeridos", "base_conocimiento_turistico", "uso_diario", "cuentas"].forEach(coleccion);

  conversacion.itinerarios_sugeridos.createIndex({ turistaId: 1, fechaActualizacion: -1 }, { name: "itinerarios_turista" });
  conversacion.base_conocimiento_turistico.createIndex({ categoria: 1, fechaActualizacion: -1 }, { name: "conocimiento_categoria" });
  conversacion.uso_diario.createIndex({ turistaId: 1, dia: 1 }, { name: "uso_turista_dia", unique: true });
  print("    C001: índices de chatbot_db");
})();
