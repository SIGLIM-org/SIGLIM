package mx.tecnm.morelia.siglim.programacion;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.ConflictoRepository.BloqueCancha;
import mx.tecnm.morelia.siglim.programacion.ConflictoRepository.EquipoEnConflicto;
import mx.tecnm.morelia.siglim.programacion.ConflictoRepository.PartidoEnConflicto;
import mx.tecnm.morelia.siglim.programacion.ConflictoRepository.RestriccionCancha;
import mx.tecnm.morelia.siglim.programacion.ConflictoRepository.SancionDeEquipo;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.CanchaDeLiga;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.EquipoDeLiga;
import org.springframework.stereotype.Component;

/**
 * Validador de conflictos duros de la programación manual (RF-039, VAL-003 a VAL-006).
 *
 * <ul>
 *   <li><b>VAL-003.</b> Un equipo no puede tener otro partido cuyo bloque se traslape.</li>
 *   <li><b>VAL-004.</b> Una cancha no puede estar ocupada por otro partido en el mismo bloque.</li>
 *   <li><b>VAL-005.</b> La cancha debe estar activa y libre de bloqueos o mantenimiento. Un
 *       {@code DISPONIBLE} es informativo y no se exige (decisión D3 de H-07.1).</li>
 *   <li><b>VAL-006.</b> Ninguno de los equipos puede estar dado de baja ni tener una sanción
 *       vigente de suspensión o baja que impida jugar (RN-057).</li>
 * </ul>
 *
 * <p>El bloque de cancha sale de los parámetros vigentes de la temporada y de la categoría del
 * equipo local (RN-050, DEC-25); nunca se fija en el código. Los mensajes nombran el otro partido,
 * la cancha y el horario, como pide el criterio de aceptación de H-12. La restricción GiST y el
 * trigger {@code tg_partidos_programacion} siguen siendo la última garantía ante una carrera entre
 * dos peticiones.
 *
 * <p><b>Guardar y verificar.</b> {@link #evaluar} devuelve todos los conflictos de un partido para
 * la verificación sin guardar (H-12.3) y {@link #validar} usa esa misma evaluación para rechazar
 * el alta o el ajuste con el primero, de modo que las dos operaciones nunca discrepen.
 *
 * <p>Refs: H-12.2, H-12.3, H-12.4, RF-039, VAL-003, VAL-004, VAL-005, VAL-006, RN-050, RN-057,
 * DEC-25
 */
@Component
class ValidadorConflictos {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String MENSAJE_SIN_PARAMETROS =
            "La temporada no tiene parámetros deportivos: no se puede calcular el bloque de cancha. "
                    + "Configúralos y vuelve a intentar.";

    private final ConflictoRepository conflictos;

    ValidadorConflictos(ConflictoRepository conflictos) {
        this.conflictos = conflictos;
    }

    /**
     * Valida el partido antes de escribirlo y lanza el primer conflicto duro con un mensaje
     * explicativo. Usa {@link #evaluar} para que el alta, el ajuste y la verificación compartan
     * exactamente las mismas reglas.
     *
     * @param grupoId   grupo de la jornada, para ubicar la temporada y la categoría
     * @param partidoId partido que se ajusta, o {@code null} si se registra uno nuevo
     * @param local     equipo local, ya validado como perteneciente al grupo
     * @param visitante equipo visitante, ya validado como perteneciente al grupo
     * @param cancha    cancha bloqueada en la transacción, con su nombre y estado
     * @param inicio    inicio propuesto en la zona de negocio
     * @throws ReglaNegocioException 409 {@code VAL-003}, {@code VAL-004}, {@code VAL-005} o
     *         {@code VAL-006}; 409 {@code RF-100} si la temporada no tiene parámetros
     */
    void validar(UUID grupoId, UUID partidoId, EquipoDeLiga local, EquipoDeLiga visitante,
            CanchaDeLiga cancha, OffsetDateTime inicio) {
        List<Conflicto> encontrados = evaluar(grupoId, partidoId, local, visitante, cancha, inicio);
        if (!encontrados.isEmpty()) {
            Conflicto primero = encontrados.getFirst();
            throw ReglaNegocioException.conflicto(primero.codigo(), primero.mensaje());
        }
    }

