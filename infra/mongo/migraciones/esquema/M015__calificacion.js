// M015 - Calificaciones en el catálogo (plan del 100 %, iteración H; RF17, RF61; DEC-302).
//
// servicios (validador v5) y atractivos (validador v3): el campo opcional «calificacion» {promedio, cantidad, version},
// que ms-catalog copia del evento PromedioActualizado de ms-analytics (ADR-011). Sin relleno: una entidad sin
// calificaciones no tiene el campo (la búsqueda la ordena al final con «Calificación»).
//
// INMUTABLE: usa sus propias copias de los validadores (M015__*.schema.json), nunca docs/. Idempotente.

(function () {
  const fs = require("fs");
  const path = require("path");
  const catalogo = db.getSiblingDB("catalog_db");

  [["servicios", "M015__servicios.v5.schema.json"], ["atractivos", "M015__atractivos.v3.schema.json"]]
    .forEach(([coleccion, archivo]) => {
      const validador = JSON.parse(fs.readFileSync(path.join(__dirname, archivo), "utf8"));
      const resultado = catalogo.runCommand({
        collMod: coleccion, validator: validador, validationLevel: "strict", validationAction: "error"
      });
      if (resultado.ok !== 1) {
        throw new Error("M015: collMod de " + coleccion + " falló: " + JSON.stringify(resultado));
      }
    });

  catalogo.servicios.createIndex({ "calificacion.promedio": -1, _id: 1 }, { name: "servicios_calificacion" });
  print("    M015: validadores v5 (servicios) y v3 (atractivos) con calificacion; índice servicios_calificacion");
})();
