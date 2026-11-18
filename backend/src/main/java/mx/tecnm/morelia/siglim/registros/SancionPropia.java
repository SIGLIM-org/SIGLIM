package mx.tecnm.morelia.siglim.registros;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Sanción de sujeto {@code JUGADOR} de una persona, para la consulta propia y la elegibilidad
 * (H-24.1, RF-020, RF-144). La llena {@link ConsultaSancionesPropias}.
 *
 * <p>{@code tipo} y {@code estado} son los valores de los enums {@code siglim.tipo_sancion} y
 * {@code siglim.estado_sancion} como texto: el enum {@code TipoSancion} vive en {@code disciplina}, de un
 * nivel superior, y este paquete no lo importa (ADR-0006).
 *
 * <p>Refs: H-24.1, RF-020, RF-144, DEC-141, DEC-142
 *
 * @param tipo              {@code SUSPENSION_PARTIDOS}, {@code MULTA}, {@code PERDIDA_PUNTOS},
 *                          {@code PERDIDA_PARTIDO} o {@code BAJA}
 * @param estado            {@code VIGENTE}, {@code CUMPLIDA} o {@code REVOCADA}
 * @param partidos          partidos de suspensión; {@code null} si la sanción no es por partidos
 * @param partidosCumplidos partidos ya cumplidos
 * @param vigenteHasta      fin de la vigencia; {@code null} si no tiene fecha de término
 * @param articulo          artículo de la resolución; {@code null} si la resolución no lo tiene
 * @param folio             folio del expediente
 */
public record SancionPropia(
        UUID id,
        String tipo,
        String estado,
        Integer partidos,
        int partidosCumplidos,
        LocalDate vigenteDesde,
        LocalDate vigenteHasta,
        ArticuloDto articulo,
        String folio) {

    /** Artículo del reglamento que fundamenta la sanción. */
    public record ArticuloDto(String numero, String descripcion) {
    }
}
