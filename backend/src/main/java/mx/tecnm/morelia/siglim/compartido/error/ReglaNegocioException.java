package mx.tecnm.morelia.siglim.compartido.error;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Una operación incumple una regla de negocio del ERS. El servicio la lanza al validar
 * <b>antes</b> de escribir, con un mensaje que explica la causa a la persona; el
 * {@link ManejadorGlobalErrores} la convierte en RFC 9457 con su {@code codigo}.
 *
 * <pre>{@code
 * throw ReglaNegocioException.conflicto("VAL-004",
 *         "La cancha «Unidad Deportiva Morelos 2» ya tiene el partido Halcones vs. Toros de 10:00 a 11:45.");
 * }</pre>
 *
 * <p>Si la interfaz necesita más datos que el mensaje, {@link #con(String, Object)} agrega
 * extensiones al problema RFC 9457, por ejemplo en una advertencia que se puede aceptar:
 *
 * <pre>{@code
 * throw ReglaNegocioException.conflicto("RF-030", "El bloqueo afecta a 2 partidos.")
 *         .con("aceptable", true)
 *         .con("partidosAfectados", partidos);
 * }</pre>
 *
 * <p>El estado HTTP sigue las convenciones de API §3.1: el método de fábrica dice el tipo de regla.
 *
 * <p>Refs: T-02.4, convenciones de API §3
 */
public final class ReglaNegocioException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final HttpStatus estado;
    private final String codigo;
    /** Solo viaja dentro del proceso hasta el manejador; no forma parte de la forma serializada. */
    private final transient Map<String, Object> propiedades = new LinkedHashMap<>();

    private ReglaNegocioException(HttpStatus estado, String codigo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
    }

    /** 400: falta un dato obligatorio para la regla, como el motivo (RN-021). */
    public static ReglaNegocioException invalida(String codigo, String mensaje) {
        return new ReglaNegocioException(HttpStatus.BAD_REQUEST, codigo, mensaje);
    }

    /** 403: la persona no puede hacer la acción aunque tenga el permiso (VAL-021, VAL-022). */
    public static ReglaNegocioException prohibida(String codigo, String mensaje) {
        return new ReglaNegocioException(HttpStatus.FORBIDDEN, codigo, mensaje);
    }

    /** 409: choca con el estado actual de otros datos (programación, unicidad, transición). */
    public static ReglaNegocioException conflicto(String codigo, String mensaje) {
        return new ReglaNegocioException(HttpStatus.CONFLICT, codigo, mensaje);
    }

    /** 422: la petición está bien formada pero su contenido incumple una regla (elegibilidad). */
    public static ReglaNegocioException noProcesable(String codigo, String mensaje) {
        return new ReglaNegocioException(HttpStatus.UNPROCESSABLE_CONTENT, codigo, mensaje);
    }

    /**
     * Agrega una extensión al problema RFC 9457. Va a la respuesta tal cual: nunca datos personales
     * ni detalles técnicos.
     *
     * @param nombre nombre del campo en camelCase; no debe ser un campo estándar ni {@code codigo}
     * @param valor  valor que Jackson pueda serializar
     * @return esta misma excepción, para encadenar antes de lanzarla
     */
    public ReglaNegocioException con(String nombre, Object valor) {
        propiedades.put(nombre, valor);
        return this;
    }

    /** Extensiones agregadas con {@link #con(String, Object)}, en orden; vacío si no hay. */
    public Map<String, Object> propiedades() {
        return Collections.unmodifiableMap(propiedades);
    }

    public HttpStatus estado() {
        return estado;
    }

    /** Código del ERS incumplido: {@code VAL-xxx}, {@code RF-xxx} o {@code RN-xxx}. */
    public String codigo() {
        return codigo;
    }
}
