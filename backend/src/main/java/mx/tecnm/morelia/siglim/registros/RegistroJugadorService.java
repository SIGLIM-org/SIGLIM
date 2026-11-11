package mx.tecnm.morelia.siglim.registros;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.EquipoDelRegistro;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.RegistroEnTemporada;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.RequisitosMenor;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de un registro de jugador en un equipo de su club con unicidad por CURP (RF-013, RF-107,
 * CU-004).
 *
 * <ul>
 *   <li><b>Ámbito (DEC-85, ADR-0007).</b> {@code registros.registro:EDITAR} sobre el club del equipo;
 *       un equipo de otro club responde {@code 404} igual que uno inexistente (VAL-024).</li>
 *   <li><b>Identidad global (DEC-78, DEC-88).</b> La persona puede no ser visible por RLS: su
 *       existencia y sus registros en la temporada se leen con funciones {@code SECURITY DEFINER}.
 *       Del registro de otro club solo se dicen el club y el equipo, nunca su estado (un
 *       {@code SUSPENDIDO} revelaría una sanción) ni sus ids.</li>
 *   <li><b>Fecha de México (DEC-89).</b> {@code alta_en} se calcula con el {@link Clock} en
 *       {@code America/Mexico_City} y se envía explícita: {@code current_date} de la base es UTC. Con
 *       esa fecha se decide si la persona es menor.</li>
 *   <li><b>Concurrencia (DEC-90).</b> El {@code INSERT … ON CONFLICT … DO NOTHING} sobre
 *       {@code ux_registros_activo} deja la transacción sana si otra petición registró antes a la
 *       persona: se vuelve a consultar y se responde {@code 409 VAL-017} con los nombres.</li>
 *   <li><b>Auditoría (RF-093).</b> {@code REGISTRAR_REGISTRO_JUGADOR} en M-04 con identificadores y
 *       estado, sin datos personales. El historial lo escribe {@code tg_registros_historial}.</li>
 * </ul>
 *
 * <p>Refs: H-09.2, RF-003, RF-013, RF-016, RF-101, RF-107, RN-037, RN-039, RN-055, VAL-017, VAL-024,
 * VAL-025, VAL-026, CU-004, DEC-78, DEC-85 a DEC-90
 */
@Service
public class RegistroJugadorService {

    private static final String EDITAR = "registros.registro:EDITAR";
    private static final String MODULO = "M-04";
    private static final String ENTIDAD = "registros_jugador";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private final RegistroJugadorRepository registros;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final UUID ligaId;

