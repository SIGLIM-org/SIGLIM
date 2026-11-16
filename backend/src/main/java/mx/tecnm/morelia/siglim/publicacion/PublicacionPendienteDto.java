package mx.tecnm.morelia.siglim.publicacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Jornada o comunicado en {@code REVISION} que espera el dictamen del Presidente (RF-090, RF-121).
 *
 * <p>Es la fila de la bandeja de pendientes; el detalle se pide a {@code GET /jornadas/{id}} o a
 * {@code GET /comunicados/{id}} según {@code tipo}.
 *
 * <p>Refs: H-13.1, RF-090, RF-121, UI-009
 *
 * @param tipo       jornada o comunicado
 * @param id         identificador de la jornada o del comunicado
 * @param titulo     «Jornada 3» o el título del comunicado
 * @param contexto   temporada, categoría y grupo de la jornada; se omite en un comunicado
 * @param partidos   partidos vigentes (no cancelados) de la jornada; se omite en un comunicado
 * @param enviadoPor quien la envió (Programador) o redactó (Secretaría), con su nombre visible
 * @param enviadoEn  fecha del último envío a revisión, en la zona de negocio
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicacionPendienteDto(
        TipoPublicacion tipo,
        UUID id,
        String titulo,
        String contexto,
        Long partidos,
        Perfil enviadoPor,
        OffsetDateTime enviadoEn) {

    /** Perfil interno con el nombre que muestra la aplicación (sin correo ni datos personales). */
    public record Perfil(UUID id, String nombre) {
    }
}
