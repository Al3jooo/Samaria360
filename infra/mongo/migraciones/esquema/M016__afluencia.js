// M016 - Afluencia de los atractivos (plan del 100 %, iteración J; RF39, RF08, RF51, RF64; DEC-304).
//
// atractivos (validador v4): el campo opcional «ocupacionActual» {valor, fecha}, la última ocupación que registra el
// Gestor. Colección nueva «ocupaciones» (validador v1): el historial de lecturas, con el límite vigente al registrar.
// Sin relleno: un atractivo sin lectura no tiene afluencia.
//
// INMUTABLE: usa sus propias copias de los validadores (M016__*.schema.json), nunca docs/. Idempotente.

(function () {
  const fs = require("fs");
  const path = require("path");
  const catalogo = db.getSiblingDB("catalog_db");

  const atractivos = JSON.parse(fs.readFileSync(path.join(__dirname, "M016__atractivos.v4.schema.json"), "utf8"));
  const resultado = catalogo.runCommand({
    collMod: "atractivos", validator: atractivos, validationLevel: "strict", validationAction: "error"
  });
  if (resultado.ok !== 1) {
    throw new Error("M016: collMod de atractivos falló: " + JSON.stringify(resultado));
  }

  const ocupaciones = JSON.parse(fs.readFileSync(path.join(__dirname, "M016__ocupaciones.v1.schema.json"), "utf8"));
  const existe = catalogo.getCollectionInfos({ name: "ocupaciones" }).length > 0;
  const creada = existe
    ? catalogo.runCommand({ collMod: "ocupaciones", validator: ocupaciones, validationLevel: "strict", validationAction: "error" })
    : catalogo.runCommand({ create: "ocupaciones", validator: ocupaciones, validationLevel: "strict", validationAction: "error" });
  if (creada.ok !== 1) {
    throw new Error("M016: " + (existe ? "collMod" : "create") + " de ocupaciones falló: " + JSON.stringify(creada));
  }

  catalogo.ocupaciones.createIndex({ atractivoId: 1, fecha: -1 }, { name: "ocupaciones_atractivo" });
  catalogo.atractivos.createIndex({ categoria: 1 }, { name: "atractivos_categoria" });
  print("    M016: validador v4 (atractivos) con ocupacionActual; colección ocupaciones e índices");
})();
