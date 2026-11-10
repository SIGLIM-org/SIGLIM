package mx.tecnm.morelia.siglim.tesoreria;

/**
 * Medio con que se pagó un cobro; refleja el enum {@code siglim.medio_pago}.
 *
 * <p>{@code EN_LINEA_SIMULADO} solo lo produce la confirmación del pago simulado (RF-138, ADR-0012):
 * el registro de un pago externo (RF-139) lo rechaza con {@code 400}, y el CHECK
 * {@code ck_pagos_medio} exige que ese medio lleve un intento de pago.
 *
 * <p>Refs: H-31.1, RF-138, RF-139, ADR-0012
 */
public enum MedioPago {
    EN_LINEA_SIMULADO,
    EFECTIVO,
    TRANSFERENCIA,
    OTRO
}
