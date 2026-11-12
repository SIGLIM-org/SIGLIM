package mx.tecnm.morelia.siglim.registros;

/**
 * Estado de validación de un documento, igual que el enum {@code siglim.estado_validacion} (RF-109).
 *
 * <p>Un documento nace {@code PENDIENTE}; la liga lo aprueba o lo rechaza una sola vez
 * ({@code tg_documentos_validar}). Un rechazado se corrige con un reemplazo (DEC-93).
 *
 * <p>Refs: H-10.1, RF-109, DEC-93, DEC-94
 */
public enum EstadoValidacion {
    PENDIENTE,
    APROBADO,
    RECHAZADO
}
