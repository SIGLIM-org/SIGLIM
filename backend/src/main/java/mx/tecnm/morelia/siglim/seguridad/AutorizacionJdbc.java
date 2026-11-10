package mx.tecnm.morelia.siglim.seguridad;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.AutorizacionRepositorio.AsignacionPermiso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evalúa el permiso contra las asignaciones activas del usuario de la sesión y el ámbito del
 * recurso.
 *
 * <ul>
 *   <li>Ámbito {@code LIGA}: alcanza cualquier recurso de la liga.</li>
 *   <li>Ámbito {@code CLUB}: alcanza los recursos de los clubes de esa asignación.</li>
 *   <li>Ámbito {@code PROPIO}: alcanza los de las personas de las que el usuario es titular, que
 *       resuelve {@code es_titular} en la base (RF-144).</li>
 *   <li>Ámbito desconocido: no autoriza (denegación por defecto).</li>
 * </ul>
 *
 * <p>La titularidad se consulta solo cuando hace falta: un permiso de liga o de club resuelve sin
 * tocar las personas del objetivo.
 *
 * <p>Las asignaciones se leen en cada llamada, sin caché: un cambio de permisos surte efecto en la
 * siguiente operación, también fuera de una petición HTTP (por ejemplo, una tarea programada que
 * cambia los permisos a mitad del proceso).
 *
 * <p>Refs: H-02.1, RF-094, RF-144, RN-054, RNF-003, VAL-015, VAL-024, DD-009, DP-008, ADR-0007
 */
@Service
class AutorizacionJdbc implements Autorizacion {

    /** Valores de {@code siglim.ambito} (migración). */
    private static final String AMBITO_LIGA = "LIGA";
    private static final String AMBITO_CLUB = "CLUB";
    private static final String AMBITO_PROPIO = "PROPIO";

    private final AutorizacionRepositorio repositorio;

    AutorizacionJdbc(AutorizacionRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code MANDATORY}: corre dentro de la transacción del servicio, que ya fijó el contexto de
     * usuario y el {@code search_path} (ADR-0014).
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    public void exigir(String permiso, ObjetivoAutorizacion objetivo) {
        List<AsignacionPermiso> asignaciones = asignacionesQueOtorgan(permiso);
        if (!alcanzaElObjetivo(asignaciones, objetivo)) {
            // Igual que un recurso inexistente: no se revela si existe (VAL-024). El detalle solo
            // va a la bitácora.
            throw Autorizacion.fueraDeAmbito(permiso);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code MANDATORY}: igual que {@link #exigir}, corre dentro de la transacción del servicio.
     *
     * <p>{@code noRollbackFor}: la denegación por permiso es una regla de negocio, no un fallo de
     * la base. Un servicio que prueba varios permisos y captura el 403 (M-11 acepta REDACTAR o
     * APROBAR para consultar) debe poder seguir y confirmar su transacción; sin esto, Spring marca
     * la transacción compartida como {@code rollback-only} al atravesar este método.
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY,
            noRollbackFor = ReglaNegocioException.class)
    public AlcanceAutorizacion alcance(String permiso) {
        boolean todaLaLiga = false;
        boolean propio = false;
        Set<UUID> clubes = new HashSet<>();
        for (AsignacionPermiso asignacion : asignacionesQueOtorgan(permiso)) {
            switch (asignacion.ambito()) {
                case AMBITO_LIGA -> todaLaLiga = true;
                case AMBITO_CLUB -> {
                    if (asignacion.clubId() != null) {
                        clubes.add(asignacion.clubId());
                    }
                }
                case AMBITO_PROPIO -> propio = true;
                default -> {
                    // Un ámbito que este backend no conoce no aporta nada al alcance.
                }
            }
        }
        return new AlcanceAutorizacion(todaLaLiga, clubes, propio);
    }

    /** Asignaciones activas que otorgan el permiso o 403 {@code VAL-015} si no hay ninguna. */
    private List<AsignacionPermiso> asignacionesQueOtorgan(String permiso) {
        List<AsignacionPermiso> asignaciones =
                repositorio.listarAsignacionesDelPermiso(UsuarioActual.idRequerido(), permiso);
        if (asignaciones.isEmpty()) {
            throw Autorizacion.sinPermiso();
        }
        return asignaciones;
    }

    private boolean alcanzaElObjetivo(List<AsignacionPermiso> asignaciones, ObjetivoAutorizacion objetivo) {
        boolean algunaPropia = false;
        for (AsignacionPermiso asignacion : asignaciones) {
            switch (asignacion.ambito()) {
                case AMBITO_LIGA -> {
                    return true;
                }
                case AMBITO_CLUB -> {
                    if (asignacion.clubId() != null && objetivo.clubes().contains(asignacion.clubId())) {
                        return true;
                    }
                }
                case AMBITO_PROPIO -> algunaPropia = true;
                default -> {
                    // Un ámbito que este backend no conoce no autoriza nada.
                }
            }
        }
        return algunaPropia && repositorio.esTitularDeAlguna(objetivo.personas());
    }
}
