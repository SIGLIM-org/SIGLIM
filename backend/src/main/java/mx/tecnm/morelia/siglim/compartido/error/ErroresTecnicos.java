package mx.tecnm.morelia.siglim.compartido.error;

import org.springframework.http.HttpStatus;

/**
 * Códigos técnicos y mensajes de los errores que no vienen de una regla del ERS (convenciones de
 * API §3.1): formato de la petición, recurso inexistente, permisos, fallas de infraestructura.
 *
 * <p>Los mensajes son para la persona que usa el sistema: en español, dicen qué pasó y qué hacer,
 * sin SQL, trazas ni nombres internos.
 *
 * <p>Refs: T-02.4, convenciones de API §3.1
 */
public final class ErroresTecnicos {

    public static final String VALIDACION = "VALIDACION";
    public static final String NO_AUTENTICADO = "NO_AUTENTICADO";
    /** Denegación por defecto (VAL-015). */
    public static final String SIN_PERMISO = "VAL-015";
    public static final String NO_ENCONTRADO = "NO_ENCONTRADO";
    public static final String METODO_NO_PERMITIDO = "METODO_NO_PERMITIDO";
    public static final String FORMATO_NO_SOPORTADO = "FORMATO_NO_SOPORTADO";
    public static final String ARCHIVO_DEMASIADO_GRANDE = "ARCHIVO_DEMASIADO_GRANDE";
    public static final String TIPO_ARCHIVO_NO_PERMITIDO = "TIPO_ARCHIVO_NO_PERMITIDO";
    public static final String DUPLICADO = "DUPLICADO";
    public static final String REFERENCIA_INVALIDA = "REFERENCIA_INVALIDA";
    public static final String CONCURRENCIA = "CONCURRENCIA";
    public static final String ERROR_INTERNO = "ERROR_INTERNO";
    public static final String SERVICIO_NO_DISPONIBLE = "SERVICIO_NO_DISPONIBLE";

    static final String DETALLE_VALIDACION = "La solicitud no es válida. Revisa los datos enviados.";
    static final String DETALLE_CAMPOS = "Revisa los campos marcados.";
    static final String DETALLE_SIN_PERMISO = "No tienes permiso para realizar esta acción.";
    /** Igual para «no existe» y «fuera de tu ámbito»: no se revela cuál de los dos (VAL-024). */
    static final String DETALLE_NO_ENCONTRADO = "El recurso no existe o no tienes acceso a él.";
    static final String DETALLE_METODO = "Esta operación no está disponible para este recurso.";
    static final String DETALLE_FORMATO = "El formato de la solicitud no es compatible. Envía y acepta JSON.";
    static final String DETALLE_ARCHIVO = "El archivo supera el tamaño permitido.";
    static final String DETALLE_TIPO_ARCHIVO = "El tipo de archivo no está permitido. Usa JPG, PNG, WebP o PDF.";
    static final String DETALLE_ARCHIVO_VACIO = "El archivo está vacío. Selecciona otro archivo.";
    static final String DETALLE_DUPLICADO = "Ya existe un registro con esos datos.";
    static final String DETALLE_REFERENCIA =
            "Uno de los datos hace referencia a un registro que no existe. Actualiza la página e intenta de nuevo.";
    static final String DETALLE_REGLA = "Los datos no cumplen una regla de validación.";
    static final String DETALLE_CONCURRENCIA =
            "Otra persona modificó estos datos al mismo tiempo. Recarga la página e intenta de nuevo.";
    static final String DETALLE_INTERNO =
            "Ocurrió un error inesperado. Si se repite, comparte con el administrador el identificador del error.";
    static final String DETALLE_NO_DISPONIBLE =
            "El servicio no está disponible por el momento. Intenta de nuevo en unos minutos.";

    private ErroresTecnicos() {
    }

    /** Código técnico para un error de Spring MVC del que solo se conoce el estado HTTP. */
    static String codigoPara(HttpStatus estado) {
        return switch (estado) {
            case UNAUTHORIZED -> NO_AUTENTICADO;
            case FORBIDDEN -> SIN_PERMISO;
            case NOT_FOUND -> NO_ENCONTRADO;
            case METHOD_NOT_ALLOWED -> METODO_NO_PERMITIDO;
            case NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE -> FORMATO_NO_SOPORTADO;
            case CONTENT_TOO_LARGE -> ARCHIVO_DEMASIADO_GRANDE;
            case SERVICE_UNAVAILABLE -> SERVICIO_NO_DISPONIBLE;
            default -> estado.is4xxClientError() ? VALIDACION : ERROR_INTERNO;
        };
    }

    /** Mensaje para la persona a partir del estado HTTP. */
    static String detallePara(HttpStatus estado) {
        return switch (estado) {
            case FORBIDDEN -> DETALLE_SIN_PERMISO;
            case NOT_FOUND -> DETALLE_NO_ENCONTRADO;
            case METHOD_NOT_ALLOWED -> DETALLE_METODO;
            case NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE -> DETALLE_FORMATO;
            case CONTENT_TOO_LARGE -> DETALLE_ARCHIVO;
            case SERVICE_UNAVAILABLE -> DETALLE_NO_DISPONIBLE;
            default -> estado.is4xxClientError() ? DETALLE_VALIDACION : DETALLE_INTERNO;
        };
    }

    /** Título corto del tipo de error. */
    static String tituloPara(HttpStatus estado) {
        return switch (estado) {
            case BAD_REQUEST -> "Solicitud inválida";
            case UNAUTHORIZED -> "No autenticado";
            case FORBIDDEN -> "Acción no permitida";
            case NOT_FOUND -> "Recurso no encontrado";
            case METHOD_NOT_ALLOWED -> "Método no permitido";
            case NOT_ACCEPTABLE, UNSUPPORTED_MEDIA_TYPE -> "Formato no soportado";
            case CONFLICT -> "Conflicto con el estado actual";
            case CONTENT_TOO_LARGE -> "Archivo demasiado grande";
            case UNPROCESSABLE_CONTENT -> "No se puede procesar";
            case SERVICE_UNAVAILABLE -> "Servicio no disponible";
            default -> estado.is4xxClientError() ? "Solicitud inválida" : "Error interno";
        };
    }
}
