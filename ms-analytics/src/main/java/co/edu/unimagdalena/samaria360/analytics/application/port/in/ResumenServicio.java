package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import java.math.BigDecimal;

/** Un servicio calificado del Prestador con el promedio y la cantidad de sus calificaciones visibles (RF56). */
public record ResumenServicio(String idServicio, String nombre, BigDecimal promedio, long cantidad) {
}
