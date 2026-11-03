package mx.tecnm.morelia.siglim.organizacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Club afiliado a la liga (RF-008). Los campos opcionales vacíos se omiten (convenciones de API §1).
 *
 * <p>Refs: H-06.1, RF-008, RN-041
 *
 * @param cerradoEn    fecha de cierre; solo en un club {@code CERRADO}
 * @param motivoCierre motivo del cierre; solo en un club {@code CERRADO}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClubDto(
        UUID id,
        String nombre,
        EstadoClub estado,
        OffsetDateTime creadoEn,
        LocalDate cerradoEn,
        String motivoCierre) {
}
