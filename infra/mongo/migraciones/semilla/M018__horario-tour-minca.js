// QA-08: corregir solo el texto contradictorio de la semilla, conservando la oferta reservable y el inventario.
// No cambia horarios editados por el prestador ni servicios ajenos con el mismo nombre. Idempotente.
(function () {
  const catalogo = db.getSiblingDB("catalog_db");
  const resultado = catalogo.servicios.updateOne(
    {
      prestadorId: "00000000-0000-4000-8000-000000000002",
      nombreComercial: "Tour de Café en Minca",
      categoria: "Tour",
      horarioAtencion: "Salidas diarias a las 8:00 a. m. y 1:00 p. m.",
      "oferta.salidas": ["08:00", "14:00"]
    },
    {
      $set: { horarioAtencion: "Salidas diarias a las 8:00 a. m. y 2:00 p. m." },
      $inc: { version: NumberInt(1) }
    });
  print("    M018: horario de la semilla corregido en " + resultado.modifiedCount + " servicio");
})();
