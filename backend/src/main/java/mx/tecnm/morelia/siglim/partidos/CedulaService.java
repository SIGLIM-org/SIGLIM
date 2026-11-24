package mx.tecnm.morelia.siglim.partidos;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.CedulaBloqueada;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.DatosEnvio;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.EquipoAlineacion;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.MarcadorDefault;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.PartidoCedula;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.RegistroAlineable;
import mx.tecnm.morelia.siglim.partidos.GuardarAlineacionesRequest.JugadorRequest;
import mx.tecnm.morelia.siglim.partidos.RegistrarIncidenciaRequest.InvolucradoRequest;
import mx.tecnm.morelia.siglim.partidos.eventos.CedulaEnviada;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captura de la cédula del partido: abrirla, la alineación con elegibilidad y el marcador (M-08.2,
 * H-16.1); los eventos, las incidencias, las observaciones y el envío a validación (H-16.2).
 *
 * <ul>
 *   <li><b>Permiso.</b> Consultar exige {@code partidos.cedula:CONSULTAR} y capturar,
 *       {@code partidos.cedula:CAPTURAR}, los dos con ámbito {@code LIGA}: la cédula es de la liga y
 *       en la matriz inicial los tiene el Capturista. La vista del árbitro designado ({@code PROPIO})
 *       llega con su bandeja (H-17) <b>[PROPUESTA]</b> (DEC-164).</li>
 *   <li><b>Abrir (RF-025, RF-046).</b> Solo con el partido {@code EN_CURSO}; un partido tiene una sola
 *       cédula ({@code 409 DUPLICADO}). Quien la abre queda como capturista y nunca podrá aprobarla
 *       (VAL-021).</li>
 *   <li><b>Contenido solo en captura (RF-122).</b> La alineación y el marcador se guardan con la
 *       cédula {@code EN_CAPTURA}; en otro estado, {@code 409 RF-122}.</li>
 *   <li><b>Alineación (RF-019, RF-020, RF-110, VAL-002, VAL-018).</b> {@code PUT} reemplaza la
 *       alineación completa. Se valida cada jugador y, si alguno no puede jugar, nada se guarda y se
 *       responde {@code 422 VAL-002} con la lista {@code errores}: un elemento por jugador con su
 *       {@code codigo} ({@code RF-019}, {@code RF-020}, {@code VAL-018}, {@code RF-025} o
 *       {@code REFERENCIA_INVALIDA}) y un mensaje con su nombre (DEC-165).</li>
 *   <li><b>Marcador (RF-047, RF-126, DEC-24, DEC-28).</b> Con un equipo no presentado el sistema asigna
 *       el marcador por default de la versión vigente de los parámetros (3-0 <b>[DEMO]</b>, DEC-72) y
 *       la petición no lleva goles; si no, los dos goles son obligatorios y los penales solo se
 *       registran con empate (DEC-166).</li>
 *   <li><b>Eventos e incidencias (RF-025, RF-026, H-16.2).</b> Un evento es de un jugador alineado en
 *       la cédula y toma su equipo; la sustitución lleva al que entra, del mismo equipo. Una incidencia
 *       lleva un tipo activo del catálogo y al menos un involucrado: un jugador alineado o uno de los dos
 *       equipos <b>[PROPUESTA]</b> (DEC-171). Los eventos se quitan con {@code DELETE} mientras la
 *       cédula está en captura.</li>
 *   <li><b>Envío (RF-122, VAL-020, H-16.2).</b> Exige el marcador y la alineación de cada equipo que se
 *       presentó; si falta algo responde {@code 422 VAL-020} con la lista {@code faltantes}. Después ya
 *       nada se edita ({@code 409 RF-122}) hasta que el árbitro la devuelva (DEC-170).</li>
 *   <li><b>Auditoría (RF-093).</b> {@code ABRIR_CEDULA}, {@code CAPTURAR_ALINEACION},
 *       {@code CAPTURAR_MARCADOR}, {@code REGISTRAR_EVENTO_CEDULA}, {@code ELIMINAR_EVENTO_CEDULA},
 *       {@code REGISTRAR_INCIDENCIA_CEDULA}, {@code CAPTURAR_OBSERVACIONES} y {@code ENVIAR_CEDULA} en
 *       M-08, sin nombres de jugadores ni textos libres.</li>
 * </ul>
 *
 * <p>Refs: H-16.1, H-16.2, RF-019, RF-020, RF-025, RF-026, RF-046, RF-047, RF-110, RF-122, RF-126,
 * VAL-002, VAL-018, VAL-020, DEC-12, DEC-19, DEC-24, DEC-28, DEC-164, DEC-165, DEC-166, DEC-170, DEC-171
 */
