package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.error;

/**
 * Valor del campo {@code codigo} de los ProblemDetail, con el mismo formato que ms-auth. Es estable: el frontend lo
 * traduce a ES o EN (RF03, RNF08), así que no se debe renombrar.
 */
public enum CodigoError {
    CRITERIOS_INVALIDOS,
    OFERTA_NO_DISPONIBLE,
    NO_AUTENTICADO,
    /** Token de antes de un cambio de estado o de rol de la cuenta: su claim ver es menor que la versión conocida (DEC-29). */
    SESION_INVALIDADA,
    DATOS_INVALIDOS,
    /** No existe una pregunta frecuente con ese id, o el id no es un ObjectId (RF26). */
    FAQ_NO_ENCONTRADA,
    /** Ya hay 200 preguntas frecuentes, el tope de la colección (DEC-62). */
    LIMITE_FAQ,
    /** No existe un aviso con ese id, o el id no es un ObjectId (RF40). */
    AVISO_NO_ENCONTRADO,
    /**
     * El recurso ya no está en el estado de origen: el aviso ya estaba despublicado (DEC-64) o el servicio ya no estaba
     * Pendiente (DEC-128); como DEC-39 en ms-auth. Lleva {@code estadoActual}.
     */
    ESTADO_CAMBIADO,
    /** El turista ya tiene 100 favoritos, el tope por turista (DEC-86). */
    LIMITE_FAVORITOS,
    /**
     * El prestador no puede registrar servicios: su cuenta no está en la proyección, no está Activa o no tiene RNT (RF09,
     * DEC-125, DEC-139).
     */
    PRESTADOR_NO_HABILITADO,
    /** No existe un servicio con ese id, el id no es un ObjectId o es de otro prestador (DEC-143, DEC-151). */
    SERVICIO_NO_ENCONTRADO,
    /**
     * El servicio cambió después de que el Prestador lo leyó: otra edición o la decisión del Administrador subieron su
     * versión (DEC-186). Lleva {@code versionActual}.
     */
    SERVICIO_MODIFICADO,
    /** No existe un atractivo con ese id, o el id no es un ObjectId (DEC-217). */
    ATRACTIVO_NO_ENCONTRADO,
    /**
     * El atractivo cambió después de que el Gestor lo leyó: otra edición o un cambio de estado subieron su versión
     * (DEC-207). Lleva {@code versionActual}.
     */
    ATRACTIVO_MODIFICADO,
    /** No existe una imagen con ese id, o el id no es un ObjectId (DEC-209). */
    IMAGEN_NO_ENCONTRADA,
    /** La petición multipart supera los topes del servidor (30 MB en total, 6 MB por parte; ADR-009). Es un 413. */
    CARGA_DEMASIADO_GRANDE,
    /** RF48 (DEC-313): la galería ya tiene 5 imágenes. */
    LIMITE_IMAGENES,
    /** RF48 (DEC-313): la galería debe quedar con al menos una imagen. */
    IMAGEN_MINIMA,
    ACCESO_DENEGADO,
    SOLICITUD_INVALIDA,
    ERROR_INTERNO
}
