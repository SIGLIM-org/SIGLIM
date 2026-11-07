package mx.tecnm.morelia.siglim.compartido.contexto;

import java.util.UUID;

/**
 * Quien ejecuta la operación en curso: una persona con su JWT ({@link AutenticacionSiglim}) o el
 * perfil Sistema en una tarea programada ({@link AutenticacionSistema}).
 *
 * <p>Es {@code sealed} para que no aparezca otra forma de sesión sin revisar
 * {@link GestorTransaccionesSiglim}, que decide con ella los claims y el rol de base de datos.
 *
 * <p>Refs: B-31.1, ADR-0005, ADR-0009, ADR-0014
 */
public sealed interface SesionSiglim permits AutenticacionSiglim, AutenticacionSistema {

    /** {@code perfiles.id}: el que registran la auditoría y los historiales. */
    UUID perfilId();
}
