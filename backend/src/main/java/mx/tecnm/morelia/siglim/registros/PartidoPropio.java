package mx.tecnm.morelia.siglim.registros;

import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;

/**
 * Partido del equipo de un registro para la consulta propia (H-24.1, RF-144). Lo llena
 * {@link ConsultaPartidosPropios}.
 *
 * <p>{@code estado} es el valor del enum {@code siglim.estado_partido} como texto: el enum
 * {@code EstadoPartido} vive en {@code programacion}, de un nivel superior (ADR-0006).
 *
 * <p>Refs: H-24.1, RF-144, DEC-140
 *
 * @param jornada   número de la jornada
 * @param inicio    fecha y hora de inicio en la zona de negocio ({@code America/Mexico_City})
 * @param cancha    nombre de la cancha
 * @param esLocal   si el equipo del registro juega como local
 * @param resultado resultado oficial vigente; {@code null} si no tiene
 */
public record PartidoPropio(
        UUID id,
        int jornada,
        OffsetDateTime inicio,
        String cancha,
        ReferenciaDto local,
        ReferenciaDto visitante,
        boolean esLocal,
        String estado,
        ResultadoDto resultado) {

    /** Resultado oficial vigente; los penales son {@code null} si no hubo tanda. */
    public record ResultadoDto(int golesLocal, int golesVisitante, Integer penalesLocal, Integer penalesVisitante) {
    }
}
