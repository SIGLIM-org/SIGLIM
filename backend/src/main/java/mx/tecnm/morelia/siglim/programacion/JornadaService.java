package mx.tecnm.morelia.siglim.programacion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.CanchaDeLiga;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosJornada;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosPartido;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.EquipoDeLiga;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.GrupoDeLiga;
import mx.tecnm.morelia.siglim.programacion.PartidoDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Programación manual de jornadas y partidos (M-07.1, H-12.1).
 *
 * <ul>
 *   <li><b>Alcance.</b> La jornada y el partido son recursos de la liga: se crean y se ajustan con
 *       {@code programacion.jornada:EDITAR} en el ámbito {@code LIGA}. La consulta usa
 *       {@code programacion.jornada:CONSULTAR}, tal como define la tabla de convenciones de API §6.7
 *       (el listado del módulo usa {@code EDITAR}).</li>
 *   <li><b>Solo en borrador.</b> Se registra un partido y se ajusta solo mientras su jornada está en
 *       {@code BORRADOR}; en revisión o publicada responde {@code 409 RF-090}, porque lo que se
 *       aprueba debe ser lo que se publica (ERS §4.3.6).</li>
 *   <li><b>Ajuste con motivo (RF-040, RN-018).</b> Todo cambio de equipos, cancha o inicio exige
 *       motivo de al menos 10 caracteres ({@code 400 RN-021}); queda en la auditoría y el servicio
 *       fija el motivo de la operación para los triggers. Un {@code PATCH} sin cambios no escribe ni
 *       audita.</li>
 *   <li><b>Conflictos duros (RF-039, VAL-003 a VAL-006).</b> {@link ValidadorConflictos} rechaza,
 *       antes de escribir, el equipo duplicado, la cancha ocupada, la cancha inactiva o restringida y
 *       el equipo dado de baja o sancionado, con un mensaje que nombra el otro partido, la cancha y
 *       el horario. El trigger {@code tg_partidos_programacion} y la restricción GiST siguen siendo
 *       la última garantía ante una carrera entre dos peticiones.</li>
 *   <li><b>Auditoría (RF-093).</b> {@code REGISTRAR_JORNADA}, {@code REGISTRAR_PARTIDO} y
 *       {@code AJUSTAR_PARTIDO} en M-07, con identificadores y sin datos personales.</li>
 * </ul>
 *
 * <p>Refs: H-12.1, RF-033, RF-036, RF-039, RF-040, RF-093, RN-018, RN-021, VAL-003 a VAL-006,
 * ADR-0007, ADR-0011
 */
@Service
public class JornadaService {

    private static final String CONSULTAR = "programacion.jornada:CONSULTAR";
    private static final String EDITAR = "programacion.jornada:EDITAR";
    private static final String MODULO = "M-07";
    private static final String ENTIDAD_JORNADA = "jornadas";
    private static final String ENTIDAD_PARTIDO = "partidos";
    private static final int TAMANO_MAXIMO = 100;
    /** [PROPUESTA] Igual que las demás transiciones y bloqueos: mínimo de las observaciones. */
    private static final int MINIMO_MOTIVO = 10;

    private final JornadaRepository jornadas;
    private final ValidadorConflictos validador;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final UUID ligaId;

    JornadaService(JornadaRepository jornadas, ValidadorConflictos validador, Autorizacion autorizacion,
            AuditoriaService auditoria, MotivoOperacion motivoOperacion, SiglimPropiedades propiedades) {
        this.jornadas = jornadas;
        this.validador = validador;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.ligaId = propiedades.ligaId();
    }

    // ── Jornadas ────────────────────────────────────────────────────────────────────────────────

