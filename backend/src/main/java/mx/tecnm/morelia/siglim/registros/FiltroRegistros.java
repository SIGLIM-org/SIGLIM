package mx.tecnm.morelia.siglim.registros;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;

/**
 * Filtros, orden y página de {@code GET /api/v1/registros-jugador} (convenciones de API §4, DEC-109).
 * Llegan como texto y los valida el servicio después del permiso, igual que en el resto de M-04
 * (DEC-85): un estado o un orden desconocidos, o una página fuera de rango, responden
 * {@code 400 VALIDACION}.
 *
 * <p>Un {@code temporadaId}, {@code clubId} o {@code equipoId} fuera del ámbito del usuario no es un
 * error: el filtro de ámbito del SQL deja la página vacía (ADR-0007).
 *
 * <p>Refs: H-10.5, RF-017, DEC-109
 *
 * @param estado      uno o varios {@link EstadoRegistro} separados por coma; {@code null} o en blanco
 *                    para todos
 * @param temporadaId temporada del registro; {@code null} para todas
 * @param clubId      club del equipo; {@code null} para todos
 * @param equipoId    equipo del registro; {@code null} para todos
 * @param orden       {@code creadoEn,asc} (por defecto), {@code creadoEn,desc} o {@code nombre,asc}
 * @param pagina      página, desde 0
 * @param tamano      filas por página, de 1 a 100
 */
public record FiltroRegistros(String estado, UUID temporadaId, UUID clubId, UUID equipoId, String orden, int pagina,
                              int tamano) {

    private static final int TAMANO_MAXIMO = 100;

    /** Orden aceptado por el listado; siempre desempata por el id del registro. */
    enum Orden {
        CREADO_EN_ASC("creadoEn,asc"),
        CREADO_EN_DESC("creadoEn,desc"),
        /** Apellido paterno, apellido materno y nombre. */
        NOMBRE_ASC("nombre,asc");

        private final String parametro;

        Orden(String parametro) {
            this.parametro = parametro;
        }
    }

    /**
     * Comprueba página, tamaño, estados y orden.
     *
     * @throws ReglaNegocioException 400 {@code VALIDACION} si alguno no es válido
     */
    void validar() {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw invalido("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        estados();
        ordenElegido();
    }

    /** Estados pedidos, sin repetir; vacía si no se filtra por estado. */
    List<EstadoRegistro> estados() {
        if (estado == null || estado.isBlank()) {
            return List.of();
        }
        Set<EstadoRegistro> estados = new LinkedHashSet<>();
        for (String valor : estado.split(",", -1)) {
            estados.add(estadoDe(valor.strip()));
        }
        return List.copyOf(estados);
    }

    /** El orden pedido o {@code creadoEn,asc} si no se indicó. */
    Orden ordenElegido() {
        if (orden == null || orden.isBlank()) {
            return Orden.CREADO_EN_ASC;
        }
        String valor = orden.strip();
        return Arrays.stream(Orden.values())
                .filter(opcion -> opcion.parametro.equals(valor))
                .findFirst()
                .orElseThrow(() -> invalido("El orden no es válido. Usa creadoEn,asc, creadoEn,desc o nombre,asc."));
    }

    long desplazamiento() {
        return (long) pagina * tamano;
    }

    private static EstadoRegistro estadoDe(String valor) {
        for (EstadoRegistro opcion : EstadoRegistro.values()) {
            if (opcion.name().equals(valor)) {
                return opcion;
            }
        }
        throw invalido("El estado no es válido. Usa PENDIENTE_VALIDACION, ACTIVO, SUSPENDIDO, BAJA o RECHAZADO, "
                + "separados por coma.");
    }

    private static ReglaNegocioException invalido(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
