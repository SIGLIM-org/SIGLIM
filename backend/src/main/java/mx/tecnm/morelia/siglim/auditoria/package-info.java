/**
 * M-01 Administración y seguridad: auditoría.
 *
 * <p>Registro y consulta de la auditoría inalterable (RF-093, RF-134). La consulta (H-21.1) está en
 * {@link mx.tecnm.morelia.siglim.auditoria.ConsultaAuditoriaService} y es de solo lectura; su permiso
 * se evalúa con {@link mx.tecnm.morelia.siglim.auditoria.AutorizacionConsultaAuditoria}, que implementa
 * {@code seguridad} para no crear un ciclo.
 *
 * <p>Nivel 1 de dependencias (ADR-0006): solo puede usar {@code compartido}. Si necesita
 * información de un módulo de nivel superior, lo hace con una interfaz definida en este paquete o
 * con un evento de dominio en {@code eventos}.
 */
package mx.tecnm.morelia.siglim.auditoria;
