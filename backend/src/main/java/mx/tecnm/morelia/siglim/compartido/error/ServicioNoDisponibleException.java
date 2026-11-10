package mx.tecnm.morelia.siglim.compartido.error;

import java.io.Serial;

/**
 * Un servicio externo (AWS S3) no respondió o rechazó la operación. Se responde 503
 * {@code SERVICIO_NO_DISPONIBLE} con un mensaje genérico (convenciones de API §3.1); la causa va a
 * la bitácora.
 *
 * <p>El mensaje es para la bitácora: nunca debe contener credenciales, URL prefirmadas ni el
 * contenido de un archivo (RNF-011).
 *
 * <p>Refs: T-04, convenciones de API §3.1
 */
public final class ServicioNoDisponibleException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
