package mx.tecnm.morelia.siglim.seguridad;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cuenta de SIGLIM con sus asignaciones de rol (RF-094, convenciones de API §6.1).
 *
 * <p>La lista devuelve solo las asignaciones activas, que son los chips de rol de F-12; el detalle
 * devuelve también las revocadas, con su fecha, para mostrar la vigencia. El correo es un dato
 * personal: las respuestas llevan {@code Cache-Control: no-store} (RNF-012, convenciones §5).
 *
 * <p>Refs: H-03.1, RF-094, RN-054
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UsuarioDto(
        UUID id,
        String nombreMostrado,
        String correo,
        boolean activo,
        OffsetDateTime creadoEn,
        List<AsignacionDto> asignaciones) {

    public UsuarioDto {
        asignaciones = List.copyOf(asignaciones);
    }

    /**
     * Asignación de rol con su ámbito y, para {@code CLUB}, su club.
     *
     * @param activa     falso en una asignación revocada, que solo aparece en el detalle
     * @param revocadaEn fecha de revocación; solo en una asignación revocada
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AsignacionDto(
            UUID id,
            RolDto rol,
            String ambito,
            ClubDto club,
            boolean activa,
            OffsetDateTime creadoEn,
            OffsetDateTime revocadaEn) {
    }

    /** Rol asignado, por su identificador y su código estable. */
    public record RolDto(UUID id, String codigo, String nombre) {
    }

    /** Club de una asignación con ámbito {@code CLUB}. */
    public record ClubDto(UUID id, String nombre) {
    }
}
