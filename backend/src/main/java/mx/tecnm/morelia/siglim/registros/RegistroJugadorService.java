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
 * CU-004) y su validación por la liga (RF-017, RF-018, RF-109, VAL-001).
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
 * </ul>
 *
 * <p>Refs: H-09.2, H-10.2, H-10.5, RF-003, RF-013, RF-016, RF-017, RF-018, RF-093, RF-101, RF-107, RF-109,
 * RN-021, RN-037, RN-039, RN-055, RNF-012, VAL-001, VAL-015, VAL-017, VAL-022, VAL-024, VAL-025, VAL-026, CU-004,
 * DEC-78, DEC-85 a DEC-90, DEC-97 a DEC-102, DEC-109, DEC-110
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
                documentos.vigentes(registro.id()));
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
