package mx.tecnm.morelia.siglim.programacion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.EquipoDeLiga;
import mx.tecnm.morelia.siglim.programacion.LocaliaRepository.CanchaLocalia;
import mx.tecnm.morelia.siglim.programacion.LocaliaRepository.FechaDeJornada;
import org.springframework.stereotype.Component;

/**
 * Advertencias de localía de la programación manual (RF-120, VAL-027). No son conflictos duros: el
 * Programador puede guardar el partido si escribe un motivo de localía (CU-006).
 *
 * <ul>
 *   <li><b>{@link Regla#CANCHA_DEL_LOCAL} (DEC-34).</b> Si el club del equipo local tiene cancha
 *       (propia, ofrecida o en comodato; DEC-33) y el partido se programa en otra, incluida la del
 *       rival, se advierte.</li>
 *   <li><b>{@link Regla#UNIDAD_DEPORTIVA} (RN-049, DEC-34).</b> En las últimas
 *       {@code fechas_finales_unidad_deportiva} fechas de la fase regular (10 [DEMO]) se advierte una
 *       cancha fuera de la unidad deportiva. Una jornada {@code numero} es de las últimas si
 *       {@code numero > fechas_fase_regular − fechas_finales}. Si la temporada no tiene definido el
 *       total de fechas, o la jornada es de una fase final, la regla no se evalúa: sin el total no
 *       se sabe cuáles son las últimas.</li>
 * </ul>
 *
 * <p>Las mismas reglas alimentan el alta, el ajuste y la verificación de la jornada, igual que
 * {@link ValidadorConflictos}.
 *
 * <p>Refs: B-15.1, RF-120, VAL-027, RN-049, DEC-33, DEC-34, CU-006
 */
@Component
class ValidadorLocalia {

    static final String CODIGO = "VAL-027";

    private final LocaliaRepository localia;

    ValidadorLocalia(LocaliaRepository localia) {
        this.localia = localia;
    }

    /**
     * Evalúa la localía de un partido sin escribir nada.
     *
     * @param grupoId   grupo de la jornada
     * @param jornadaId jornada del partido, para saber su número y si es de la fase regular
     * @param local     equipo local con su club
     * @param canchaId  cancha propuesta, ya validada como de la liga
     * @return lista vacía si el partido respeta las dos reglas
     */
    List<Advertencia> evaluar(UUID grupoId, UUID jornadaId, EquipoDeLiga local, UUID canchaId) {
        CanchaLocalia cancha = localia.buscarCancha(canchaId).orElse(null);
        if (cancha == null) {
            return List.of();
        }
        List<Advertencia> advertencias = new ArrayList<>();

        List<CanchaLocalia> delClub = localia.canchasDelClub(local.clubId(), grupoId, local.id());
        if (!delClub.isEmpty() && delClub.stream().noneMatch(propia -> propia.id().equals(cancha.id()))) {
            advertencias.add(new Advertencia(Regla.CANCHA_DEL_LOCAL,
                    "El equipo local «" + local.nombre() + "» tiene cancha (" + nombres(delClub)
                            + ") y el partido se programó en «" + cancha.nombre()
                            + "»: por la regla de localía el local juega en su cancha (DEC-34)."));
        }

        localia.buscarFecha(jornadaId)
                .filter(fecha -> !cancha.enUnidadDeportiva() && esDeLasUltimas(fecha))
                .ifPresent(fecha -> advertencias.add(new Advertencia(Regla.UNIDAD_DEPORTIVA,
                        "La jornada " + fecha.numero() + " está entre las últimas " + fecha.fechasFinales()
                                + " fechas de la fase regular (de " + fecha.fechasFaseRegular()
                                + ") y la cancha «" + cancha.nombre()
                                + "» está fuera de la unidad deportiva (RN-049). El número de fechas es un"
                                + " valor de demostración, no oficial.")));
        return advertencias;
    }

    /** RN-049: la jornada de la fase regular cae en las últimas {@code fechasFinales} fechas. */
    private static boolean esDeLasUltimas(FechaDeJornada fecha) {
        if (!fecha.faseRegular() || fecha.fechasFaseRegular() == null || fecha.fechasFinales() == null
                || fecha.fechasFinales() <= 0) {
            return false;
        }
        return fecha.numero() > fecha.fechasFaseRegular() - fecha.fechasFinales();
    }

    private static String nombres(List<CanchaLocalia> canchas) {
        return canchas.stream().map(c -> "«" + c.nombre() + "»").collect(Collectors.joining(", "));
    }

    /** Regla de localía que no se respeta. */
    enum Regla {
        /** DEC-34: el local con cancha juega en ella. */
        CANCHA_DEL_LOCAL,
        /** RN-049: en las últimas fechas de la fase regular se juega en la unidad deportiva. */
        UNIDAD_DEPORTIVA
    }

    /** Advertencia de localía con la regla y una explicación que nombra equipos y canchas. */
    record Advertencia(Regla regla, String mensaje) {
    }
}