@Service
public class CedulaService {

    static final String CONSULTAR = "partidos.cedula:CONSULTAR";
    static final String CAPTURAR = "partidos.cedula:CAPTURAR";
    private static final String MODULO = "M-08";
    private static final String ENTIDAD = "cedulas";
    private static final int GOLES_MAXIMOS = 99;
    private static final int PLAYERA_MAXIMA = 99;
    private static final int JUGADORES_MAXIMOS = 100;
    /** Límite del {@code ck_eventos_minuto}: tiempo extra y penales caben en 150 minutos. */
    private static final int MINUTO_MAXIMO = 150;
    /** [PROPUESTA] Ni el ERS ni la BD fijan los largos; evitan textos desmedidos en la cédula (DEC-171). */
    private static final int MAXIMO_OBSERVACIONES_EVENTO = 500;
    private static final int MAXIMO_DESCRIPCION = 1000;
    private static final int MAXIMO_OBSERVACIONES = 2000;
    private static final int INVOLUCRADOS_MAXIMOS = 30;

    private final CedulaRepository cedulas;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final UUID ligaId;

    CedulaService(CedulaRepository cedulas, Autorizacion autorizacion, AuditoriaService auditoria,
            ApplicationEventPublisher eventos, SiglimPropiedades propiedades) {
        this.cedulas = cedulas;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Cédula del partido. {@code 404} si el partido no es de la liga o si todavía no tiene cédula.
     */
    @Transactional(readOnly = true)
    public CedulaDto consultar(UUID partidoId) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        if (!cedulas.existePartido(ligaId, partidoId)) {
            throw new RecursoNoEncontradoException("Partido " + partidoId);
        }
        UUID cedulaId = cedulas.cedulaDePartido(partidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cédula del partido " + partidoId));
        return cedulas.buscar(cedulaId).orElseThrow();
    }

    /**
     * Abre la cédula del partido {@code EN_CAPTURA} con la sesión como capturista.
     *
     * <p>Orden: permiso ({@code 403 VAL-015} o {@code 404}) → el partido es de la liga ({@code 404}) →
     * está en curso ({@code 409 RF-046}) → no tiene cédula ({@code 409 DUPLICADO}).
     */
    @Transactional
    public CedulaDto abrir(UUID partidoId) {
        autorizacion.exigir(CAPTURAR, ObjetivoAutorizacion.deLiga());
        PartidoCedula partido = cedulas.bloquearPartido(ligaId, partidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Partido " + partidoId));
        if (partido.estado() != EstadoPartido.EN_CURSO) {
            throw ReglaNegocioException.conflicto("RF-046",
                    "La cédula se abre cuando el partido está en curso; este partido está "
                            + estadoLegible(partido.estado()) + ".");
        }
        if (cedulas.cedulaDePartido(partidoId).isPresent()) {
            throw duplicada();
        }
        UUID cedulaId = cedulas.insertar(partidoId, UsuarioActual.idRequerido()).orElseThrow(CedulaService::duplicada);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("partidoId", partidoId.toString());
        auditar("ABRIR_CEDULA", cedulaId, detalle);
        return cedulas.buscar(cedulaId).orElseThrow();
    }

    /**
     * Reemplaza la alineación de los dos equipos.
     *
     * <p>Orden: permiso → la cédula es de la liga ({@code 404}) → está en captura ({@code 409 RF-122})
     * → forma de la petición ({@code 400 VALIDACION} con {@code errores}) → elegibilidad de cada jugador
     * ({@code 422 VAL-002} con {@code errores}) → un jugador que se quita no tiene eventos
     * ({@code 409 RF-025}).
     */
    @Transactional
    public CedulaDto guardarAlineaciones(UUID cedulaId, GuardarAlineacionesRequest peticion) {
        autorizacion.exigir(CAPTURAR, ObjetivoAutorizacion.deLiga());
        CedulaBloqueada cedula = bloquearEnCaptura(cedulaId, "La alineación");
        List<JugadorRequest> jugadores = validarForma(peticion);

        Set<UUID> registroIds = new HashSet<>();
        Set<UUID> equipoIds = new HashSet<>();
        jugadores.forEach(jugador -> {
            registroIds.add(jugador.registroJugadorId());
            equipoIds.add(jugador.equipoId());
        });
        Map<UUID, RegistroAlineable> registros = cedulas.registros(ligaId, registroIds);
        registros.values().forEach(registro -> equipoIds.add(registro.equipoId()));
        Map<UUID, EquipoAlineacion> equipos = cedulas.equipos(equipoIds);

        List<Map<String, Object>> errores = new ArrayList<>();
        for (int i = 0; i < jugadores.size(); i++) {
            JugadorRequest jugador = jugadores.get(i);
            Causa causa = causaDeInelegibilidad(cedula, jugador, registros.get(jugador.registroJugadorId()),
                    equipos);
            if (causa != null) {
                errores.add(errorDeJugador(i, jugador.registroJugadorId(), causa.codigo(), causa.mensaje()));
            }
        }
        if (!errores.isEmpty()) {
            throw ReglaNegocioException.noProcesable("VAL-002", errores.size() == 1
                            ? "Un jugador no puede alinearse: " + errores.getFirst().get("mensaje")
                            : errores.size() + " jugadores no pueden alinearse. Revisa la causa de cada uno.")
                    .con("errores", errores);
        }

        Map<UUID, UUID> actual = cedulas.alineacionActual(cedulaId);
        Set<UUID> quitados = new HashSet<>();
        actual.forEach((registro, fila) -> {
            if (!registroIds.contains(registro)) {
                quitados.add(fila);
            }
        });
        if (!cedulas.conEventos(quitados).isEmpty()) {
            throw ReglaNegocioException.noProcesable("RF-025",
                    "Un jugador que quitas de la alineación ya tiene goles, tarjetas o sustituciones en la "
                            + "cédula: borra primero esos eventos.");
        }
        cedulas.eliminarAlineaciones(quitados);
        int local = 0;
        int visitante = 0;
        for (JugadorRequest jugador : jugadores) {
            boolean titular = jugador.titular() == null || jugador.titular();
            UUID fila = actual.get(jugador.registroJugadorId());
            if (fila == null) {
                cedulas.insertarAlineacion(cedulaId, jugador.registroJugadorId(), jugador.equipoId(),
                        jugador.numeroPlayera(), titular);
            } else {
                cedulas.actualizarAlineacion(fila, jugador.equipoId(), jugador.numeroPlayera(), titular);
            }
            if (jugador.equipoId().equals(cedula.equipoLocalId())) {
                local++;
            } else {
                visitante++;
            }
        }
        cedulas.tocar(cedulaId);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("partidoId", cedula.partidoId().toString());
        detalle.put("jugadoresLocal", local);
        detalle.put("jugadoresVisitante", visitante);
        auditar("CAPTURAR_ALINEACION", cedulaId, detalle);
        return cedulas.buscar(cedulaId).orElseThrow();
    }

    /**
     * Guarda el marcador o la no presentación de un equipo.
     *
     * <p>Orden: permiso → la cédula es de la liga ({@code 404}) → está en captura ({@code 409 RF-122})
     * → datos ({@code 400 VALIDACION}) → el equipo no presentado juega el partido
     * ({@code 422 RF-126}) → la temporada tiene parámetros ({@code 409 RF-100}) → penales solo con
     * empate ({@code 422 RF-047}).
     */
    @Transactional
    public CedulaDto guardarMarcador(UUID cedulaId, GuardarMarcadorRequest peticion) {
        autorizacion.exigir(CAPTURAR, ObjetivoAutorizacion.deLiga());
        CedulaBloqueada cedula = bloquearEnCaptura(cedulaId, "El marcador");
        if (peticion == null) {
            throw invalida("Indica el marcador o el equipo que no se presentó.");
        }
        Integer golesLocal;
        Integer golesVisitante;
        Integer penalesLocal = null;
        Integer penalesVisitante = null;
        UUID noPresentado = peticion.noPresentadoEquipoId();
        boolean porDefault = noPresentado != null;
        if (porDefault) {
            if (peticion.golesLocal() != null || peticion.golesVisitante() != null
                    || peticion.penalesLocal() != null || peticion.penalesVisitante() != null) {
                throw invalida("Con un equipo no presentado el marcador lo asigna la Liga por default: "
                        + "no envíes goles ni penales.");
            }
            if (!cedula.juega(noPresentado)) {
                throw ReglaNegocioException.noProcesable("RF-126",
                        "El equipo no presentado debe ser uno de los dos equipos del partido.");
            }
            MarcadorDefault marcador = cedulas.marcadorDefault(cedula.temporadaId())
                    .orElseThrow(() -> ReglaNegocioException.conflicto("RF-100",
                            "La temporada no tiene parámetros: no hay un marcador por default que asignar."));
            boolean localNoSePresento = noPresentado.equals(cedula.equipoLocalId());
            golesLocal = localNoSePresento ? marcador.golesPerdedor() : marcador.golesGanador();
            golesVisitante = localNoSePresento ? marcador.golesGanador() : marcador.golesPerdedor();
        } else {
            golesLocal = goles(peticion.golesLocal(), "golesLocal");
            golesVisitante = goles(peticion.golesVisitante(), "golesVisitante");
            if ((peticion.penalesLocal() == null) != (peticion.penalesVisitante() == null)) {
                throw invalida("Registra los penales de los dos equipos o de ninguno.");
            }
            if (peticion.penalesLocal() != null) {
                penalesLocal = goles(peticion.penalesLocal(), "penalesLocal");
                penalesVisitante = goles(peticion.penalesVisitante(), "penalesVisitante");
                if (!golesLocal.equals(golesVisitante)) {
                    throw ReglaNegocioException.noProcesable("RF-047",
                            "Los penales solo se registran cuando el partido terminó empatado.");
                }
            }
        }
        cedulas.guardarMarcador(cedulaId, golesLocal, golesVisitante, penalesLocal, penalesVisitante, noPresentado);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("partidoId", cedula.partidoId().toString());
        detalle.put("golesLocal", golesLocal);
        detalle.put("golesVisitante", golesVisitante);
        if (penalesLocal != null) {
            detalle.put("penalesLocal", penalesLocal);
            detalle.put("penalesVisitante", penalesVisitante);
        }
        if (porDefault) {
            detalle.put("noPresentadoEquipoId", noPresentado.toString());
        }
        detalle.put("porDefault", porDefault);
        auditar("CAPTURAR_MARCADOR", cedulaId, detalle);
        return cedulas.buscar(cedulaId).orElseThrow();
    }

    /**
     * Registra un gol, autogol, tarjeta o sustitución (RF-025). El equipo es el de la alineación del
     * jugador.
     *
     * <p>Orden: permiso → la cédula es de la liga ({@code 404}) → está en captura ({@code 409 RF-122})
     * → datos ({@code 400 VALIDACION}) → el jugador (y el que entra) están alineados en esta cédula
     * ({@code 422 REFERENCIA_INVALIDA}) → en una sustitución, los dos son del mismo equipo
     * ({@code 422 RF-025}).
     */
    @Transactional
    public CedulaDto registrarEvento(UUID cedulaId, RegistrarEventoRequest peticion) {
        autorizacion.exigir(CAPTURAR, ObjetivoAutorizacion.deLiga());
        CedulaBloqueada cedula = bloquearEnCaptura(cedulaId, "Los eventos");
        if (peticion == null || peticion.tipo() == null) {
            throw invalida("Indica el tipo de evento: gol, autogol, tarjeta o sustitución.");
        }
        if (peticion.alineacionId() == null) {
            throw invalida("Indica el jugador del evento.");
        }
        boolean sustitucion = peticion.tipo() == TipoEventoCedula.SUSTITUCION;
        if (sustitucion && peticion.alineacionEntraId() == null) {
            throw invalida("Indica el jugador que entra en la sustitución.");
        }
        if (!sustitucion && peticion.alineacionEntraId() != null) {
            throw invalida("El jugador que entra solo se indica en una sustitución.");
        }
        if (sustitucion && peticion.alineacionEntraId().equals(peticion.alineacionId())) {
            throw invalida("En una sustitución entra un jugador distinto del que sale.");
        }
        Integer minuto = minuto(peticion.minuto());
        String observaciones = textoOpcional(peticion.observaciones(), MAXIMO_OBSERVACIONES_EVENTO, "observaciones");
        UUID equipoId = cedulas.equipoDeAlineacion(cedulaId, peticion.alineacionId())
                .orElseThrow(() -> noAlineado("El jugador del evento"));
        if (sustitucion) {
            UUID equipoEntra = cedulas.equipoDeAlineacion(cedulaId, peticion.alineacionEntraId())
                    .orElseThrow(() -> noAlineado("El jugador que entra"));
            if (!equipoEntra.equals(equipoId)) {
                throw ReglaNegocioException.noProcesable("RF-025",
                        "En una sustitución, el jugador que entra y el que sale son del mismo equipo.");
            }
        }
        cedulas.insertarEvento(cedulaId, peticion.tipo(), minuto, equipoId, peticion.alineacionId(),
                peticion.alineacionEntraId(), observaciones);
        cedulas.tocar(cedulaId);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("partidoId", cedula.partidoId().toString());
        detalle.put("tipo", peticion.tipo().name());
        detalle.put("minuto", minuto);
        detalle.put("equipoId", equipoId.toString());
        auditar("REGISTRAR_EVENTO_CEDULA", cedulaId, detalle);
        return cedulas.buscar(cedulaId).orElseThrow();
    }

    /**
     * Quita un evento capturado por error. Orden: permiso → la cédula es de la liga ({@code 404}) → está
     * en captura ({@code 409 RF-122}) → el evento es de esta cédula ({@code 404}).
     */
    @Transactional
    public void eliminarEvento(UUID cedulaId, UUID eventoId) {
        autorizacion.exigir(CAPTURAR, ObjetivoAutorizacion.deLiga());
        CedulaBloqueada cedula = bloquearEnCaptura(cedulaId, "Los eventos");
        if (cedulas.eliminarEvento(cedulaId, eventoId) == 0) {
            throw new RecursoNoEncontradoException("Evento " + eventoId);
        }
        cedulas.tocar(cedulaId);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("partidoId", cedula.partidoId().toString());
        detalle.put("eventoId", eventoId.toString());
        auditar("ELIMINAR_EVENTO_CEDULA", cedulaId, detalle);
    }

    /**
     * Registra una incidencia con su tipo y sus involucrados (RF-026).
     *
     * <p>Orden: permiso → la cédula es de la liga ({@code 404}) → está en captura ({@code 409 RF-122})
     * → datos ({@code 400 VALIDACION} con {@code errores} por involucrado) → el tipo es activo de la liga
     * ({@code 422 REFERENCIA_INVALIDA}) → cada involucrado es un jugador alineado en la cédula o uno de
     * los dos equipos ({@code 422 RF-026}) <b>[PROPUESTA]</b> (DEC-171).
     */
    @Transactional
    public CedulaDto registrarIncidencia(UUID cedulaId, RegistrarIncidenciaRequest peticion) {
        autorizacion.exigir(CAPTURAR, ObjetivoAutorizacion.deLiga());
        CedulaBloqueada cedula = bloquearEnCaptura(cedulaId, "Las incidencias");
        if (peticion == null || peticion.tipoIncidenciaId() == null) {
            throw invalida("Indica el tipo de incidencia.");
        }
        Integer minuto = minuto(peticion.minuto());
        String descripcion = textoOpcional(peticion.descripcion(), MAXIMO_DESCRIPCION, "descripcion");
        if (descripcion == null) {
            throw invalida("Describe lo que pasó en la incidencia.").con("errores",
                    List.of(errorDeCampo("descripcion", "Es obligatoria.")));
        }
        List<InvolucradoRequest> involucrados = validarInvolucrados(peticion.involucrados());
        var tipo = cedulas.tipoIncidenciaActivo(ligaId, peticion.tipoIncidenciaId())
                .orElseThrow(() -> ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                        "El tipo de incidencia no existe en el catálogo de la liga o está inactivo."));
        Set<UUID> alineados = cedulas.personasAlineadas(cedulaId);
        for (InvolucradoRequest involucrado : involucrados) {
            if (involucrado.equipoId() != null && !cedula.juega(involucrado.equipoId())) {
                throw ReglaNegocioException.noProcesable("RF-026",
                        "El equipo involucrado debe ser uno de los dos equipos del partido.");
            }
            if (involucrado.personaId() != null && !alineados.contains(involucrado.personaId())) {
                throw ReglaNegocioException.noProcesable("RF-026",
                        "La persona involucrada debe ser un jugador alineado en esta cédula.");
            }
        }
        UUID incidenciaId = cedulas.insertarIncidencia(cedulaId, tipo.id(), minuto, descripcion);
        involucrados.forEach(involucrado ->
                cedulas.insertarInvolucrado(incidenciaId, involucrado.personaId(), involucrado.equipoId()));
        cedulas.tocar(cedulaId);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("partidoId", cedula.partidoId().toString());
        detalle.put("incidenciaId", incidenciaId.toString());
        detalle.put("tipoIncidenciaId", tipo.id());
        detalle.put("involucrados", involucrados.size());
        auditar("REGISTRAR_INCIDENCIA_CEDULA", cedulaId, detalle);
        return cedulas.buscar(cedulaId).orElseThrow();
    }

    /**
     * Reemplaza las observaciones del partido (RF-025). Orden: permiso → {@code 404} →
     * {@code 409 RF-122} → hasta 2000 caracteres ({@code 400 VALIDACION}).
     */
    @Transactional
    public CedulaDto guardarObservaciones(UUID cedulaId, GuardarObservacionesRequest peticion) {
        autorizacion.exigir(CAPTURAR, ObjetivoAutorizacion.deLiga());
        CedulaBloqueada cedula = bloquearEnCaptura(cedulaId, "Las observaciones");
        String observaciones = textoOpcional(peticion == null ? null : peticion.observaciones(),
                MAXIMO_OBSERVACIONES, "observaciones");
        cedulas.guardarObservaciones(cedulaId, observaciones);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("partidoId", cedula.partidoId().toString());
        detalle.put("conObservaciones", observaciones != null);
        auditar("CAPTURAR_OBSERVACIONES", cedulaId, detalle);
        return cedulas.buscar(cedulaId).orElseThrow();
    }

    /**
     * Envía la cédula a validación: {@code EN_CAPTURA → PENDIENTE_VALIDACION} (RF-122).
     *
     * <p>Orden: permiso → la cédula es de la liga ({@code 404}) → está en captura ({@code 409 RF-122})
     * → está completa ({@code 422 VAL-020} con la extensión {@code faltantes}: el marcador y la alineación
     * de cada equipo que se presentó, DEC-170). El trigger fija el plazo del árbitro (RF-124) y repite
     * VAL-020. Publica {@link CedulaEnviada} para el aviso al árbitro designado (H-25).
     */
    @Transactional
    public CedulaDto enviar(UUID cedulaId) {
        autorizacion.exigir(CAPTURAR, ObjetivoAutorizacion.deLiga());
        CedulaBloqueada cedula = bloquearEnCaptura(cedulaId, "El envío");
        DatosEnvio datos = cedulas.datosEnvio(cedulaId);
        List<Map<String, Object>> faltantes = new ArrayList<>();
        if (!datos.conMarcador()) {
            faltantes.add(errorDeCampo("marcador", "Captura el marcador o marca al equipo que no se presentó."));
        }
        if (datos.jugadoresLocal() == 0 && !cedula.equipoLocalId().equals(datos.noPresentadoEquipoId())) {
            faltantes.add(errorDeCampo("alineacionLocal", "Captura la alineación de «" + datos.localNombre() + "»."));
        }
        if (datos.jugadoresVisitante() == 0 && !cedula.equipoVisitanteId().equals(datos.noPresentadoEquipoId())) {
            faltantes.add(errorDeCampo("alineacionVisitante",
                    "Captura la alineación de «" + datos.visitanteNombre() + "»."));
        }
        if (!faltantes.isEmpty()) {
            throw ReglaNegocioException.noProcesable("VAL-020", "La cédula está incompleta y no se puede enviar: "
                            + String.join(" ", faltantes.stream().map(falta -> (String) falta.get("mensaje")).toList()))
                    .con("faltantes", faltantes);
        }
        OffsetDateTime plazo = cedulas.enviar(cedulaId);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("partidoId", cedula.partidoId().toString());
        detalle.put("plazoVenceEn", plazo.toString());
        auditar("ENVIAR_CEDULA", cedulaId, detalle);
        eventos.publishEvent(new CedulaEnviada(ligaId, cedulaId, cedula.partidoId(), plazo));
        return cedulas.buscar(cedulaId).orElseThrow();
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    /** Forma de los involucrados: al menos uno, persona o equipo (no ambos) y sin repetir. */
    private static List<InvolucradoRequest> validarInvolucrados(List<InvolucradoRequest> involucrados) {
        if (involucrados == null || involucrados.isEmpty()) {
            throw invalida("Indica al menos una persona o equipo involucrado.");
        }
        if (involucrados.size() > INVOLUCRADOS_MAXIMOS) {
            throw invalida("Una incidencia admite hasta " + INVOLUCRADOS_MAXIMOS + " involucrados.");
        }
        List<Map<String, Object>> errores = new ArrayList<>();
        Set<UUID> vistos = new HashSet<>();
        for (int i = 0; i < involucrados.size(); i++) {
            InvolucradoRequest involucrado = involucrados.get(i);
            String campo = "involucrados[" + i + "]";
            if (involucrado == null || (involucrado.personaId() == null) == (involucrado.equipoId() == null)) {
                errores.add(errorDeCampo(campo, "Indica una persona o un equipo, no ambos."));
            } else if (!vistos.add(involucrado.personaId() != null ? involucrado.personaId() : involucrado.equipoId())) {
                errores.add(errorDeCampo(campo, "El involucrado está repetido."));
            }
        }
        if (!errores.isEmpty()) {
            throw invalida("Revisa los involucrados de la incidencia.").con("errores", errores);
        }
        return involucrados;
    }

    private static Integer minuto(Integer minuto) {
        if (minuto != null && (minuto < 0 || minuto > MINUTO_MAXIMO)) {
            throw invalida("El minuto va de 0 a " + MINUTO_MAXIMO + ".").con("errores",
                    List.of(errorDeCampo("minuto", "Fuera de rango.")));
        }
        return minuto;
    }

    /** Texto recortado; vacío cuenta como {@code null}. */
    private static String textoOpcional(String texto, int maximo, String campo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.strip();
        if (limpio.length() > maximo) {
            throw invalida("El texto admite hasta " + maximo + " caracteres.").con("errores",
                    List.of(errorDeCampo(campo, "Demasiado largo.")));
        }
        return limpio;
    }

    private static ReglaNegocioException noAlineado(String quien) {
        return ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                quien + " no está en la alineación de esta cédula.");
    }

    private CedulaBloqueada bloquearEnCaptura(UUID cedulaId, String contenido) {
        CedulaBloqueada cedula = cedulas.bloquear(ligaId, cedulaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cédula " + cedulaId));
        if (cedula.estado() != EstadoCedula.EN_CAPTURA) {
            throw ReglaNegocioException.conflicto("RF-122", contenido
                    + " solo se modifica mientras la cédula está en captura; ahora está "
                    + estadoLegible(cedula.estado()) + ".");
        }
        return cedula;
    }

    /** Forma de la petición: lista presente, identificadores, playera y sin jugadores repetidos. */
    private static List<JugadorRequest> validarForma(GuardarAlineacionesRequest peticion) {
        if (peticion == null || peticion.jugadores() == null) {
            throw invalida("Indica la lista de jugadores de la alineación.");
        }
        List<JugadorRequest> jugadores = peticion.jugadores();
        if (jugadores.size() > JUGADORES_MAXIMOS) {
            throw invalida("La alineación admite hasta " + JUGADORES_MAXIMOS + " jugadores.");
        }
        List<Map<String, Object>> errores = new ArrayList<>();
        Set<UUID> vistos = new HashSet<>();
        for (int i = 0; i < jugadores.size(); i++) {
            JugadorRequest jugador = jugadores.get(i);
            String campo = "jugadores[" + i + "]";
            if (jugador == null) {
                errores.add(errorDeCampo(campo, "Falta el jugador."));
                continue;
            }
            if (jugador.registroJugadorId() == null) {
                errores.add(errorDeCampo(campo + ".registroJugadorId", "Indica el registro del jugador."));
            } else if (!vistos.add(jugador.registroJugadorId())) {
                errores.add(errorDeCampo(campo + ".registroJugadorId", "El jugador está repetido en la alineación."));
            }
            if (jugador.equipoId() == null) {
                errores.add(errorDeCampo(campo + ".equipoId", "Indica el equipo con el que juega."));
            }
            if (jugador.numeroPlayera() != null
                    && (jugador.numeroPlayera() < 0 || jugador.numeroPlayera() > PLAYERA_MAXIMA)) {
                errores.add(errorDeCampo(campo + ".numeroPlayera",
                        "El número de playera va de 0 a " + PLAYERA_MAXIMA + "."));
            }
        }
        if (!errores.isEmpty()) {
            throw invalida("Revisa los datos de la alineación.").con("errores", errores);
        }
        return jugadores;
    }

    /** Causa por la que el jugador no puede alinearse, o {@code null} si es elegible. */
    private static Causa causaDeInelegibilidad(CedulaBloqueada cedula, JugadorRequest jugador,
            RegistroAlineable registro, Map<UUID, EquipoAlineacion> equipos) {
        if (!cedula.juega(jugador.equipoId())) {
            return new Causa("RF-025", "El equipo indicado no juega este partido.");
        }
        if (registro == null) {
            return new Causa(ErroresTecnicos.REFERENCIA_INVALIDA, "El registro indicado no existe en la liga.");
        }
        String nombre = "«" + registro.nombre() + "»";
        if (!registro.temporadaId().equals(cedula.temporadaId())) {
            return new Causa("RF-019", nombre + " no tiene registro en la temporada de este partido.");
        }
        if (!"ACTIVO".equals(registro.estado())) {
            return new Causa("RF-019", nombre + " no puede jugar: su registro " + estadoRegistro(registro.estado())
                    + ".");
        }
        if (registro.sancionado()) {
            return new Causa("RF-020", nombre + " tiene una sanción vigente que le impide jugar.");
        }
        if (!registro.equipoId().equals(jugador.equipoId())) {
            EquipoAlineacion destino = equipos.get(jugador.equipoId());
            boolean mismoClub = destino != null && registro.clubId().equals(destino.clubId());
            boolean inmediataSuperior = destino != null && registro.categoriaSuperiorId() != null
                    && registro.categoriaSuperiorId().equals(destino.categoriaId());
            if (!mismoClub || !inmediataSuperior) {
                return new Causa("VAL-018", nombre
                        + " solo puede alinearse con su equipo o con el de la categoría inmediata superior de su club.");
            }
        }
        return null;
    }

    /** Código del ERS y mensaje de por qué un jugador no puede alinearse. */
    private record Causa(String codigo, String mensaje) {
    }

    private static String estadoRegistro(String estado) {
        return switch (estado) {
            case "PENDIENTE_VALIDACION" -> "está pendiente de validación";
            case "SUSPENDIDO" -> "está suspendido";
            case "BAJA" -> "está dado de baja";
            case "RECHAZADO" -> "fue rechazado";
            default -> "no está activo";
        };
    }

    private static Integer goles(Integer valor, String campo) {
        if (valor == null) {
            throw invalida("Indica los goles de los dos equipos.").con("errores",
                    List.of(errorDeCampo(campo, "Es obligatorio.")));
        }
        if (valor < 0 || valor > GOLES_MAXIMOS) {
            throw invalida("Los goles y los penales van de 0 a " + GOLES_MAXIMOS + ".").con("errores",
                    List.of(errorDeCampo(campo, "Fuera de rango.")));
        }
        return valor;
    }

    private static Map<String, Object> errorDeCampo(String campo, String mensaje) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("campo", campo);
        error.put("mensaje", mensaje);
        return error;
    }

    private static Map<String, Object> errorDeJugador(int indice, UUID registroId, String codigo, String mensaje) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("campo", "jugadores[" + indice + "]");
        error.put("indice", indice);
        error.put("registroJugadorId", registroId.toString());
        error.put("codigo", codigo);
        error.put("mensaje", mensaje);
        return error;
    }

    private static String estadoLegible(Enum<?> estado) {
        return estado.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }

    private static ReglaNegocioException duplicada() {
        return ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO, "El partido ya tiene cédula.");
    }

    private void auditar(String accion, UUID cedulaId, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, cedulaId.toString(), null, detalle));
    }
}
