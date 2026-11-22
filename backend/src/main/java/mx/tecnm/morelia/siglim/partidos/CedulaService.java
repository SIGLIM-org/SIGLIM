package mx.tecnm.morelia.siglim.partidos;

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
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.EquipoAlineacion;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.MarcadorDefault;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.PartidoCedula;
import mx.tecnm.morelia.siglim.partidos.CedulaRepository.RegistroAlineable;
import mx.tecnm.morelia.siglim.partidos.GuardarAlineacionesRequest.JugadorRequest;
import mx.tecnm.morelia.siglim.programacion.EstadoPartido;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captura de la cédula del partido: abrirla, la alineación con elegibilidad y el marcador (M-08.2,
 * H-16.1). Los eventos, las incidencias y el envío a validación son de H-16.2.
 *
 * <ul>
 *   <li><b>Permiso.</b> Consultar exige {@code partidos.cedula:CONSULTAR} y capturar,
 *       {@code partidos.cedula:CAPTURAR}, los dos con ámbito {@code LIGA}: la cédula es de la liga y
 *       en la matriz inicial los tiene el Capturista. La vista del árbitro designado ({@code PROPIO})
 *       llega con su bandeja (H-17) <b>[PROPUESTA]</b> (DEC-155).</li>
 *   <li><b>Abrir (RF-025, RF-046).</b> Solo con el partido {@code EN_CURSO}; un partido tiene una sola
 *       cédula ({@code 409 DUPLICADO}). Quien la abre queda como capturista y nunca podrá aprobarla
 *       (VAL-021).</li>
 *   <li><b>Contenido solo en captura (RF-122).</b> La alineación y el marcador se guardan con la
 *       cédula {@code EN_CAPTURA}; en otro estado, {@code 409 RF-122}.</li>
 *   <li><b>Alineación (RF-019, RF-020, RF-110, VAL-002, VAL-018).</b> {@code PUT} reemplaza la
 *       alineación completa. Se valida cada jugador y, si alguno no puede jugar, nada se guarda y se
 *       responde {@code 422 VAL-002} con la lista {@code errores}: un elemento por jugador con su
 *       {@code codigo} ({@code RF-019}, {@code RF-020}, {@code VAL-018}, {@code RF-025} o
 *       {@code REFERENCIA_INVALIDA}) y un mensaje con su nombre (DEC-156).</li>
 *   <li><b>Marcador (RF-047, RF-126, DEC-24, DEC-28).</b> Con un equipo no presentado el sistema asigna
 *       el marcador por default de la versión vigente de los parámetros (3-0 <b>[DEMO]</b>, DEC-72) y
 *       la petición no lleva goles; si no, los dos goles son obligatorios y los penales solo se
 *       registran con empate (DEC-157).</li>
 *   <li><b>Auditoría (RF-093).</b> {@code ABRIR_CEDULA}, {@code CAPTURAR_ALINEACION} y
 *       {@code CAPTURAR_MARCADOR} en M-08, sin nombres de jugadores.</li>
 * </ul>
 *
 * <p>Refs: H-16.1, RF-019, RF-020, RF-025, RF-046, RF-047, RF-110, RF-122, RF-126, VAL-002, VAL-018,
 * DEC-12, DEC-19, DEC-24, DEC-28, DEC-155, DEC-156, DEC-157
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

    private final CedulaRepository cedulas;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    CedulaService(CedulaRepository cedulas, Autorizacion autorizacion, AuditoriaService auditoria,
            SiglimPropiedades propiedades) {
        this.cedulas = cedulas;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
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

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

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
