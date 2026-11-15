import type { TFunction } from "i18next";
import type { AccionAuditada, RegistroAuditoria } from "./auditoriaApi";

/** Plantilla del detalle según la acción (DEC-322). */
const PLANTILLAS: Record<AccionAuditada, string> = {
  PRESTADOR_APROBADO: "prestador",
  PRESTADOR_RECHAZADO: "prestador",
  SERVICIO_APROBADO: "servicio",
  SERVICIO_RECHAZADO: "servicio",
  ROL_CAMBIADO: "rol",
  RESERVA_CANCELADA: "reserva",
  CALIFICACION_OCULTADA: "calificacion",
  CALIFICACION_MOSTRADA: "calificacion",
  CALIFICACION_ELIMINADA: "calificacion",
};

/**
 * El detalle en el idioma activo (RF03, RNF08) con los datos que guarda ms-analytics desde A005 (DEC-322); los roles y
 * el tipo de entidad también se traducen. Una entrada anterior, sin datos, muestra el texto guardado (en español).
 */
export function detalleTraducido(
  registro: RegistroAuditoria,
  t: TFunction,
): string {
  const datos = registro.datos;
  if (!datos || Object.keys(datos).length === 0) return registro.detalle;
  const valores: Record<string, string> = { ...datos };
  for (const clave of ["rolAnterior", "rolNuevo"]) {
    if (valores[clave])
      valores[clave] = t(`auditoria.roles.${valores[clave]}`, {
        defaultValue: valores[clave],
      });
  }
  if (valores.tipoEntidad) {
    valores.tipoEntidad = t(`auditoria.entidades.${valores.tipoEntidad}`, {
      defaultValue: valores.tipoEntidad,
    });
  }
  // DEC-323: desde que ServicioDecidido trae el nombre comercial, la auditoría lo muestra.
  const plantilla =
    PLANTILLAS[registro.accion] === "servicio" && valores.nombreServicio
      ? "servicioConNombre"
      : PLANTILLAS[registro.accion];
  return t(`auditoria.detalles.${plantilla}`, valores);
}
