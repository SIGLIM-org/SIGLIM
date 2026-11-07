package mx.tecnm.morelia.siglim.compartido.contexto;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Ejecuta un proceso automático como el perfil Sistema (ADR-0005 punto 3, ADR-0009 punto 4).
 *
 * <p>Una tarea {@code @Scheduled} no tiene sesión: sin este ejecutor, {@link GestorTransaccionesSiglim}
 * adoptaría {@code siglim_publico} y {@link UsuarioActual#idRequerido()} fallaría al auditar. Dentro
 * de {@link #ejecutar(Supplier)}, cada transacción que se abra fija
 * {@code {"perfil_id":"<Sistema>"}} y {@code SET LOCAL ROLE siglim_app}, de modo que RLS se evalúa
 * con la asignación {@code SISTEMA} de ámbito {@code LIGA} y la auditoría y los historiales quedan
 * atribuidos al perfil Sistema.
 *
 * <p>Uso, desde el servicio dueño de la tarea:
 * <pre>{@code
 * ejecutorSistema.ejecutar(() -> transaccion.execute(estado -> vencerEnTransaccion(hoy)));
 * }</pre>
 * La transacción se abre <b>dentro</b> del ejecutor: el contexto se lee al iniciarla.
 *
 * <p><b>Por qué se niega en ciertos casos.</b> Ejecutar como Sistema salta la autorización por
 * permiso de la persona, así que solo se permite en hilos sin petición HTTP (el planificador o el
 * arranque). También se rechaza dentro de una transacción ya abierta, cuyo contexto quedó fijado
 * antes y atribuiría el cambio a otra sesión, y con una sesión ya presente en el hilo, para no
 * sobrescribirla. Son errores de programación ({@link IllegalStateException}).
 *
 * <p><b>Limpieza.</b> Los hilos del planificador se reutilizan: el contexto se limpia siempre en
 * {@code finally}, también si el proceso lanza una excepción.
 *
 * <p>Refs: B-31.1, ADR-0005, ADR-0009, ADR-0014
 */
@Component
public class EjecutorSistema {

    /**
     * {@code perfiles.id} del perfil Sistema, sembrado por la migración inicial (§12.1) con
     * {@code es_sistema = true} y el rol {@code SISTEMA} en ámbito {@code LIGA}. Único lugar del
     * backend donde aparece.
     */
    public static final UUID PERFIL_SISTEMA = UUID.fromString("a0000000-0000-4000-8000-000000000002");

    /**
     * Ejecuta el proceso con la sesión del perfil Sistema y la retira al terminar.
     *
     * @param proceso trabajo que abre sus propias transacciones
     * @return lo que devuelva el proceso
     * @throws IllegalStateException si se llama dentro de una petición HTTP, de una transacción o con
     *                               una sesión ya presente en el hilo
     */
    public <T> T ejecutar(Supplier<T> proceso) {
        exigirHiloSinContexto();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new AutenticacionSistema(PERFIL_SISTEMA));
        SecurityContextHolder.setContext(contexto);
        try {
            return proceso.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void exigirHiloSinContexto() {
        if (RequestContextHolder.getRequestAttributes() != null) {
            throw new IllegalStateException("El perfil Sistema no se usa dentro de una petición HTTP.");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Ejecuta como Sistema antes de abrir la transacción: su contexto ya quedó fijado.");
        }
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            throw new IllegalStateException("El hilo ya tiene una sesión; el perfil Sistema no la reemplaza.");
        }
    }
}
