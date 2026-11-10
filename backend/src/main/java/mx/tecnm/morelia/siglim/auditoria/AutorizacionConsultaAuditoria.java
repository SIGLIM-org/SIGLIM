package mx.tecnm.morelia.siglim.auditoria;

/**
 * Autoriza la consulta de la auditoría sin que este paquete dependa de {@code seguridad}.
 *
 * <p>{@code seguridad} usa a {@code auditoria} para registrar sus operaciones (ADR-0006 §2), así que
 * importarlo desde aquí crearía un ciclo. Es la «interfaz definida por quien la necesita»: la
 * declara este paquete y la implementa {@code seguridad}.
 *
 * <p>Refs: H-21.1, RF-134, VAL-015, ADR-0006, ADR-0007
 */
public interface AutorizacionConsultaAuditoria {

    /**
     * Evalúa {@code auditoria.registro:CONSULTAR} para el usuario de la sesión. Corre dentro de la
     * transacción del servicio que llama.
     *
     * @return {@code true} si el permiso alcanza toda la liga (ámbito {@code LIGA})
     * @throws mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException 403 {@code VAL-015} si
     *                         ninguna de sus asignaciones activas otorga el permiso
     */
    boolean alcanzaTodaLaLiga();
}
