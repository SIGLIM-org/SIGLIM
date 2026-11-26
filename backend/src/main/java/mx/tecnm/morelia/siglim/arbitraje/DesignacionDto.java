package mx.tecnm.morelia.siglim.arbitraje;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.arbitraje.ArbitroDto.ReferenciaDto;

/**
 * Designación de un árbitro a un partido (RF-023, convenciones de API §6.5).
 *
 * <p>En la vista por jornada ({@code GET /designaciones}) solo van {@code id}, {@code arbitro},
 * {@code funcion}, {@code automatica} y {@code designadoEn}: el partido ya es el contenedor y solo se
 * muestran las vigentes. Al designar y al retirar se agregan {@code partidoId}, {@code vigente} y, en
 * el retiro, {@code motivoCambio}. Los campos sin valor se omiten.
 *
 * <p>Refs: H-14.1, RF-023, RF-115, DEC-201, DEC-203
 *
 * @param id           identificador de la designación
 * @param partidoId    partido designado; solo en las respuestas de designar y retirar
 * @param arbitro      árbitro con su club; el nombre va {@code null} si RLS oculta la persona
 * @param funcion      central o asistente
 * @param vigente      si sigue vigente; solo en las respuestas de designar y retirar
 * @param automatica   verdadero si la hizo la asignación automática (DEC-182)
 * @param designadoEn  fecha y hora de la designación, en la zona de negocio
 * @param motivoCambio motivo con el que se retiró o sustituyó; solo si ya no está vigente
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesignacionDto(
        UUID id,
        UUID partidoId,
        ArbitroDesignadoDto arbitro,
        FuncionArbitral funcion,
        Boolean vigente,
        boolean automatica,
        OffsetDateTime designadoEn,
        String motivoCambio) {

    /** La misma designación sin los campos del partido, como va dentro de la vista por jornada. */
    DesignacionDto enJornada() {
        return new DesignacionDto(id, null, arbitro, funcion, null, automatica, designadoEn, null);
    }

    /**
     * Árbitro de una designación o de la lista de candidatos, sin CURP ni datos de contacto
     * (convenciones de API §9.2). {@code nombre} se serializa {@code null} cuando la política RLS de
     * {@code personas} no deja ver a la persona (DEC-78).
     *
     * @param id     árbitro
     * @param nombre nombre completo o {@code null}
     * @param club   club que lo presenta (DEC-17)
     */
    public record ArbitroDesignadoDto(UUID id, String nombre, ReferenciaDto club) {
    }
}
