package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.AfluenciaUseCase.AfluenciaAtractivo;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Afluencia;
import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaAlternativos.Alternativo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Afluencia de un atractivo para WF-06 (RF39, RF08, RF51; DEC-304, DEC-305): el nivel vigente (nulo sin lectura del
 * día) y hasta 3 destinos alternativos de menor concurrencia.
 */
public record RespuestaAfluencia(AfluenciaDto afluencia, List<AlternativoDto> alternativos) {

    public record AfluenciaDto(String nivel, int porcentaje, int ocupacion, int limite, Instant fecha,
                               boolean limiteAlcanzado) {
        public static AfluenciaDto de(Afluencia a) {
            return a == null ? null : new AfluenciaDto(a.nivel().name(), a.porcentaje(), a.ocupacion(), a.limite(),
                    a.fecha(), a.limiteAlcanzado());
        }
    }

    /** {@code nivel} y {@code porcentaje} nulos si el alternativo no tiene lectura del día. */
    public record AlternativoDto(String id, String nombre, String categoria, String imagen, String nivel,
                                 Integer porcentaje, double distanciaKm) {
        static AlternativoDto de(Alternativo a) {
            return new AlternativoDto(a.atractivo().id(), a.atractivo().nombre(), a.atractivo().categoria(),
                    a.atractivo().imagen(), a.afluencia() == null ? null : a.afluencia().nivel().name(),
                    a.afluencia() == null ? null : a.afluencia().porcentaje(),
                    BigDecimal.valueOf(a.distanciaKm()).setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
    }

    public static RespuestaAfluencia de(AfluenciaAtractivo resultado) {
        return new RespuestaAfluencia(AfluenciaDto.de(resultado.afluencia()),
                resultado.alternativos().stream().map(AlternativoDto::de).toList());
    }
}
