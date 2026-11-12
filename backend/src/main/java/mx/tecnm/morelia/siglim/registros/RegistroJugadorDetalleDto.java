package mx.tecnm.morelia.siglim.registros;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.TemporadaResumenDto;

/**
 * Detalle de {@code GET /api/v1/registros-jugador/{id}} (DEC-109): los campos de
 * {@link RegistroJugadorDto} más {@code esMenor}, los tutores vigentes del menor y la CURP de la
 * persona, completa solo para quien tiene {@code organizacion.persona:CONSULTAR} sobre el club del
 * registro (DEC-110).
 *
 * <p>Refs: H-10.5, RF-017, RNF-012, DEC-109, DEC-110
 *
 * @param esMenor       si la persona era menor en la fecha de alta (DEC-89 y DEC-92)
 * @param altaEn        fecha de alta en {@code America/Mexico_City}
 * @param creadoEn      fecha y hora de creación en la zona de negocio
 * @param validadoPor   perfil de la liga que validó o rechazó; {@code null} si sigue pendiente
 * @param validadoEn    fecha y hora de la validación en la zona de negocio; {@code null} si sigue pendiente
 * @param observaciones observaciones de la validación; {@code null} si no hubo
 * @param tutores       tutelas vigentes del menor por fecha de creación; vacía si no es menor
 */
public record RegistroJugadorDetalleDto(
        UUID id,
        PersonaDetalleDto persona,
        ReferenciaDto equipo,
        ReferenciaDto club,
        ReferenciaDto categoria,
        TemporadaResumenDto temporada,
        EstadoRegistro estado,
        boolean esMenor,
        LocalDate altaEn,
        OffsetDateTime creadoEn,
        ReferenciaDto validadoPor,
        OffsetDateTime validadoEn,
        String observaciones,
        List<TutorDto> tutores) {

    public RegistroJugadorDetalleDto {
        tutores = tutores == null ? List.of() : List.copyOf(tutores);
    }

    /** El mismo detalle con la CURP completa solo si se puede ver y con los tutores indicados. */
    RegistroJugadorDetalleDto con(boolean mostrarCurp, List<TutorDto> tutoresVigentes) {
        PersonaDetalleDto personaVisible = mostrarCurp ? persona
                : new PersonaDetalleDto(persona.id(), persona.nombreCompleto(), persona.curpEnmascarada(), null);
        return new RegistroJugadorDetalleDto(id, personaVisible, equipo, club, categoria, temporada, estado, esMenor,
                altaEn, creadoEn, validadoPor, validadoEn, observaciones, tutoresVigentes);
    }

    /**
     * La persona del registro. {@code curpEnmascarada} siempre va; {@code curp} es {@code null} sin
     * {@code organizacion.persona:CONSULTAR} sobre el club (DEC-110).
     */
    public record PersonaDetalleDto(UUID id, String nombreCompleto, String curpEnmascarada, String curp) {
    }

    /** Tutor vigente del menor: nombre y parentesco, sin CURP ni contacto (RNF-012). */
    public record TutorDto(String nombreCompleto, String parentesco) {
    }
}