    /**
     * Jornadas de un grupo, por número (RF-033).
     *
     * @param grupoId filtro obligatorio: la programación se arma por grupo
     * @param estado  filtro opcional por estado de publicación
     * @param pagina  página, desde 0
     * @param tamano  filas por página, de 1 a 100
     * @throws ReglaNegocioException 400 {@code VALIDACION} sin {@code grupoId} o con paginación inválida
     */
    @Transactional(readOnly = true)
    public Pagina<JornadaDto> listar(UUID grupoId, EstadoJornada estado, int pagina, int tamano) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        if (grupoId == null) {
            throw invalida("Indica el grupo con el filtro grupoId.");
        }
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw invalida("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        jornadas.buscarGrupo(ligaId, grupoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Grupo " + grupoId));

        long desplazamiento = (long) pagina * tamano;
        List<JornadaDto> contenido = jornadas.listarJornadas(ligaId, grupoId, estado, tamano, desplazamiento)
                .stream()
                .map(datos -> aDto(datos, null))
                .toList();
        return Pagina.de(contenido, pagina, tamano, jornadas.contarJornadas(ligaId, grupoId, estado));
    }

    /** Detalle de la jornada con sus partidos ordenados por inicio (RF-033). */
    @Transactional(readOnly = true)
    public JornadaDto consultar(UUID id) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        DatosJornada jornada = jornadas.buscarJornada(ligaId, id).orElseThrow(() -> noExisteJornada(id));
        return aDto(jornada, jornadas.listarPartidos(id).stream().map(JornadaService::aDto).toList());
    }

    /**
     * Registra una jornada en {@code BORRADOR} con la autoría de la sesión (RF-033).
     *
     * <p>Orden: permiso → datos del cuerpo ({@code 400}) → grupo de la liga ({@code 422}) → fase del
     * mismo grupo ({@code 422}) → número libre ({@code 409 DUPLICADO}).
     */
    @Transactional
    public JornadaDto crear(CrearJornadaRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        if (peticion == null || peticion.grupoId() == null) {
            throw invalida("El grupo de la jornada es obligatorio.");
        }
        int numero = numeroValido(peticion.numero());
        GrupoDeLiga grupo = jornadas.buscarGrupo(ligaId, peticion.grupoId())
                .orElseThrow(() -> referenciaInvalida("El grupo indicado no existe en la liga."));
        if (peticion.faseId() != null && !jornadas.faseDelGrupo(grupo.id(), peticion.faseId())) {
            throw referenciaInvalida("La fase indicada no pertenece al grupo de la jornada.");
        }
        if (jornadas.existeNumero(grupo.id(), peticion.faseId(), numero)) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "El grupo ya tiene la jornada " + numero
                            + (peticion.faseId() == null ? "" : " en esa fase") + ".");
        }

        UUID id = jornadas.insertarJornada(grupo.id(), peticion.faseId(), numero,
                UsuarioActual.idRequerido());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("grupoId", grupo.id().toString());
        if (peticion.faseId() != null) {
            detalle.put("faseId", peticion.faseId().toString());
        }
        detalle.put("numero", numero);
        detalle.put("estado", EstadoJornada.BORRADOR.name());
        auditar("REGISTRAR_JORNADA", ENTIDAD_JORNADA, id, null, detalle);
        return aDto(buscarJornada(id), List.of());
    }

    // ── Partidos ────────────────────────────────────────────────────────────────────────────────

    /**
     * Registra un partido {@code PROGRAMADO} en una jornada en {@code BORRADOR} (RF-033, RF-036).
     *
     * <p>Orden: permiso → jornada existe ({@code 404}) → jornada en borrador ({@code 409 RF-090}) →
     * cuerpo ({@code 400}) → equipos del grupo ({@code 422 RF-033}) → cancha de la liga ({@code 422})
     * → conflictos duros ({@code 409 VAL-003} a {@code VAL-006}).
     */
    @Transactional
    public PartidoDto registrarPartido(UUID jornadaId, CrearPartidoRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        DatosJornada jornada = jornadas.buscarJornadaParaActualizar(ligaId, jornadaId)
                .orElseThrow(() -> noExisteJornada(jornadaId));
        exigirBorrador(jornada);
        if (peticion == null) {
            throw invalida("Indica los equipos, la cancha y el inicio del partido.");
        }
        if (Objects.equals(peticion.equipoLocalId(), peticion.equipoVisitanteId())) {
            throw invalida("El equipo local y el visitante deben ser distintos.");
        }
        EquipoDeLiga local = equipoDelGrupo(jornada.grupoId(), peticion.equipoLocalId());
        EquipoDeLiga visitante = equipoDelGrupo(jornada.grupoId(), peticion.equipoVisitanteId());
        if (peticion.inicio() == null) {
            throw invalida("La fecha y hora de inicio son obligatorias.");
        }
        CanchaDeLiga cancha = bloquearCancha(peticion.canchaId());

        validador.validar(jornada.grupoId(), null, local, visitante, cancha, peticion.inicio());

        UUID id = jornadas.insertarPartido(jornada.id(), local.id(), visitante.id(), peticion.canchaId(),
                peticion.inicio(), UsuarioActual.idRequerido());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("jornadaId", jornada.id().toString());
        detalle.put("equipoLocalId", local.id().toString());
        detalle.put("equipoVisitanteId", visitante.id().toString());
        detalle.put("canchaId", peticion.canchaId().toString());
        auditar("REGISTRAR_PARTIDO", ENTIDAD_PARTIDO, id, null, detalle);
        return aDto(buscarPartido(id));
    }

    /**
     * Ajusta equipos, cancha o inicio de un partido de una jornada en {@code BORRADOR} (RF-040).
     *
     * <p>Un campo ausente conserva su valor. Si hay cambios, el motivo es obligatorio
     * ({@code 400 RN-021}) y se registra en la auditoría (RN-018); sin cambios, la petición es
     * inocua. Un partido finalizado o cancelado no se modifica ({@code 409 RF-046}). Antes de
     * escribir se repiten los conflictos duros ({@code 409 VAL-003} a {@code VAL-006}), sin contar
     * el propio partido.
     */
    @Transactional
    public PartidoDto actualizarPartido(UUID id, ActualizarPartidoRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        DatosPartido actual = jornadas.buscarPartidoParaActualizar(ligaId, id)
                .orElseThrow(() -> noExistePartido(id));
        if (actual.estadoJornada() != EstadoJornada.BORRADOR) {
            throw ReglaNegocioException.conflicto("RF-090",
                    "Solo se ajusta un partido de una jornada en borrador; la jornada está "
                            + actual.estadoJornada().texto() + ".");
        }
        if (actual.estado() == EstadoPartido.FINALIZADO || actual.estado() == EstadoPartido.CANCELADO) {
            throw ReglaNegocioException.conflicto("RF-046",
                    "El partido está " + actual.estado().name().toLowerCase()
                            + " y ya no se puede ajustar.");
        }

        UUID localId = peticion == null || peticion.equipoLocalId() == null
                ? actual.equipoLocalId() : peticion.equipoLocalId();
        UUID visitanteId = peticion == null || peticion.equipoVisitanteId() == null
                ? actual.equipoVisitanteId() : peticion.equipoVisitanteId();
        UUID canchaId = peticion == null || peticion.canchaId() == null
                ? actual.canchaId() : peticion.canchaId();
        OffsetDateTime inicio = peticion == null || peticion.inicio() == null
                ? actual.inicio() : peticion.inicio();

        List<String> campos = camposCambiados(actual, localId, visitanteId, canchaId, inicio);
        if (campos.isEmpty()) {
            return aDto(actual);
        }
        if (localId.equals(visitanteId)) {
            throw invalida("El equipo local y el visitante deben ser distintos.");
        }
        String motivo = motivoValido(peticion == null ? null : peticion.motivo());
        EquipoDeLiga local = equipoDelGrupo(actual.grupoId(), localId);
        EquipoDeLiga visitante = equipoDelGrupo(actual.grupoId(), visitanteId);
        CanchaDeLiga cancha = bloquearCancha(canchaId);

        validador.validar(actual.grupoId(), id, local, visitante, cancha, inicio);

        motivoOperacion.fijar(motivo);
        jornadas.actualizarPartido(id, local.id(), visitante.id(), canchaId, inicio);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", campos);
        detalle.put("jornadaId", actual.jornadaId().toString());
        auditar("AJUSTAR_PARTIDO", ENTIDAD_PARTIDO, id, motivo, detalle);
        return aDto(buscarPartido(id));
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    private DatosJornada buscarJornada(UUID id) {
        return jornadas.buscarJornada(ligaId, id).orElseThrow(() -> noExisteJornada(id));
    }

    private DatosPartido buscarPartido(UUID id) {
        return jornadas.buscarPartido(ligaId, id).orElseThrow(() -> noExistePartido(id));
    }

    private static RecursoNoEncontradoException noExisteJornada(UUID id) {
        return new RecursoNoEncontradoException("Jornada " + id);
    }

    private static RecursoNoEncontradoException noExistePartido(UUID id) {
        return new RecursoNoEncontradoException("Partido " + id);
    }

    private static void exigirBorrador(DatosJornada jornada) {
        if (jornada.estadoPublicacion() != EstadoJornada.BORRADOR) {
            throw ReglaNegocioException.conflicto("RF-090",
                    "Solo se programan partidos de una jornada en borrador; la jornada " + jornada.numero()
                            + " está " + jornada.estadoPublicacion().texto() + ".");
        }
    }

    /** El equipo debe existir y pertenecer al grupo de la jornada (RF-033). */
    private EquipoDeLiga equipoDelGrupo(UUID grupoId, UUID equipoId) {
        if (equipoId == null) {
            throw invalida("Indica los dos equipos del partido.");
        }
        EquipoDeLiga equipo = jornadas.buscarEquipo(ligaId, equipoId)
                .orElseThrow(() -> referenciaInvalida("Uno de los equipos indicados no existe en la liga."));
        if (!equipo.grupoId().equals(grupoId)) {
            throw ReglaNegocioException.noProcesable("RF-033",
                    "El equipo «" + equipo.nombre() + "» no pertenece al grupo de la jornada.");
        }
        return equipo;
    }

    /**
     * Bloquea la cancha de la liga y la devuelve con su nombre y estado para VAL-005.
     *
     * @throws ReglaNegocioException 422 {@code REFERENCIA_INVALIDA} si falta o no existe en la liga
     */
    private CanchaDeLiga bloquearCancha(UUID canchaId) {
        if (canchaId == null) {
            throw referenciaInvalida("La cancha indicada no existe en la liga.");
        }
        return jornadas.bloquearCancha(ligaId, canchaId)
                .orElseThrow(() -> referenciaInvalida("La cancha indicada no existe en la liga."));
    }

    private static int numeroValido(Integer numero) {
        if (numero == null) {
            throw invalida("El número de la jornada es obligatorio.");
        }
        if (numero < 1) {
            throw invalida("El número de la jornada debe ser mayor que cero.");
        }
        return numero;
    }

    private static String motivoValido(String motivo) {
        String texto = motivo == null ? "" : motivo.strip();
        if (texto.length() < MINIMO_MOTIVO) {
            throw ReglaNegocioException.invalida("RN-021",
                    "Escribe un motivo de al menos " + MINIMO_MOTIVO + " caracteres para ajustar el partido.");
        }
        return texto;
    }

    private static List<String> camposCambiados(DatosPartido actual, UUID localId, UUID visitanteId,
            UUID canchaId, OffsetDateTime inicio) {
        List<String> campos = new ArrayList<>();
        if (!Objects.equals(localId, actual.equipoLocalId())) {
            campos.add("equipoLocalId");
        }
        if (!Objects.equals(visitanteId, actual.equipoVisitanteId())) {
            campos.add("equipoVisitanteId");
        }
        if (!Objects.equals(canchaId, actual.canchaId())) {
            campos.add("canchaId");
        }
        if (!Objects.equals(inicio.toInstant(), actual.inicio().toInstant())) {
            campos.add("inicio");
        }
        return campos;
    }

    // ── Representación ─────────────────────────────────────────────────────────────────────────

    private static JornadaDto aDto(DatosJornada datos, List<PartidoDto> partidos) {
        return new JornadaDto(datos.id(), datos.grupoId(), datos.faseId(), datos.numero(),
                datos.estadoPublicacion(), partidos, datos.creadoEn(), datos.actualizadoEn());
    }

    private static PartidoDto aDto(DatosPartido datos) {
        return new PartidoDto(datos.id(), datos.jornadaId(),
                new ReferenciaDto(datos.equipoLocalId(), datos.equipoLocalNombre()),
                new ReferenciaDto(datos.equipoVisitanteId(), datos.equipoVisitanteNombre()),
                new ReferenciaDto(datos.canchaId(), datos.canchaNombre()),
                datos.inicio(), datos.finBloque(), datos.estado(), datos.motivoLocalia(),
                datos.creadoEn(), datos.actualizadoEn());
    }

    // ── Auditoría (RF-093): solo identificadores ───────────────────────────────────────────────

    private void auditar(String accion, String entidad, UUID entidadId, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, entidad, entidadId.toString(), motivo,
                detalle));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }

    private static ReglaNegocioException referenciaInvalida(String mensaje) {
        return ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA, mensaje);
    }
}
