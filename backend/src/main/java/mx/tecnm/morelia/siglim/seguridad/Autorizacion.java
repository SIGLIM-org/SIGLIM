package mx.tecnm.morelia.siglim.seguridad;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Autoriza una operación por permiso y ámbito antes de ejecutarla, con denegación por defecto
 * (ADR-0007).
 *
 * <ul>
 *   <li><b>Regla.</b> Con ámbito {@code LIGA} se permite en toda la liga; con {@code CLUB}, si el
 *       club de la asignación está entre los clubes del objetivo; con {@code PROPIO}, si el usuario
 *       es titular (él mismo o el menor bajo su tutela) de alguna de las personas del objetivo.</li>
 *   <li><b>Denegación.</b> Si ninguna asignación activa del usuario otorga el permiso,
 *       {@code 403 VAL-015}. Si alguna lo otorga pero con un ámbito que no alcanza el objetivo,
 *       {@code 404} igual que un recurso inexistente, para no revelar si existe
 *       ({@link mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException}, VAL-024).</li>
 *   <li><b>Permisos como datos.</b> Se leen de {@code asignaciones_rol}, {@code rol_permisos} y
 *       {@code permisos} en cada operación, sin caché ni claims del token, para que un cambio de
 *       permisos surta efecto de inmediato. El código <b>nunca</b> compara nombres de rol
 *       (DP-008).</li>
 *   <li><b>Barrera principal.</b> Las políticas RLS de PostgreSQL son defensa en profundidad
 *       (ADR-0014); el frontend solo oculta lo que no aplica y no es barrera.</li>
 * </ul>
 *
 * <p>Se llama al inicio de cada método público de servicio, dentro de su transacción, con el
 * objetivo que construye el módulo dueño del recurso ({@link ObjetivoAutorizacion}). Ambas
 * operaciones se ejecutan con {@code Propagation.MANDATORY}: fuera de una transacción de servicio
 * no hay contexto de usuario y la consulta correría con el usuario de conexión (ADR-0014).
 *
 * <pre>{@code
 * autorizacion.exigir("programacion.jornada:APROBAR", ObjetivoAutorizacion.deLiga());
 * }</pre>
 *
 * <p>Refs: H-02.1, RF-094, RF-144, RN-054, RNF-003, VAL-015, VAL-024, DD-009, DP-008, ADR-0007
 */
public interface Autorizacion {

    /**
     * Verifica que el usuario de la sesión pueda hacer esa operación sobre ese recurso.
     *
     * @param permiso  código {@code modulo.recurso:ACCION} del catálogo de la migración
     * @param objetivo a quién pertenece el recurso ({@link ObjetivoAutorizacion})
     * @throws mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException 403 {@code VAL-015} si
     *                         ninguna de sus asignaciones activas otorga el permiso
     * @throws mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException 404 si tiene el
     *                         permiso pero fuera del objetivo (VAL-024)
     */
    void exigir(String permiso, ObjetivoAutorizacion objetivo);

    /**
     * Resume el alcance de todas las asignaciones activas que otorgan el permiso, para que un
     * listado filtre en el SQL y su total cuente solo las filas visibles (criterio 3 de H-02,
     * ADR-0007 §2).
     *
     * <p>Agrupa <b>todas</b> las asignaciones que otorgan el permiso, no solo la primera: los
     * clubes son la unión de las asignaciones con ámbito {@code CLUB}. Un ámbito que este backend no
     * conoce no aporta nada. No consulta la titularidad: {@code propio} solo indica que el usuario
     * tiene el ámbito, y la condición {@code es_titular} la evalúa la base por fila.
     *
     * <p>El repositorio de listado aplica este patrón, con la <b>misma condición en el
     * {@code COUNT}</b> para que {@code totalElementos} no delate filas fuera de ámbito:
     *
     * <pre>{@code
     * WHERE (:todaLaLiga OR club_id = ANY(:clubes) OR (:propio AND es_titular(persona_id)))
     * }</pre>
     *
     * <p>Los clubes van como {@code SqlArrayValue("uuid", ...)} (nunca como lista unida por comas)
     * para que un conjunto vacío sea un arreglo vacío y no rompa el SQL.
     *
     * @param permiso código {@code modulo.recurso:ACCION} del catálogo de la migración
     * @return alcance del usuario para ese permiso
     * @throws mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException 403 {@code VAL-015} si
     *                         ninguna de sus asignaciones activas otorga el permiso, igual que
     *                         {@link #exigir(String, ObjetivoAutorizacion)}
     */
    AlcanceAutorizacion alcance(String permiso);

    /**
     * Excepción de la denegación por permiso: {@code 403 VAL-015}. Única fuente de este error, para
     * que la implementación y el arnés de pruebas de los controladores (H-02.3) lancen exactamente
     * la misma.
     *
     * <p>Refs: H-02.3, VAL-015, RNF-003
     */
    static ReglaNegocioException sinPermiso() {
        return ReglaNegocioException.prohibida(ErroresTecnicos.SIN_PERMISO,
                "Tu cuenta no tiene el permiso necesario para esta operación.");
    }

    /**
     * Excepción de la denegación por ámbito: {@code 404}, igual que un recurso inexistente, para no
     * revelar si existe (VAL-024). El texto, con el permiso, solo llega a la bitácora.
     *
     * <p>Refs: H-02.3, VAL-024
     *
     * @param permiso código {@code modulo.recurso:ACCION} que no alcanzó el objetivo
     */
    static RecursoNoEncontradoException fueraDeAmbito(String permiso) {
        return new RecursoNoEncontradoException(
                "El permiso " + permiso + " no alcanza el ámbito del recurso solicitado");
    }

    /**
     * Alcance del usuario para un permiso: toda la liga, un conjunto de clubes y el ámbito propio.
     *
     * <p>{@code todaLaLiga} vuelve cierta la condición sin mirar los clubes; {@code clubes} es la
     * unión de las asignaciones {@code CLUB} (puede estar vacío); {@code propio} habilita la
     * condición {@code es_titular} por fila. El frontend no recibe este record: es para el SQL de
     * los repositorios.
     */
    record AlcanceAutorizacion(boolean todaLaLiga, Set<UUID> clubes, boolean propio) {

        public AlcanceAutorizacion {
            Objects.requireNonNull(clubes, "clubes");
            clubes = Set.copyOf(clubes);
        }
    }
}
