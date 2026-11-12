package mx.tecnm.morelia.siglim.registros;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;

/**
 * Registro de un jugador en un equipo de la temporada (RF-013, RF-017), con sus referencias anidadas
 * (convenciones de API §9.1). No lleva la CURP ni otros datos personales además del nombre (§9.2).
 *
 * <p>Refs: H-09.2, H-10.2, RF-013, RF-017, RF-109, DEC-90, DEC-102
 *
 * @param altaEn        fecha de alta en {@code America/Mexico_City}
 * @param creadoEn      fecha y hora de creación en la zona de negocio
 * @param validadoPor   perfil de la liga que validó o rechazó, con su nombre mostrado; {@code null} si
 *                      sigue pendiente
 * @param validadoEn    fecha y hora de la validación en la zona de negocio; {@code null} si sigue pendiente
 * @param observaciones observaciones de la validación; {@code null} si no hubo
 */
public record RegistroJugadorDto(
        UUID id,
        PersonaResumenDto persona,
        ReferenciaDto equipo,
        ReferenciaDto club,
        ReferenciaDto categoria,
        TemporadaResumenDto temporada,
        EstadoRegistro estado,
        LocalDate altaEn,
        OffsetDateTime creadoEn,
        ReferenciaDto validadoPor,
        OffsetDateTime validadoEn,
        String observaciones) {

    /** Identificador y nombre de una entidad relacionada. */
    public record ReferenciaDto(UUID id, String nombre) {
    }

    /** La persona del registro: solo su nombre completo, sin CURP (§9.2). */
    public record PersonaResumenDto(UUID id, String nombreCompleto) {
    }

    /** Temporada del equipo con su estado. */
    public record TemporadaResumenDto(UUID id, String nombre, EstadoTemporada estado) {
    }
}
