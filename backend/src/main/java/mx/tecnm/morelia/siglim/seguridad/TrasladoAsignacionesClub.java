package mx.tecnm.morelia.siglim.seguridad;

import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.seguridad.UsuarioRepository.FilaAsignacion;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Traslada las asignaciones de rol con ámbito {@code CLUB} de un club cerrado por cambio de nombre
 * al club nuevo (B-04, DEC-61).
 *
 * <p>Cada asignación activa del club anterior se <b>revoca</b> ({@code activa = false},
 * {@code revocada_en = now()}) y se crea una equivalente (mismo perfil y rol, ámbito {@code CLUB},
 * club nuevo, {@code creado_por} = usuario actual). Nunca se cambia el {@code club_id} de una fila
 * existente: el historial de quién tuvo qué ámbito se conserva (RN-005). Cada paso se audita en
 * {@code M-01} como {@code REVOCAR_ASIGNACION} (motivo «Cambio de nombre del club») y
 * {@code ASIGNAR_ROL}, con el mismo detalle que {@link UsuarioService}: solo rol, ámbito y club
 * (RNF-012).
 *
 * <p><b>Precondición:</b> no exige permisos propios. Lo llama un servicio que ya autorizó la
 * operación de toda la liga ({@code organizacion.club:EDITAR}) y que corre en una transacción; este
 * componente se une a ella ({@link Propagation#MANDATORY}) para que el cierre del club, el club
 * nuevo y el traslado se confirmen o se reviertan juntos. Tampoco aplica VAL-022: no es una cuenta
 * cambiando sus propias asignaciones, sino un efecto mecánico del cambio de nombre.
 *
 * <p>Refs: B-04, RF-094, RF-105, RN-005, RN-054, RNF-012, DEC-61
 */
@Component
public class TrasladoAsignacionesClub {

    static final String MOTIVO = "Cambio de nombre del club";

    private static final String MODULO = "M-01";
    private static final String ENTIDAD_ASIGNACION = "asignaciones_rol";
    private static final String AMBITO_CLUB = Ambito.CLUB.name();

    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    TrasladoAsignacionesClub(UsuarioRepository usuarios, AuditoriaService auditoria,
            SiglimPropiedades propiedades) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Revoca las asignaciones activas con ámbito {@code CLUB} del club anterior y las recrea en el
     * club nuevo, con su auditoría.
     *
     * @return número de asignaciones trasladadas
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int trasladar(UUID clubAnteriorId, UUID clubNuevoId) {
        List<FilaAsignacion> asignaciones = usuarios.listarAsignacionesActivasDeClub(clubAnteriorId);
        if (asignaciones.isEmpty()) {
            return 0;
        }
        UUID creadoPor = UsuarioActual.idRequerido();
        for (FilaAsignacion anterior : asignaciones) {
            usuarios.revocarAsignacion(anterior.id());
            auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "REVOCAR_ASIGNACION", ENTIDAD_ASIGNACION,
                    anterior.id().toString(), MOTIVO,
                    UsuarioService.detalleAsignacion(anterior.rolCodigo(), AMBITO_CLUB, clubAnteriorId)));

            UUID nueva = usuarios.insertarAsignacion(anterior.perfilId(), anterior.rolId(), ligaId, AMBITO_CLUB,
                    clubNuevoId, creadoPor);
            auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "ASIGNAR_ROL", ENTIDAD_ASIGNACION,
                    nueva.toString(), null,
                    UsuarioService.detalleAsignacion(anterior.rolCodigo(), AMBITO_CLUB, clubNuevoId)));
        }
        return asignaciones.size();
    }
}
