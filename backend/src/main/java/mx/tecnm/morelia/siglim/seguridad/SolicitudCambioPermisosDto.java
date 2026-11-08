package mx.tecnm.morelia.siglim.seguridad;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.seguridad.SolicitudCambioPermisosRepository.FilaSolicitud;

/**
 * Solicitud de cambio de permisos pendiente de la aprobación del Presidente (RF-096, F-13).
 *
 * <p>Los permisos se exponen por su código, no por su identificador, y separados en los que se
 * agregarían y los que se quitarían al rol. {@code solicitadoPor} permite mostrar quién hizo la
 * solicitud en la pestaña de dictamen; en la auditoría no se copian nombres (RNF-012).
 *
 * <p>Refs: H-20.1, RF-096, VAL-023
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SolicitudCambioPermisosDto(
        UUID id,
        RolDto rol,
        List<String> permisosAgregados,
        List<String> permisosQuitados,
        String motivo,
        EstadoSolicitudPermisos estado,
        PerfilSolicitudDto solicitadoPor,
        OffsetDateTime solicitadoEn,
        PerfilSolicitudDto resueltoPor,
        OffsetDateTime resueltoEn,
        String observaciones) {

    /** Perfil que solicita o resuelve; solo el nombre visible en la pantalla interna. */
    public record PerfilSolicitudDto(UUID id, String nombreMostrado) {
    }

    static SolicitudCambioPermisosDto desde(FilaSolicitud fila) {
        return new SolicitudCambioPermisosDto(
                fila.id(),
                new RolDto(fila.rolId(), fila.rolCodigo(), fila.rolNombre(), fila.rolDescripcion(),
                        Ambito.valueOf(fila.rolAmbitoPredeterminado()), fila.rolAsignable()),
                fila.permisosAgregados(),
                fila.permisosQuitados(),
                fila.motivo(),
                fila.estado(),
                new PerfilSolicitudDto(fila.solicitadoPor(), fila.solicitadoPorNombre()),
                fila.solicitadoEn(),
                fila.resueltoPor() == null
                        ? null
                        : new PerfilSolicitudDto(fila.resueltoPor(), fila.resueltoPorNombre()),
                fila.resueltoEn(),
                fila.observaciones());
    }
}
