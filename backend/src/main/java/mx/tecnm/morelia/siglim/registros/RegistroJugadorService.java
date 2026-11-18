package mx.tecnm.morelia.siglim.registros;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.registros.DocumentoRegistroRepository.RegistroDeDocumentos;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDetalleDto.TutorDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.EquipoDelRegistro;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.RegistroAnterior;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.RegistroEnTemporada;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.RequisitosMenor;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.Avance;
import mx.tecnm.morelia.siglim.registros.RequisitosRegistroDto.RequisitoDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de un registro de jugador en un equipo de su club con unicidad por CURP (RF-013, RF-107,
 * CU-004), su validación por la liga (RF-017, RF-018, RF-109, VAL-001) y su baja (RF-014, RN-021).
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
 *   <li><b>Auditoría (RF-093).</b> {@code REGISTRAR_REGISTRO_JUGADOR} y
 *       {@code VALIDAR_REGISTRO_JUGADOR} en M-04 con identificadores y estado, sin datos personales.
 *       El historial lo escribe {@code tg_registros_historial}.</li>
 *   <li><b>Validación (H-10.2, DEC-97 a DEC-102).</b> {@code registros.registro:VALIDAR} sobre el club
 *       del equipo. El registro se bloquea con el mismo {@code bloquearRegistro} de la validación de
 *       documentos (DEC-93), así que VAL-001 se calcula con {@link RequisitosDocumentos} sobre datos
 *       estables; el {@code UPDATE} es condicional. {@code tg_registros_validar} repite VAL-015,
 *       VAL-001 y VAL-025 como última garantía (DEC-101).</li>
 *   <li><b>Consulta (H-10.5, DEC-109, DEC-110).</b> {@code registros.registro:CONSULTAR}: el listado
 *       filtra por ámbito en el SQL ({@code alcance}) y el detalle exige el club o la persona del
 *       registro, como los requisitos. La CURP va enmascarada en el listado y completa en el detalle
 *       solo con {@code organizacion.persona:CONSULTAR} sobre el club ({@link Autorizacion#permite}).
 *       Las consultas no se auditan: no cambian nada.</li>
 *   <li><b>Baja (H-23.1, DEC-118 a DEC-124).</b> {@code registros.registro:EDITAR} sobre el club del
 *       equipo, sin separación de funciones; solo un registro {@code ACTIVO} se da de baja, con motivo
 *       de 10 a 500 caracteres que va al historial y a la auditoría ({@code DAR_BAJA_REGISTRO_JUGADOR}).
 *       {@code baja_en} es la fecha de México. Se bloquea el registro con {@code bloquearRegistro} y el
 *       {@code UPDATE} es condicional. Un equipo en {@code BAJA} no la impide.
 *       {@code SUSPENDIDO → BAJA} queda para la sanción de M-09.</li>
 *   <li><b>Renovación y transferencia (B-06, DEC-146).</b> {@code registros.registro:EDITAR} sobre el
 *       club destino; el registro anterior es el más reciente de la persona en una temporada previa
 *       ({@code siglim.registro_anterior}). La renovación exige el mismo club y reutiliza los
 *       documentos aprobados del anterior; la transferencia exige otro club y que la temporada
 *       destino permita el cambio en sus parámetros (RF-111). VAL-026/RN-039 impiden el cambio
 *       dentro de la temporada. El registro nuevo queda {@code PENDIENTE_VALIDACION} con
 *       {@code registro_anterior_id}; se audita como {@code RENOVAR_REGISTRO} o
 *       {@code TRANSFERIR_REGISTRO}.</li>
 *   <li><b>Historial (B-07.1).</b> {@code registros.registro:CONSULTAR} sobre el club del registro,
 *       solo lectura y sin auditar (RF-018, RNF-015).</li>
 * </ul>
 *
 * <p>Refs: B-06, B-07.1, H-09.2, H-10.2, H-10.5, H-23.1, RF-003, RF-013, RF-014, RF-015, RF-016, RF-017, RF-018, RF-093,
 * RF-101, RF-107, RF-109, RF-111, RN-021, RN-037, RN-039, RN-055, RNF-012, RNF-015, VAL-001, VAL-015, VAL-017, VAL-022,
 * VAL-024, VAL-025, VAL-026, CU-004, DEC-13, DEC-78, DEC-85 a DEC-90, DEC-97 a DEC-102, DEC-109, DEC-110,
 * DEC-118 a DEC-124, DEC-146, DEC-153
 */
@Service
public class RegistroJugadorService {

    private static final String EDITAR = "registros.registro:EDITAR";
    private static final String VALIDAR = "registros.registro:VALIDAR";
    private static final String CONSULTAR = "registros.registro:CONSULTAR";
    /** Permiso que deja ver la CURP completa en el detalle (convenciones §9.2, DEC-110). */
    private static final String CONSULTAR_PERSONA = "organizacion.persona:CONSULTAR";
    private static final String MODULO = "M-04";
    private static final String ENTIDAD = "registros_jugador";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final int MINIMO_OBSERVACIONES = 10;
    private static final int MAXIMO_OBSERVACIONES = 500;
    /** Motivo del historial cuando la liga activa un registro sin observaciones (DEC-98). */
    static final String MOTIVO_APROBACION = "Registro validado por la liga";

    private final RegistroJugadorRepository registros;
    private final DocumentoRegistroRepository documentos;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final Clock reloj;
    private final UUID ligaId;

    RegistroJugadorService(RegistroJugadorRepository registros, DocumentoRegistroRepository documentos,
            Autorizacion autorizacion, AuditoriaService auditoria, MotivoOperacion motivoOperacion, Clock reloj,
            SiglimPropiedades propiedades) {
        this.registros = registros;
        this.documentos = documentos;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
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
            validarRequisitosMenor(registros.requisitosMenor(personaId),
                    "Registra primero a su tutor y después da de alta el registro.",
                    "Registra el consentimiento del tutor y después da de alta el registro.");
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

    // ── Renovación y transferencia entre temporadas (B-06, DEC-146) ──────────────────────────────

    /**
     * Renueva en una temporada nueva el registro de una persona que sigue en el mismo club (RF-015).
     * Crea un registro {@code PENDIENTE_VALIDACION} en el equipo de destino y lo enlaza con el último
     * registro de la persona en una temporada anterior ({@code registro_anterior_id}); los documentos
     * aprobados de sus registros anteriores del mismo club cuentan para VAL-001 (DEC-146).
     *
     * <p>Orden de errores (el mismo del alta más los de DEC-146):
     * <ol>
     *   <li>Sin {@code registros.registro:EDITAR} en ningún ámbito: {@code 403 VAL-015}.</li>
     *   <li>Falta {@code personaId} o {@code equipoId}: {@code 400 VALIDACION}.</li>
     *   <li>Equipo inexistente en la liga o de un club fuera de ámbito: {@code 404} (VAL-024).</li>
     *   <li>Temporada del equipo {@code CERRADA}: {@code 409 RF-003}.</li>
     *   <li>Equipo en {@code BAJA}: {@code 422 RF-013}.</li>
     *   <li>Club sin afiliación {@code APROBADA} en la temporada: {@code 409 RF-101}.</li>
     *   <li>Persona inexistente: {@code 422 REFERENCIA_INVALIDA}.</li>
     *   <li>Registro activo en la temporada destino: {@code 409 VAL-017}; una baja en otro club de
     *       esa temporada, {@code 409 VAL-026}; en otro equipo del mismo club, {@code 409 RN-039}.</li>
     *   <li>Sin registro anterior en una temporada previa: {@code 422 RF-015}.</li>
     *   <li>El registro anterior es de otro club: {@code 422 RF-015} (usa la transferencia).</li>
     *   <li>Menor sin tutela vigente o sin consentimiento: {@code 422 VAL-025}.</li>
     * </ol>
     */
    @Transactional
    public RegistroJugadorDto renovar(MovimientoRegistroRequest peticion) {
        return movimiento(TipoMovimientoRegistro.RENOVACION, peticion);
    }

    /**
     * Transfiere a una persona a un equipo de otro club en una temporada nueva (RF-016, RF-111). El
     * registro queda {@code PENDIENTE_VALIDACION} y enlazado con el último registro de la persona en
     * una temporada anterior; también conserva el historial de clubes (RF-012, DEC-13).
     *
     * <p>Valen las mismas reglas de {@link #renovar}, con dos propias de la transferencia:
     * <ul>
     *   <li>El registro anterior debe ser de otro club; si es del mismo, {@code 422 RF-016} (usa la
     *       renovación).</li>
     *   <li>La temporada destino debe permitir el cambio de club en su versión vigente de
     *       parámetros; si no, {@code 409 RF-111} (RF-111, DEC-13). Sin versión de parámetros rige el
     *       valor por omisión de la tabla, {@code true}.</li>
     * </ul>
     * Una transferencia no hereda los documentos del club de origen: el club destino carga los que
     * correspondan al registro nuevo (DEC-146).
     */
    @Transactional
    public RegistroJugadorDto transferir(MovimientoRegistroRequest peticion) {
        return movimiento(TipoMovimientoRegistro.TRANSFERENCIA, peticion);
    }

    /**
     * Qué movimiento entre temporadas puede ofrecer el asistente de alta (F-18) para la persona y el
     * equipo de destino (B-06.2, DEC-153). Es de solo lectura y no se audita. Sigue las mismas reglas
     * que {@link #renovar} y {@link #transferir}, que vuelven a validar al enviar:
     * <ul>
     *   <li>Sin {@code registros.registro:EDITAR} en ningún ámbito: {@code 403 VAL-015}.</li>
     *   <li>Falta {@code personaId} o {@code equipoId}: {@code 400 VALIDACION}.</li>
     *   <li>Equipo inexistente o de un club fuera de ámbito: {@code 404} (VAL-024).</li>
     *   <li>La persona ya tiene un registro no rechazado en la temporada destino, o no tiene registro
     *       anterior: {@code tipo} nulo.</li>
     *   <li>Registro anterior del mismo club: {@code RENOVACION}, permitida.</li>
     *   <li>De otro club: {@code TRANSFERENCIA}, permitida solo si la temporada destino admite el
     *       cambio de club (RF-111); si no, {@code motivo} explica la configuración de la Liga.</li>
     * </ul>
     * Nunca responde el club, el equipo, el estado ni los ids del registro anterior (DEC-88, DEC-90).
     */
    @Transactional(readOnly = true)
    public OpcionMovimientoDto opcionMovimiento(UUID personaId, UUID equipoId) {
        autorizacion.alcance(EDITAR);
        if (personaId == null || equipoId == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Indica la persona y el equipo de destino.");
        }
        EquipoDelRegistro equipo = registros.buscarEquipo(ligaId, equipoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Equipo " + equipoId));
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deClubes(equipo.clubId()));

        boolean registradaEnLaTemporada = registros.registrosEnTemporada(personaId, equipo.temporadaId()).stream()
                .anyMatch(previo -> previo.estado() != EstadoRegistro.RECHAZADO);
        if (registradaEnLaTemporada) {
            return OpcionMovimientoDto.ninguna();
        }
        return registros.registroAnterior(personaId, equipo.temporadaId())
                .map(anterior -> {
                    if (anterior.clubId().equals(equipo.clubId())) {
                        return new OpcionMovimientoDto(TipoMovimientoRegistro.RENOVACION.name(), true,
                                anterior.temporadaNombre(), null);
                    }
                    boolean permitido = registros.permiteCambioClub(equipo.temporadaId());
                    return new OpcionMovimientoDto(TipoMovimientoRegistro.TRANSFERENCIA.name(), permitido,
                            anterior.temporadaNombre(), permitido ? null : sinCambioDeClub(equipo));
                })
                .orElseGet(OpcionMovimientoDto::ninguna);
    }

    /** Mensaje de RF-111: qué pasó y qué hacer, sin datos del club de origen (PEN-016). */
    private static String sinCambioDeClub(EquipoDelRegistro equipo) {
        return "La temporada «" + equipo.temporadaNombre() + "» no permite cambios de club entre "
                + "temporadas. La liga puede habilitarlo en los parámetros de la temporada.";
    }

    /** Reglas comunes de la renovación y la transferencia (RF-015, RF-016, RF-111, VAL-026). */
    private RegistroJugadorDto movimiento(TipoMovimientoRegistro tipo, MovimientoRegistroRequest peticion) {
        autorizacion.alcance(EDITAR);
        if (peticion == null || peticion.personaId() == null || peticion.equipoId() == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Indica la persona y el equipo de destino.");
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

        // La temporada destino no admite un segundo registro activo ni un cambio de club (VAL-017,
        // VAL-026, RN-039); la renovación y la transferencia son entre temporadas.
        validarUnicidad(registros.registrosEnTemporada(personaId, equipo.temporadaId()), equipo);

        RegistroAnterior anterior = registros.registroAnterior(personaId, equipo.temporadaId())
                .orElseThrow(() -> ReglaNegocioException.noProcesable(tipo.codigo(), sinRegistroAnterior(tipo)));
        boolean mismoClub = anterior.clubId().equals(equipo.clubId());
        if (tipo == TipoMovimientoRegistro.RENOVACION && !mismoClub) {
            throw ReglaNegocioException.noProcesable("RF-015",
                    "El registro anterior es del club «" + anterior.clubNombre() + "»: para cambiarlo de "
                            + "club usa la transferencia.");
        }
        if (tipo == TipoMovimientoRegistro.TRANSFERENCIA && mismoClub) {
            throw ReglaNegocioException.noProcesable("RF-016",
                    "El registro anterior ya es de tu club: para conservar al jugador usa la renovación.");
        }
        if (!mismoClub && !registros.permiteCambioClub(equipo.temporadaId())) {
            throw ReglaNegocioException.conflicto("RF-111", sinCambioDeClub(equipo));
        }
        if (esMenor) {
            validarRequisitosMenor(registros.requisitosMenor(personaId),
                    "Registra primero a su tutor y después " + tipo.verbo() + " el registro.",
                    "Registra el consentimiento del tutor y después " + tipo.verbo() + " el registro.");
        }

        String motivo = mismoClub
                ? "Renovación desde la temporada «" + anterior.temporadaNombre() + "»"
                : "Transferencia desde el club «" + anterior.clubNombre() + "» de la temporada «"
                        + anterior.temporadaNombre() + "»";
        motivoOperacion.fijar(motivo);
        UUID id = registros.insertar(personaId, equipo.id(), equipo.temporadaId(), altaEn, anterior.id(),
                        UsuarioActual.idRequerido())
                .orElseThrow(() -> registrosEnCarrera(personaId, equipo));

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("personaId", personaId.toString());
        detalle.put("equipoId", equipo.id().toString());
        detalle.put("temporadaId", equipo.temporadaId().toString());
        detalle.put("registroAnteriorId", anterior.id().toString());
        detalle.put("clubAnteriorId", anterior.clubId().toString());
        detalle.put("equipoAnteriorId", anterior.equipoId().toString());
        detalle.put("temporadaAnteriorId", anterior.temporadaId().toString());
        detalle.put("estado", EstadoRegistro.PENDIENTE_VALIDACION.name());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, tipo.accion(), ENTIDAD, id.toString(),
                motivo, detalle));

        return registros.buscar(id)
                .orElseThrow(() -> new IllegalStateException("El registro " + id + " no es visible tras guardarlo"));
    }

    /** Mensaje cuando la persona no tiene registros en temporadas anteriores (RF-015, RF-016). */
    private static String sinRegistroAnterior(TipoMovimientoRegistro tipo) {
        String accion = tipo == TipoMovimientoRegistro.RENOVACION ? "renovar" : "transferir";
        return "La persona no tiene un registro anterior en una temporada previa de la liga: no hay nada que "
                + accion + ". Da de alta su registro en el equipo.";
    }

    /**
     * Activa o rechaza un registro {@code PENDIENTE_VALIDACION} (RF-017, RF-109, CU-004). Guarda el
     * estado, quién y cuándo validó y las observaciones; el historial lleva como motivo las
     * observaciones o, si se activa sin ellas, «Registro validado por la liga» (DEC-98).
     *
     * <p>Orden de errores (DEC-97, DEC-100 y DEC-99):
     * <ol>
     *   <li>Sin {@code registros.registro:VALIDAR} en ningún ámbito: {@code 403 VAL-015}.</li>
     *   <li>Falta {@code decision}: {@code 400 VALIDACION}.</li>
     *   <li>Observaciones de más de 500 caracteres: {@code 400 VALIDACION}.</li>
     *   <li>Rechazo sin observaciones de al menos 10 caracteres (sin contar los espacios de los
     *       extremos): {@code 400 RF-109}.</li>
     *   <li>Registro inexistente en la liga o de un club fuera de ámbito: {@code 404} (VAL-024).</li>
     *   <li>Se bloquea el registro (DEC-102).</li>
     *   <li>Quien valida es quien dio de alta el registro: {@code 403 VAL-022} <b>[PROPUESTA]</b>.</li>
     *   <li>Temporada {@code CERRADA}: {@code 409 RF-003}; registro que no está
     *       {@code PENDIENTE_VALIDACION}: {@code 409 RF-017}.</li>
     *   <li>Solo al activar: equipo en {@code BAJA} ({@code 422 RF-013}); documentos aplicables sin
     *       aprobar ({@code 422 VAL-001} con {@code faltantes}); menor sin tutor vigente o sin
     *       consentimiento ({@code 422 VAL-025} con {@code requisito}).</li>
     *   <li>Otra petición lo validó entre tanto: {@code 409 RF-017}.</li>
     * </ol>
     */
    @Transactional
    public RegistroJugadorDto validar(UUID registroId, ValidarRegistroRequest peticion) {
        autorizacion.alcance(VALIDAR);
        DecisionRegistro decision = peticion == null ? null : peticion.decision();
        if (decision == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Indica si el registro se activa o se rechaza.");
        }
        String observaciones = observacionesOpcionales(peticion.observaciones());
        if (decision == DecisionRegistro.RECHAZADO && (observaciones == null
                || observaciones.length() < MINIMO_OBSERVACIONES)) {
            throw ReglaNegocioException.invalida("RF-109", "Escribe observaciones de al menos "
                    + MINIMO_OBSERVACIONES + " caracteres para rechazar el registro: el club las verá para saber "
                    + "qué pasó.");
        }

        RegistroDeDocumentos encontrado = documentos.buscarRegistro(ligaId, registroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro " + registroId));
        autorizacion.exigir(VALIDAR, ObjetivoAutorizacion.deClubes(encontrado.clubId()));
        RegistroDeDocumentos registro = documentos.bloquearRegistro(ligaId, registroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro " + registroId));

        UUID perfil = UsuarioActual.idRequerido();
        // [PROPUESTA] DEC-97: separación de funciones, como DEC-79 y DEC-91.
        if (perfil.equals(registro.creadoPor())) {
            throw ReglaNegocioException.prohibida("VAL-022",
                    "No puedes validar un registro que diste de alta tú. Pide a otra persona de la liga que lo revise.");
        }
        if (registro.temporadaEstado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada del registro está cerrada: sus registros ya no se validan.");
        }
        if (registro.estado() != EstadoRegistro.PENDIENTE_VALIDACION) {
            throw ReglaNegocioException.conflicto("RF-017", "El registro ya está " + texto(registro.estado())
                    + ": solo se valida un registro pendiente de validación.");
        }
        if (decision == DecisionRegistro.ACTIVO) {
            validarActivacion(registro);
        }

        motivoOperacion.fijar(observaciones != null ? observaciones : MOTIVO_APROBACION);
        if (!registros.validar(registro.id(), decision.estado(), perfil, OffsetDateTime.now(reloj), observaciones)) {
            throw ReglaNegocioException.conflicto("RF-017",
                    "Otra persona validó este registro al mismo tiempo. Actualiza la página para ver su estado.");
        }

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("personaId", registro.personaId().toString());
        detalle.put("equipoId", registro.equipoId().toString());
        detalle.put("temporadaId", registro.temporadaId().toString());
        detalle.put("estadoAnterior", EstadoRegistro.PENDIENTE_VALIDACION.name());
        detalle.put("estado", decision.estado().name());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "VALIDAR_REGISTRO_JUGADOR", ENTIDAD,
                registro.id().toString(), observaciones, detalle));

        return registros.buscar(registro.id())
                .orElseThrow(() -> new IllegalStateException("El registro " + registro.id() + " no es visible tras validarlo"));
    }

    /**
     * Da de baja un registro {@code ACTIVO} con motivo: pasa a {@code BAJA} con {@code baja_en} de hoy
     * en {@code America/Mexico_City} y conserva su historial (RF-014, RN-021). El motivo va solo al
     * historial y a la auditoría; las observaciones y la validación no cambian (DEC-121).
     *
     * <p>Orden de errores (DEC-118 a DEC-122):
     * <ol>
     *   <li>Sin {@code registros.registro:EDITAR} en ningún ámbito: {@code 403 VAL-015}.</li>
     *   <li>Sin motivo o con menos de 10 caracteres (sin contar los espacios de los extremos):
     *       {@code 400 RN-021}; con más de 500: {@code 400 VALIDACION}.</li>
     *   <li>Registro inexistente en la liga o de un club fuera de ámbito: {@code 404} (VAL-024).</li>
     *   <li>Se bloquea el registro (DEC-122).</li>
     *   <li>Temporada {@code CERRADA}: {@code 409 RF-003}.</li>
     *   <li>Registro que no está {@code ACTIVO}: {@code 409 RF-017} con un {@code detail} según su
     *       estado (DEC-118).</li>
     *   <li>Otra petición cambió su estado entre tanto: {@code 409 RF-017}.</li>
     * </ol>
     */
    @Transactional
    public RegistroJugadorDto darDeBaja(UUID registroId, BajaRegistroRequest peticion) {
        autorizacion.alcance(EDITAR);
        String motivo = motivoDeBaja(peticion == null ? null : peticion.motivo());

        RegistroDeDocumentos encontrado = documentos.buscarRegistro(ligaId, registroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro " + registroId));
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deClubes(encontrado.clubId()));
        RegistroDeDocumentos registro = documentos.bloquearRegistro(ligaId, registroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro " + registroId));

        if (registro.temporadaEstado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada del registro está cerrada: sus registros ya no cambian.");
        }
        if (registro.estado() != EstadoRegistro.ACTIVO) {
            throw ReglaNegocioException.conflicto("RF-017", sinBaja(registro.estado()));
        }

        motivoOperacion.fijar(motivo);
        LocalDate bajaEn = LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO));
        if (!registros.darDeBaja(registro.id(), bajaEn)) {
            throw ReglaNegocioException.conflicto("RF-017",
                    "Otra persona cambió el estado de este registro al mismo tiempo. Actualiza la página para ver "
                            + "su estado.");
        }

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("personaId", registro.personaId().toString());
        detalle.put("equipoId", registro.equipoId().toString());
        detalle.put("temporadaId", registro.temporadaId().toString());
        detalle.put("estadoAnterior", EstadoRegistro.ACTIVO.name());
        detalle.put("estado", EstadoRegistro.BAJA.name());
        detalle.put("bajaEn", bajaEn.toString());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "DAR_BAJA_REGISTRO_JUGADOR", ENTIDAD,
                registro.id().toString(), motivo, detalle));

        return registros.buscar(registro.id())
                .orElseThrow(() -> new IllegalStateException("El registro " + registro.id() + " no es visible tras darlo de baja"));
    }

    // ── Consulta (H-10.5, DEC-109, DEC-110) ────────────────────────────────────────────────────

    /**
     * Registros del ámbito del usuario con sus filtros, en una página (RF-017, DEC-109). El ámbito se
     * aplica en el {@code WHERE} y en el {@code COUNT} con la misma condición, así que
     * {@code totalElementos} cuenta solo las filas visibles; un filtro fuera de ámbito devuelve una
     * página vacía. Las filas llevan la CURP enmascarada (DEC-110).
     *
     * <p>Orden de errores: sin {@code registros.registro:CONSULTAR} en ningún ámbito,
     * {@code 403 VAL-015}; estado u orden desconocido, o página o tamaño fuera de rango,
     * {@code 400 VALIDACION}.
     */
    @Transactional(readOnly = true)
    public Pagina<RegistroJugadorResumenDto> listar(FiltroRegistros filtro) {
        AlcanceAutorizacion alcance = autorizacion.alcance(CONSULTAR);
        filtro.validar();
        return Pagina.de(registros.listar(ligaId, filtro, alcance), filtro.pagina(), filtro.tamano(),
                registros.contar(ligaId, filtro, alcance));
    }

    /**
     * Detalle de un registro con {@code esMenor}, los tutores vigentes si es menor y la CURP
     * (DEC-109, DEC-110). La CURP va completa solo si la sesión tiene
     * {@code organizacion.persona:CONSULTAR} sobre el club del registro; si no, {@code curp} es
     * {@code null} y solo va {@code curpEnmascarada}.
     *
     * <p>Orden de errores, como los requisitos ({@link DocumentoRegistroService#requisitos}):
     * <ol>
     *   <li>Sin {@code registros.registro:CONSULTAR} en ningún ámbito: {@code 403 VAL-015}.</li>
     *   <li>Registro inexistente en la liga: {@code 404}.</li>
     *   <li>Registro fuera del ámbito (ni su club ni su persona): {@code 404} igual que uno inexistente
     *       (VAL-024).</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public RegistroJugadorDetalleDto consultar(UUID registroId) {
        autorizacion.alcance(CONSULTAR);
        RegistroDeDocumentos registro = documentos.buscarRegistro(ligaId, registroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro " + registroId));
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deClubesYPersonas(List.of(registro.clubId()),
                List.of(registro.personaId())));

        RegistroJugadorDetalleDto detalle = registros.buscarDetalle(registro.id())
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro " + registroId));
        boolean mostrarCurp = autorizacion.permite(CONSULTAR_PERSONA, ObjetivoAutorizacion.deClubes(registro.clubId()));
        List<TutorDto> tutores = detalle.esMenor() ? registros.tutoresVigentes(registro.personaId()) : List.of();
        return detalle.con(mostrarCurp, tutores);
    }

    /**
     * Historial de estados del registro del más antiguo al más reciente: el alta y cada cambio de
     * estado con su motivo, quién lo hizo y cuándo (RF-018). Es de solo lectura (RNF-015): el
     * historial lo escribe {@code tg_registros_historial}.
     *
     * <p>Ámbito: {@code registros.registro:CONSULTAR} sobre el club del registro
     * ({@code ObjetivoAutorizacion.deClubes}); una cuenta {@code LIGA} ve todos. La consulta propia
     * del jugador o su tutor ({@code PROPIO}) no se incluye aquí: es de H-24.
     *
     * <p>Orden de errores:
     * <ol>
     *   <li>Sin {@code registros.registro:CONSULTAR} en ningún ámbito: {@code 403 VAL-015}.</li>
     *   <li>Registro inexistente en la liga, o de un club fuera del ámbito: {@code 404}, sin
     *       distinguir los dos casos (VAL-024).</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public List<CambioEstadoRegistroDto> historial(UUID registroId) {
        autorizacion.alcance(CONSULTAR);
        RegistroDeDocumentos registro = documentos.buscarRegistro(ligaId, registroId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Registro " + registroId));
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deClubes(registro.clubId()));
        return registros.historial(registro.id());
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

    /** RF-013, VAL-001 y VAL-025, en ese orden, solo al activar (DEC-99, DEC-100). */
    private void validarActivacion(RegistroDeDocumentos registro) {
        if (!registro.equipoActivo()) {
            throw ReglaNegocioException.noProcesable("RF-013",
                    "El equipo del registro está dado de baja: el registro no se puede activar, solo rechazar.");
        }
        boolean esMenor = documentos.esMenor(registro.personaId(), registro.altaEn())
                .orElseThrow(() -> new IllegalStateException("La persona del registro " + registro.id() + " no existe"));
        Avance avance = RequisitosDocumentos.calcular(documentos.requisitos(ligaId, registro.categoriaTipo()), esMenor,
                documentos.vigentesConRenovaciones(registro.id()));
        if (!avance.listoParaValidar()) {
            throw faltanDocumentos(avance);
        }
        if (esMenor) {
            validarRequisitosMenor(registros.requisitosMenor(registro.personaId()),
                    "El club debe registrar a su tutor antes de activar el registro.",
                    "El club debe registrar el consentimiento del tutor antes de activar el registro.");
        }
    }

    /**
     * {@code 422 VAL-001} con los requisitos aplicables sin aprobar, en su orden: el {@code detail} los
     * nombra sin códigos y {@code faltantes} lleva el avance de cada uno (DEC-99).
     */
    private static ReglaNegocioException faltanDocumentos(Avance avance) {
        List<DocumentoFaltante> faltantes = avance.requisitos().stream()
                .filter(requisito -> requisito.aprobados() < requisito.cantidad())
                .map(DocumentoFaltante::de)
                .toList();
        String nombres = faltantes.stream()
                .map(faltante -> "«" + faltante.tipoDocumento().nombre() + "»")
                .collect(Collectors.joining(", "));
        return ReglaNegocioException.noProcesable("VAL-001",
                        "El registro no se puede activar: faltan documentos aprobados de " + nombres
                                + ". Aprueba los documentos pendientes o pide al club que cargue los que faltan.")
                .con("faltantes", faltantes);
    }

    /**
     * La misma condición que {@code tg_registros_validar} al activar (VAL-025, DEC-89), en el alta y en
     * la validación; solo cambia qué hacer después.
     */
    private static void validarRequisitosMenor(RequisitosMenor requisitos, String siFaltaTutor,
            String siFaltaConsentimiento) {
        if (!requisitos.tieneTutorVigente()) {
            throw ReglaNegocioException.noProcesable("VAL-025",
                            "La persona es menor de edad y no tiene un tutor registrado. " + siFaltaTutor)
                    .con("requisito", "TUTOR");
        }
        if (!requisitos.tieneConsentimiento()) {
            throw ReglaNegocioException.noProcesable("VAL-025",
                            "La persona es menor de edad y falta el consentimiento de su tutor. "
                                    + siFaltaConsentimiento)
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

    /** Recorta los espacios; en blanco es {@code null}; más de 500 caracteres es {@code 400 VALIDACION}. */
    private static String observacionesOpcionales(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String valor = texto.strip();
        if (valor.length() > MAXIMO_OBSERVACIONES) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Las observaciones admiten hasta " + MAXIMO_OBSERVACIONES + " caracteres.");
        }
        return valor;
    }

    /**
     * El motivo de la baja recortado; falta o con menos de 10 caracteres es {@code 400 RN-021} y con más
     * de 500, {@code 400 VALIDACION} (DEC-121).
     */
    private static String motivoDeBaja(String texto) {
        String valor = texto == null ? "" : texto.strip();
        if (valor.length() < MINIMO_OBSERVACIONES) {
            throw ReglaNegocioException.invalida("RN-021", "Escribe un motivo de al menos " + MINIMO_OBSERVACIONES
                    + " caracteres para dar de baja el registro.");
        }
        if (valor.length() > MAXIMO_OBSERVACIONES) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "El motivo admite hasta " + MAXIMO_OBSERVACIONES + " caracteres.");
        }
        return valor;
    }

    /** El {@code detail} de {@code 409 RF-017} cuando el registro no está {@code ACTIVO} (DEC-118). */
    private static String sinBaja(EstadoRegistro estado) {
        return switch (estado) {
            case PENDIENTE_VALIDACION ->
                    "El registro aún no está validado: si no procede, pide a la liga que lo rechace.";
            case SUSPENDIDO ->
                    "El registro tiene una sanción vigente: no se puede dar de baja mientras esté suspendido.";
            case BAJA -> "El registro ya está dado de baja.";
            case RECHAZADO -> "El registro fue rechazado: no está activo.";
            case ACTIVO -> throw new IllegalArgumentException("Un registro ACTIVO sí admite baja");
        };
    }

    private static String texto(EstadoRegistro estado) {
        return estado.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /**
     * Requisito aplicable sin aprobar, en la extensión {@code faltantes} de {@code 422 VAL-001}
     * (DEC-99): {@code cargados} son los vigentes no rechazados y {@code aprobados} los vigentes
     * {@code APROBADO}.
     */
    public record DocumentoFaltante(TipoDocumentoDto tipoDocumento, int cantidad, int cargados, int aprobados) {

        static DocumentoFaltante de(RequisitoDto requisito) {
            return new DocumentoFaltante(requisito.tipoDocumento(), requisito.cantidad(), requisito.cargados(),
                    requisito.aprobados());
        }
    }
}
