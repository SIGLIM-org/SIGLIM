package mx.tecnm.morelia.siglim.programacion;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
 * <p>Refs: H-12.2, RF-039, VAL-003, VAL-004, VAL-005, VAL-006, RN-050, RN-057, DEC-25
 */
@Component
class ValidadorConflictos {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ConflictoRepository conflictos;

    ValidadorConflictos(ConflictoRepository conflictos) {
        this.conflictos = conflictos;
    }

    /**
     * Valida el partido antes de escribirlo y lanza el primer conflicto duro con un mensaje
     * explicativo.
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
        exigirEquiposJugables(grupoId, local, visitante, inicio);
        if (!cancha.activa()) {
            throw ReglaNegocioException.conflicto("VAL-005",
                    "La cancha «" + cancha.nombre() + "» está inactiva y no se puede asignar.");
        }

        BloqueCancha bloque = conflictos.calcularBloque(grupoId, local.id(), inicio);
        if (!bloque.definido()) {
            throw ReglaNegocioException.conflicto("RF-100",
                    "La temporada no tiene parámetros deportivos: no se puede calcular el bloque de "
                            + "cancha. Configúralos y vuelve a intentar.");
        }
        OffsetDateTime finBloque = bloque.finBloque();

        conflictos.buscarRestriccion(cancha.id(), inicio, finBloque)
                .ifPresent(restriccion -> {
                    throw ReglaNegocioException.conflicto("VAL-005", mensajeRestriccion(cancha, restriccion));
                });
        conflictos.buscarCanchaOcupada(cancha.id(), inicio, finBloque, partidoId)
                .ifPresent(otro -> {
                    throw ReglaNegocioException.conflicto("VAL-004",
                            "La cancha «" + cancha.nombre() + "» ya está ocupada por el partido "
                                    + describirPartido(otro) + ".");
                });
        conflictos.buscarEquipoOcupado(local.id(), visitante.id(), inicio, finBloque, partidoId)
                .ifPresent(choque -> {
                    throw ReglaNegocioException.conflicto("VAL-003",
                            "El equipo «" + choque.equipoNombre() + "» ya tiene el partido "
                                    + describirPartido(choque.partido()) + " en la cancha «"
                                    + choque.partido().canchaNombre() + "».");
                });
    }

    // ── VAL-006: equipos elegibles ──────────────────────────────────────────────────────────────

    private void exigirEquiposJugables(UUID grupoId, EquipoDeLiga local, EquipoDeLiga visitante,
            OffsetDateTime inicio) {
        exigirEquipoActivo(local);
        exigirEquipoActivo(visitante);
        conflictos.buscarSancionVigente(grupoId, local.id(), visitante.id(), inicio)
                .ifPresent(sancion -> {
                    throw ReglaNegocioException.conflicto("VAL-006", mensajeSancion(sancion));
                });
    }

    private static void exigirEquipoActivo(EquipoDeLiga equipo) {
        if ("BAJA".equals(equipo.estado())) {
            throw ReglaNegocioException.conflicto("VAL-006",
                    "El equipo «" + equipo.nombre() + "» está dado de baja y no se puede programar.");
        }
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
}
