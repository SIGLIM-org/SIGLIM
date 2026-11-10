package mx.tecnm.morelia.siglim.compartido.error;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

/**
 * Un archivo cargado no cumple las reglas de la carga de documentos (convenciones de API §3.1 y §7):
 * está vacío, supera el tamaño máximo o su firma binaria no es de un tipo permitido.
 *
 * <p>No es una {@link ReglaNegocioException}: esta lleva un código del ERS y sus estados son los de
 * las reglas de negocio (400, 403, 409, 422); aquí los códigos son técnicos y los estados 413 y 415.
 *
 * <p>Refs: T-04, ADR-0013, convenciones de API §3.1
 */
public final class ArchivoRechazadoException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final HttpStatus estado;
    private final String codigo;

    private ArchivoRechazadoException(HttpStatus estado, String codigo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
    }

    /** 400 {@code VALIDACION}: el archivo no tiene contenido. */
    public static ArchivoRechazadoException vacio() {
        return new ArchivoRechazadoException(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION,
                ErroresTecnicos.DETALLE_ARCHIVO_VACIO);
    }

    /** 413 {@code ARCHIVO_DEMASIADO_GRANDE}, con el límite en el mensaje. */
    public static ArchivoRechazadoException demasiadoGrande(DataSize limite) {
        long bytes = limite.toBytes();
        String texto = bytes % DataSize.ofMegabytes(1).toBytes() == 0
                ? limite.toMegabytes() + " MB"
                : limite.toKilobytes() + " KB";
        return new ArchivoRechazadoException(HttpStatus.CONTENT_TOO_LARGE, ErroresTecnicos.ARCHIVO_DEMASIADO_GRANDE,
                "El archivo supera el tamaño máximo de " + texto + ".");
    }

    /** 415 {@code TIPO_ARCHIVO_NO_PERMITIDO}: la firma binaria no es JPEG, PNG, WebP ni PDF. */
    public static ArchivoRechazadoException tipoNoPermitido() {
        return new ArchivoRechazadoException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErroresTecnicos.TIPO_ARCHIVO_NO_PERMITIDO, ErroresTecnicos.DETALLE_TIPO_ARCHIVO);
    }

    public HttpStatus estado() {
        return estado;
    }

    public String codigo() {
        return codigo;
    }
}
