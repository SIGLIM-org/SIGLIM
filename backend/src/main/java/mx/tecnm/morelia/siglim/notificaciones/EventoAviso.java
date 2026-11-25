package mx.tecnm.morelia.siglim.notificaciones;

/**
 * Eventos del flujo que generan un aviso dentro de la aplicación (RF-142). Su nombre es el valor de
 * {@code notificaciones.evento}, que la interfaz usa para el icono y el filtro de F-09.
 *
 * <p>Solo están los eventos que ya publica algún módulo. Los demás de RF-142 (cédula devuelta o con
 * plazo vencido, expediente abierto, trámite por aprobar y cobro generado) se agregan aquí cuando su
 * módulo publique el evento de dominio (ADR-0006).
 *
 * <p>Refs: H-25.1, RF-142, ADR-0010
 */
public enum EventoAviso {

    /** Cédula enviada a validación: aviso al árbitro central designado (RF-122). */
    CEDULA_PENDIENTE,

    /** Cobro que pasó a {@code VENCIDO}: aviso a la directiva del club deudor (RF-141). */
    COBRO_VENCIDO,

    /** Jornada aprobada y publicada: aviso a la directiva de los clubes que juegan en ella (RF-121). */
    PROGRAMACION_PUBLICADA
}
