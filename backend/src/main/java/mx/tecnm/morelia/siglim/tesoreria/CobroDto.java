package mx.tecnm.morelia.siglim.tesoreria;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cobro a un club o a un equipo (RF-137, RF-140), con las referencias anidadas para que la interfaz
 * no haga una petición por fila (convenciones de API §9.1). Los campos opcionales vacíos se omiten
 * (§1).
 *
 * <p>El monto es {@link BigDecimal} en pesos mexicanos con dos decimales (convenciones de API §1).
 *
 * <p>Refs: H-29.1, RF-137, RF-140, UI-027
 *
 * @param equipo el equipo al que se carga; se omite en un cobro al club
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CobroDto(
        UUID id,
        TipoCobro tipo,
        String concepto,
        BigDecimal monto,
        LocalDate periodoInicio,
        LocalDate fechaLimite,
        EstadoCobro estado,
        ReferenciaDto club,
        ReferenciaDto equipo,
        OffsetDateTime creadoEn) {

    /** Identificador y nombre de una entidad relacionada. */
    public record ReferenciaDto(UUID id, String nombre) {
    }
}