    RegistroJugadorService(RegistroJugadorRepository registros, Autorizacion autorizacion,
            AuditoriaService auditoria, Clock reloj, SiglimPropiedades propiedades) {
        this.registros = registros;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Registra a una persona en un equipo; el registro queda {@code PENDIENTE_VALIDACION} (RF-013).
     *
     * <p>Orden de errores (DEC-85 a DEC-89):
     * <ol>
     *   <li>Sin el permiso en ningún ámbito: {@code 403 VAL-015}.</li>
     *   <li>Falta {@code personaId} o {@code equipoId}: {@code 400 VALIDACION}.</li>
     *   <li>Equipo inexistente en la liga o de un club fuera de ámbito: {@code 404} (VAL-024).</li>
     *   <li>Temporada del equipo {@code CERRADA}: {@code 409 RF-003}.</li>
     *   <li>Equipo en {@code BAJA}: {@code 422 RF-013}.</li>
     *   <li>Club sin afiliación {@code APROBADA} en la temporada: {@code 409 RF-101}.</li>
     *   <li>Persona inexistente: {@code 422 REFERENCIA_INVALIDA}.</li>
     *   <li>Registro activo en la temporada: {@code 409 VAL-017} con {@code club} y {@code equipo}.</li>
     *   <li>Registro en {@code BAJA} en otro club de la temporada: {@code 409 VAL-026}.</li>
     *   <li>Registro en {@code BAJA} en otro equipo del mismo club: {@code 409 RN-039}.</li>
     *   <li>Menor sin tutela vigente o sin consentimiento de un tutor vigente: {@code 422 VAL-025} con
     *       {@code requisito} {@code TUTOR} o {@code CONSENTIMIENTO}.</li>
     * </ol>
     */
    @Transactional
    public RegistroJugadorDto registrar(CrearRegistroJugadorRequest peticion) {
        autorizacion.alcance(EDITAR);
        if (peticion == null || peticion.personaId() == null || peticion.equipoId() == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Indica la persona y el equipo del registro.");
        }
        EquipoDelRegistro equipo = registros.buscarEquipo(ligaId, peticion.equipoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Equipo " + peticion.equipoId()));
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deClubes(equipo.clubId()));

        if (equipo.temporadaEstado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + equipo.temporadaNombre() + "» está cerrada: ya no admite registros "
                            + "de jugadores.");
        }
        if (!equipo.activo()) {
            throw ReglaNegocioException.noProcesable("RF-013",
                    "El equipo «" + equipo.nombre() + "» está dado de baja y no admite registros de "
                            + "jugadores. Elige otro equipo de tu club.");
        }
        if (!registros.clubAfiliado(equipo.clubId(), equipo.temporadaId())) {
            throw ReglaNegocioException.conflicto("RF-101",
                    "El club «" + equipo.clubNombre() + "» no tiene su tarjetón aprobado en la temporada «"
                            + equipo.temporadaNombre() + "». Tramita el tarjetón o espera a que la liga lo "
                            + "apruebe para registrar jugadores.");
        }

        UUID personaId = peticion.personaId();
        LocalDate altaEn = LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO));
        boolean esMenor = registros.esMenor(personaId, altaEn)
                .orElseThrow(() -> ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                        "La persona indicada no está registrada. Regístrala primero con su CURP."));

        List<RegistroEnTemporada> previos = registros.registrosEnTemporada(personaId, equipo.temporadaId());
        validarUnicidad(previos, equipo);
        if (esMenor) {
            validarRequisitosMenor(registros.requisitosMenor(personaId));
        }

        UUID id = registros.insertar(personaId, equipo.id(), equipo.temporadaId(), altaEn,
                        UsuarioActual.idRequerido())
                .orElseThrow(() -> registrosEnCarrera(personaId, equipo));

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("personaId", personaId.toString());
        detalle.put("equipoId", equipo.id().toString());
        detalle.put("temporadaId", equipo.temporadaId().toString());
        detalle.put("estado", EstadoRegistro.PENDIENTE_VALIDACION.name());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "REGISTRAR_REGISTRO_JUGADOR", ENTIDAD,
                id.toString(), null, detalle));

        return registros.buscar(id)
                .orElseThrow(() -> new IllegalStateException("El registro " + id + " no es visible tras guardarlo"));
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    /** VAL-017, VAL-026 y RN-039 con los registros no rechazados de la temporada (DEC-88). */
    private static void validarUnicidad(List<RegistroEnTemporada> previos, EquipoDelRegistro equipo) {
        for (RegistroEnTemporada previo : previos) {
            if (previo.estado().ocupaTemporada()) {
                throw yaRegistrada(previo, equipo);
            }
        }
        for (RegistroEnTemporada previo : previos) {
            if (previo.estado() == EstadoRegistro.BAJA && !previo.clubId().equals(equipo.clubId())) {
                throw ReglaNegocioException.conflicto("VAL-026",
                        "Esta persona estuvo registrada en otro club en esta temporada. Dentro de una "
                                + "temporada no se permite cambiar de club.");
            }
        }
        for (RegistroEnTemporada previo : previos) {
            if (previo.estado() == EstadoRegistro.BAJA && !previo.equipoId().equals(equipo.id())) {
                throw ReglaNegocioException.conflicto("RN-039",
                        "Esta persona fue dada de baja del equipo «" + previo.equipoNombre() + "» de tu club "
                                + "en esta temporada. Dentro de una temporada no se permite cambiar de equipo; "
                                + "solo puedes registrarla de nuevo en ese mismo equipo.");
            }
        }
    }

    /** La misma condición que {@code tg_registros_validar} al activar (VAL-025, DEC-89). */
    private static void validarRequisitosMenor(RequisitosMenor requisitos) {
        if (!requisitos.tieneTutorVigente()) {
            throw ReglaNegocioException.noProcesable("VAL-025",
                            "La persona es menor de edad y no tiene un tutor registrado. Registra primero a "
                                    + "su tutor y después da de alta el registro.")
                    .con("requisito", "TUTOR");
        }
        if (!requisitos.tieneConsentimiento()) {
            throw ReglaNegocioException.noProcesable("VAL-025",
                            "La persona es menor de edad y falta el consentimiento de su tutor. Registra el "
                                    + "consentimiento del tutor y después da de alta el registro.")
                    .con("requisito", "CONSENTIMIENTO");
        }
    }

    /**
     * El {@code INSERT} no devolvió fila: otra petición registró antes a la persona. La transacción
     * sigue sana, así que se consulta dónde quedó para explicarlo (DEC-90).
     */
    private ReglaNegocioException registrosEnCarrera(UUID personaId, EquipoDelRegistro equipo) {
        return registros.registrosEnTemporada(personaId, equipo.temporadaId()).stream()
                .filter(previo -> previo.estado().ocupaTemporada())
                .findFirst()
                .map(previo -> yaRegistrada(previo, equipo))
                .orElseThrow(() -> new IllegalStateException(
                        "El registro chocó con ux_registros_activo y no se encontró el registro activo"));
    }

    /**
     * {@code 409 VAL-017} con el club y el equipo donde ya está la persona, por nombre; nunca su
     * estado ni sus ids (DEC-63, DEC-88).
     */
    private static ReglaNegocioException yaRegistrada(RegistroEnTemporada previo, EquipoDelRegistro equipo) {
        String mensaje;
        if (previo.equipoId().equals(equipo.id())) {
            mensaje = "Esta persona ya está registrada en este equipo («" + previo.equipoNombre()
                    + "») en esta temporada. No hace falta registrarla otra vez.";
        } else if (previo.clubId().equals(equipo.clubId())) {
            mensaje = "Esta persona ya está registrada en tu club, equipo «" + previo.equipoNombre()
                    + "», en esta temporada. Un jugador solo puede estar en un equipo por temporada.";
        } else {
            mensaje = "Esta persona ya está registrada en esta temporada en el club «" + previo.clubNombre()
                    + "», equipo «" + previo.equipoNombre() + "». Un jugador solo puede estar en un equipo "
                    + "por temporada.";
        }
        return ReglaNegocioException.conflicto("VAL-017", mensaje)
                .con("club", previo.clubNombre())
                .con("equipo", previo.equipoNombre());
    }
}