    /**
     * Evalúa un partido y devuelve <b>todos</b> sus conflictos, en el orden en que se explican:
     * equipos no jugables (baja o sanción), cancha inactiva, parámetros de temporada, restricciones,
     * cancha ocupada y equipos ocupados. Si los dos equipos están sancionados u ocupados, se
     * devuelve un conflicto por cada uno (H-12.4). No escribe nada; lo usa la verificación de la
     * jornada (H-12.3).
     *
     * <p>Si la temporada no tiene parámetros se devuelve {@code RF-100} y se omiten los traslapes,
     * porque sin bloque de cancha no hay intervalo que comparar.
     *
     * @return lista vacía si el partido no tiene conflictos
     */
    List<Conflicto> evaluar(UUID grupoId, UUID partidoId, EquipoDeLiga local, EquipoDeLiga visitante,
            CanchaDeLiga cancha, OffsetDateTime inicio) {
        List<Conflicto> encontrados = new ArrayList<>();
        if ("BAJA".equals(local.estado())) {
            encontrados.add(new Conflicto("VAL-006", mensajeEquipoDeBaja(local)));
        }
        if ("BAJA".equals(visitante.estado())) {
            encontrados.add(new Conflicto("VAL-006", mensajeEquipoDeBaja(visitante)));
        }
        for (SancionDeEquipo sancion :
                conflictos.buscarSancionVigente(grupoId, local.id(), visitante.id(), inicio)) {
            encontrados.add(new Conflicto("VAL-006", mensajeSancion(sancion)));
        }
        if (!cancha.activa()) {
            encontrados.add(new Conflicto("VAL-005",
                    "La cancha «" + cancha.nombre() + "» está inactiva y no se puede asignar."));
        }

        BloqueCancha bloque = conflictos.calcularBloque(grupoId, local.id(), inicio);
        if (!bloque.definido()) {
            encontrados.add(new Conflicto("RF-100", MENSAJE_SIN_PARAMETROS));
            return encontrados;
        }
        OffsetDateTime finBloque = bloque.finBloque();
        conflictos.buscarRestriccion(cancha.id(), inicio, finBloque)
                .ifPresent(restriccion -> encontrados.add(
                        new Conflicto("VAL-005", mensajeRestriccion(cancha, restriccion))));
        conflictos.buscarCanchaOcupada(cancha.id(), inicio, finBloque, partidoId)
                .ifPresent(otro -> encontrados.add(new Conflicto("VAL-004",
                        "La cancha «" + cancha.nombre() + "» ya está ocupada por el partido "
                                + describirPartido(otro) + ".")));
        for (EquipoEnConflicto choque :
                conflictos.buscarEquipoOcupado(local.id(), visitante.id(), inicio, finBloque, partidoId)) {
            encontrados.add(new Conflicto("VAL-003",
                    "El equipo «" + choque.equipoNombre() + "» ya tiene el partido "
                            + describirPartido(choque.partido()) + " en la cancha «"
                            + choque.partido().canchaNombre() + "»."));
        }
        return encontrados;
    }

    // ── VAL-006: equipos elegibles ──────────────────────────────────────────────────────────────

    private static String mensajeEquipoDeBaja(EquipoDeLiga equipo) {
        return "El equipo «" + equipo.nombre() + "» está dado de baja y no se puede programar.";
    }

    private static String mensajeSancion(SancionDeEquipo sancion) {
        if ("BAJA".equals(sancion.tipo())) {
            return "El equipo «" + sancion.equipoNombre()
                    + "» tiene una sanción vigente de baja y no puede jugar.";
        }
        String partidos = sancion.partidos() == null ? "" : " de " + sancion.partidos() + " partido(s)";
        return "El equipo «" + sancion.equipoNombre()
                + "» tiene una suspensión vigente" + partidos + " y no puede jugar.";
    }

    // ── Mensajes explicativos ───────────────────────────────────────────────────────────────────

    private static String mensajeRestriccion(CanchaDeLiga cancha, RestriccionCancha restriccion) {
        String sujeto = "MANTENIMIENTO".equals(restriccion.tipo())
                ? "está en mantenimiento" : "tiene un bloqueo";
        return "La cancha «" + cancha.nombre() + "» " + sujeto + " del "
                + periodo(restriccion.desde(), restriccion.hasta()) + ": " + restriccion.motivo() + ".";
    }

    /** «Halcones vs. Toros» del 10/03/2031 10:00 a 10/03/2031 12:00. */
    private static String describirPartido(PartidoEnConflicto partido) {
        return "«" + partido.localNombre() + " vs. " + partido.visitanteNombre() + "» del "
                + periodo(partido.inicio(), partido.finBloque());
    }

    private static String periodo(OffsetDateTime desde, OffsetDateTime hasta) {
        return desde.atZoneSameInstant(ZONA_DE_NEGOCIO).format(FORMATO) + " a "
                + hasta.atZoneSameInstant(ZONA_DE_NEGOCIO).format(FORMATO);
    }

    /** Conflicto duro de un partido, listo para la verificación o para lanzarlo como excepción. */
    record Conflicto(String codigo, String mensaje) {
    }
}
