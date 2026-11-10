package mx.tecnm.morelia.siglim.organizacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Club afiliado a la liga (RF-008). Los campos opcionales vacíos se omiten (convenciones de API §1).
 *
 * <p>Refs: H-06.1, B-04, RF-008, RF-105, RN-041, DEC-15
 *
 * @param cerradoEn    fecha de cierre; solo en un club {@code CERRADO}
 * @param motivoCierre motivo del cierre; solo en un club {@code CERRADO}
 * @param clubAnterior club cerrado del que proviene por cambio de nombre; solo si existe (DEC-15)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClubDto(
        UUID id,
        String nombre,
        EstadoClub estado,
        OffsetDateTime creadoEn,
        LocalDate cerradoEn,
        String motivoCierre,
        ClubAnteriorDto clubAnterior) {

    /** Referencia al club cerrado del que proviene este (convenciones de API §9.1). */
    public record ClubAnteriorDto(UUID id, String nombre) {
    }
}
