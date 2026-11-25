package mx.tecnm.morelia.siglim.notificaciones;

import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta y lectura de los avisos del usuario de la sesión (M-14.1, RF-142, UI-030).
 *
 * <ul>
 *   <li><b>Permiso (ADR-0007).</b> Todo empieza con {@code autorizacion.alcance("notificaciones.aviso:CONSULTAR")}:
 *       sin el permiso, {@code 403 VAL-015}. Cualquier ámbito basta, porque un aviso es siempre del
 *       propio perfil: el SQL filtra por {@code perfil_id} y RLS lo repite.</li>
 *   <li><b>Aviso ajeno o inexistente.</b> {@code 404}, sin distinguir uno de otro (VAL-024).</li>
 *   <li><b>Lectura idempotente.</b> Marcar de nuevo un aviso leído conserva la primera
 *       {@code leidaEn}.</li>
 *   <li><b>Sin auditoría.</b> [PROPUESTA] Leer un aviso no es una operación crítica (RF-093): no
 *       cambia ningún trámite. La operación que originó el aviso ya quedó auditada.</li>
 * </ul>
 *
 * <p>Refs: H-25.1, RF-142, UI-030, VAL-015, VAL-024, ADR-0007, ADR-0010
 */
@Service
public class NotificacionService {

    private static final int TAMANO_MAXIMO = 100;

    private final NotificacionRepository notificaciones;
    private final Autorizacion autorizacion;

    NotificacionService(NotificacionRepository notificaciones, Autorizacion autorizacion) {
        this.notificaciones = notificaciones;
        this.autorizacion = autorizacion;
    }

    /**
     * Avisos del usuario, más recientes primero, con el total de no leídos para la campana.
     *
     * @param leida {@code false} solo no leídos, {@code true} solo leídos, {@code null} todos
     */
    @Transactional(readOnly = true)
    public AvisosDelUsuario listar(Boolean leida, int pagina, int tamano) {
        autorizacion.alcance(NotificacionRepository.PERMISO_AVISOS);
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        UUID perfil = UsuarioActual.idRequerido();
        Pagina<NotificacionDto> avisos = Pagina.de(notificaciones.listar(perfil, leida, pagina, tamano), pagina,
                tamano, notificaciones.contar(perfil, leida));
        return new AvisosDelUsuario(avisos, notificaciones.contar(perfil, false));
    }

    /** Marca un aviso propio como leído; {@code 404} si no existe o es de otro usuario. */
    @Transactional
    public NotificacionDto marcarLeida(UUID id) {
        autorizacion.alcance(NotificacionRepository.PERMISO_AVISOS);
        return notificaciones.marcarLeida(UsuarioActual.idRequerido(), id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Aviso " + id));
    }

    /** Marca como leídos todos los avisos sin leer del usuario. */
    @Transactional
    public LecturaMasivaDto marcarTodas() {
        autorizacion.alcance(NotificacionRepository.PERMISO_AVISOS);
        return new LecturaMasivaDto(notificaciones.marcarTodas(UsuarioActual.idRequerido()));
    }

    /**
     * Página de avisos y conteo de no leídos (cabecera {@code X-No-Leidas}).
     *
     * @param pagina   avisos del filtro pedido
     * @param noLeidas avisos sin leer del usuario, sin importar el filtro
     */
    public record AvisosDelUsuario(Pagina<NotificacionDto> pagina, long noLeidas) {
    }
}
