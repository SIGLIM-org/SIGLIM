package mx.tecnm.morelia.siglim.arbitraje;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.arbitraje.CandidatoArbitralDto.CausaDto;

/**
 * Elige al árbitro central de un partido entre los candidatos de {@link ElegibilidadArbitral}, como
 * dominio puro: sin Spring ni base de datos (DEC-214, DEC-216).
 *
 * <p><b>Orden entre los {@code elegible}</b> (DEC-214):
 * <ol>
 *   <li><b>[PROPUESTA]</b> primero los que no tienen la advertencia {@code RF-022} (declararon que no
 *       están disponibles en ese horario). DEC-204 no la vuelve regla de rechazo: si solo quedan
 *       candidatos con ella, se elige uno y la advertencia acompaña al resultado.</li>
 *   <li>Menor {@code carga} (designaciones vigentes en la temporada).</li>
 *   <li>Último partido designado más antiguo; sin ninguno (ausente del mapa) va antes que cualquiera
 *       con designaciones. No se usa {@code designado_en}: dentro de la transacción de una asignación
 *       {@code now()} es constante y todas las designaciones de la corrida empatarían.</li>
 *   <li>Identificador por su texto ({@code UUID::toString}, DEC-185), que coincide con el orden de
 *       PostgreSQL.</li>
 * </ol>
 * El resultado no depende del orden de la lista de entrada.
 *
 * <p><b>Sin candidato</b> (DEC-216): la causa cuenta a cada árbitro una sola vez, por su primera causa
 * en el orden {@code VAL-019}, {@code RF-039}, {@code YA_DESIGNADO}.
 *
 * <p><b>Con elegibles</b> (DEC-220): {@link #causaConElegibles} solo cuenta cuántos pueden pitarlo.
 *
 * <p>Refs: H-34.1, H-34.2, RF-022, RF-023, RF-039, RF-115, VAL-019, DEC-182, DEC-185, DEC-204, DEC-214,
 * DEC-216, DEC-220
 */
final class SelectorArbitroCentral {

    static final String NO_DISPONIBLE = "RF-022";
    static final String SIN_ARBITROS = "No hay árbitros validados en la liga.";

    private static final Comparator<UUID> POR_TEXTO = Comparator.comparing(UUID::toString);

    private SelectorArbitroCentral() {
    }

    /**
     * Elige al central o explica por qué no hay ninguno.
     *
     * @param candidatos    árbitros validados de la liga con su elegibilidad para el partido
     * @param ultimoPartido inicio del último partido de cada árbitro en la temporada; quien no aparece
     *                      no tiene designaciones
     */
    static Eleccion elegir(List<CandidatoArbitralDto> candidatos, Map<UUID, OffsetDateTime> ultimoPartido) {
        Comparator<CandidatoArbitralDto> orden = Comparator
                .comparing((CandidatoArbitralDto c) -> tieneAdvertencia(c, NO_DISPONIBLE))
                .thenComparingLong(CandidatoArbitralDto::carga)
                .thenComparing(c -> ultimo(ultimoPartido, c), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(c -> c.arbitro().id(), POR_TEXTO);
        return candidatos.stream()
                .filter(CandidatoArbitralDto::elegible)
                .min(orden)
                .<Eleccion>map(Elegido::new)
                .orElseGet(() -> new SinCandidato(causa(candidatos)));
    }

    /** Instante del último partido; {@code null} (nunca designado) va primero. */
    private static Instant ultimo(Map<UUID, OffsetDateTime> ultimoPartido, CandidatoArbitralDto candidato) {
        OffsetDateTime inicio = ultimoPartido.get(candidato.arbitro().id());
        return inicio == null ? null : inicio.toInstant();
    }

    private static boolean tieneAdvertencia(CandidatoArbitralDto candidato, String codigo) {
        return candidato.advertencias().stream().anyMatch(a -> codigo.equals(a.codigo()));
    }

    /** Texto de DEC-216 con los conteos por regla y el singular correcto. */
    static String causa(List<CandidatoArbitralDto> candidatos) {
        if (candidatos.isEmpty()) {
            return SIN_ARBITROS;
        }
        int mismoClub = 0;
        int choque = 0;
        int yaDesignado = 0;
        for (CandidatoArbitralDto candidato : candidatos) {
            List<String> codigos = candidato.causas().stream().map(CausaDto::codigo).toList();
            if (codigos.contains("VAL-019")) {
                mismoClub++;
            } else if (codigos.contains("RF-039")) {
                choque++;
            } else if (codigos.contains(ElegibilidadArbitral.YA_DESIGNADO)) {
                yaDesignado++;
            }
        }
        List<String> partes = new ArrayList<>();
        if (mismoClub > 0) {
            partes.add(mismoClub + (mismoClub == 1 ? " es" : " son") + " de los clubes del partido (VAL-019)");
        }
        if (choque > 0) {
            partes.add(choque + (choque == 1 ? " ya pita" : " ya pitan") + " a esa hora (RF-039)");
        }
        if (yaDesignado > 0) {
            partes.add(yaDesignado + (yaDesignado == 1 ? " ya está designado" : " ya están designados")
                    + " en este partido (YA_DESIGNADO)");
        }
        int total = candidatos.size();
        String inicio = total == 1
                ? "El único árbitro validado no puede pitarlo"
                : "Ninguno de los " + total + " árbitros validados puede pitarlo";
        return partes.isEmpty() ? inicio + "." : inicio + ": " + unir(partes) + ".";
    }

    /**
     * [PROPUESTA] Causa de un partido que sigue sin central aunque algún árbitro validado podría
     * pitarlo (DEC-220): la Comisión lo designa o la asignación automática lo cubre al repetirse.
     *
     * @param elegibles cuántos árbitros son {@code elegible} para el partido; al menos 1
     */
    static String causaConElegibles(int elegibles) {
        return elegibles == 1
                ? "1 árbitro validado puede pitarlo."
                : elegibles + " árbitros validados pueden pitarlo.";
    }

    /** «a», «a y b», «a, b y c». */
    private static String unir(List<String> partes) {
        if (partes.size() == 1) {
            return partes.get(0);
        }
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
    }

    /** Resultado de {@link #elegir}. */
    sealed interface Eleccion permits Elegido, SinCandidato {
    }

    /** Árbitro elegido; sus advertencias (p. ej. {@code RF-022}) acompañan a la designación. */
    record Elegido(CandidatoArbitralDto candidato) implements Eleccion {
    }

    /** Ningún árbitro puede pitar el partido. */
    record SinCandidato(String causa) implements Eleccion {
    }
}
