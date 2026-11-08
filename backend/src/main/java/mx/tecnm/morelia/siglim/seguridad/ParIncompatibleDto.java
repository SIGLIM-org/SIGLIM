package mx.tecnm.morelia.siglim.seguridad;

/**
 * Par de permisos que no deben concentrarse en un mismo rol sin la aprobación del Presidente
 * (RF-096, RN-053, VAL-023).
 *
 * <p>Los dos permisos se exponen por su código, igual que el resto del catálogo.
 *
 * <p>Refs: H-20.1, RF-096, RN-053, VAL-023
 */
public record ParIncompatibleDto(String permisoA, String permisoB, String motivo) {
}
