package mx.tecnm.morelia.siglim.registros;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.TemporadaResumenDto;

/**
 * Fila de {@code GET /api/v1/registros-jugador} (DEC-109). Solo lleva la CURP enmascarada (§9.2,
 * DEC-110): sin CURP completa, fecha de nacimiento, contacto ni avance de documentos.
 *
 * <p>Refs: H-10.5, RF-017, RNF-012, DEC-109, DEC-110
 *
 * @param esMenor  si la persona era menor en la fecha de alta ({@code es_menor(persona, alta_en)},
 *                 DEC-89 y DEC-92)
 * @param altaEn   fecha de alta en {@code America/Mexico_City}
 * @param creadoEn fecha y hora de creación en la zona de negocio
 */
public record RegistroJugadorResumenDto(
        UUID id,
        PersonaListadoDto persona,
        ReferenciaDto equipo,
        ReferenciaDto club,
        ReferenciaDto categoria,
        TemporadaResumenDto temporada,
        EstadoRegistro estado,
        boolean esMenor,
        LocalDate altaEn,
        OffsetDateTime creadoEn) {

    /** La persona del registro con su CURP enmascarada (DEC-110). */
    public record PersonaListadoDto(UUID id, String nombreCompleto, String curpEnmascarada) {
    }
}
