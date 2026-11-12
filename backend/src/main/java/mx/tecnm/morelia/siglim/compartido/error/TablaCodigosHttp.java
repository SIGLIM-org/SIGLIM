package mx.tecnm.morelia.siglim.compartido.error;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;

/**
 * La única tabla {@code código del ERS → estado HTTP} del backend (convenciones de API §3.2), para
 * que el mismo código responda siempre con el mismo estado, lo lance un servicio o un trigger.
 *
 * <p>Contiene los códigos que lanza la base de datos en {@code HINT} con
 * {@code siglim.error_negocio(...)}. <b>Un código nuevo en una migración exige agregarlo aquí y en
 * convenciones §3.2 en el mismo PR</b>; {@code TablaCodigosHttpTest} lo verifica contra las
 * migraciones y contra el documento.
 *
 * <p>Refs: T-02.4, convenciones de API §3.2
 */
public final class TablaCodigosHttp {

    private static final Map<String, HttpStatus> ESTADOS = Stream.of(
            // 400: falta el motivo obligatorio.
            entradas(HttpStatus.BAD_REQUEST, "RN-021"),
            // 403: la persona no puede hacer esa acción aunque tenga el permiso.
            entradas(HttpStatus.FORBIDDEN, "VAL-015", "VAL-021", "VAL-022"),
            // 409: transición o cambio no permitido por el estado actual, o choque con otros datos.
            entradas(HttpStatus.CONFLICT,
                    "VAL-003", "VAL-005", "VAL-017", "VAL-026", "VAL-028",
                    "RF-001", "RF-017", "RF-039", "RF-046", "RF-048", "RF-062", "RF-090", "RF-100",
                    "RF-109", "RF-113", "RF-121", "RF-122", "RF-123", "RF-124", "RF-127",
                    "RN-024", "RN-039", "RNF-015", "RNF-018"),
            // 422: el contenido de la petición incumple una regla.
            entradas(HttpStatus.UNPROCESSABLE_CONTENT,
                    "VAL-001", "VAL-018", "VAL-019", "VAL-020", "VAL-025",
                    "RF-013", "RF-019", "RF-020", "RF-025", "RF-033", "RF-119", "RF-139"))
            .flatMap(s -> s)
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

    private TablaCodigosHttp() {
    }

    private static Stream<Map.Entry<String, HttpStatus>> entradas(HttpStatus estado, String... codigos) {
        return Stream.of(codigos).map(codigo -> Map.entry(codigo, estado));
    }

    /** Estado HTTP del código, si está en la tabla. */
    public static Optional<HttpStatus> estadoDe(String codigo) {
        return Optional.ofNullable(ESTADOS.get(codigo));
    }

    /** Copia de la tabla completa (para las pruebas que la comparan con migraciones y documento). */
    static Map<String, HttpStatus> todos() {
        return ESTADOS;
    }
}
