package mx.tecnm.morelia.siglim.programacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaDto.NoAsignado;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaDto.ResumenDia;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaDto.SinArbitro;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaDto.Totales;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaRepository.CanchaDeAsignacion;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaRepository.EmparejamientoPendiente;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaRepository.GrupoDeLaFecha;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaRepository.ParametrosAsignacion;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaRepository.PartidoDeLaFecha;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaRepository.PeriodoDeCancha;
import mx.tecnm.morelia.siglim.programacion.ConflictoRepository.SancionDeEquipo;
import mx.tecnm.morelia.siglim.programacion.FechaDto.FilaPartido;
import mx.tecnm.morelia.siglim.programacion.FechaRepository.DatosFecha;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.CanchaDeLiga;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosJornada;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.EquipoDeLiga;
import mx.tecnm.morelia.siglim.programacion.ResultadoAsignacion.PartidoAsignado;
import mx.tecnm.morelia.siglim.programacion.ResultadoAsignacion.PartidoNoAsignado;
import mx.tecnm.morelia.siglim.programacion.ResultadoCentrales.CentralDesignado;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.CanchaAsignable;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.CategoriaAsignable;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.EquipoAsignable;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.PartidoPorColocar;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.PeriodoCancha;
import mx.tecnm.morelia.siglim.programacion.ValidadorConflictos.Conflicto;
import mx.tecnm.morelia.siglim.programacion.ValidadorLocalia.Advertencia;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asignación automática de cancha y horario a una fecha completa (H-33.2, DEC-181, ADR-0015).
 *
 * <p><b>Orden de errores</b> ({@code POST /fechas/{id}/asignacion}, DEC-206):
 * <ol>
 *   <li>{@code 403 VAL-015} sin {@code programacion.jornada:EDITAR} en el ámbito {@code LIGA}.</li>
 *   <li>{@code 400 VALIDACION} sin {@code modo}.</li>
 *   <li>{@code 400 RN-021} al regenerar con un motivo de menos de 10 caracteres.</li>
 *   <li>{@code 404} si la fecha no es de la liga. La fecha y después sus jornadas se bloquean con
 *       {@code FOR UPDATE}, como el envío y el dictamen: dos asignaciones no se cruzan (ver
 *       <b>Candados</b>).</li>
 *   <li>{@code 409 RF-003} con la temporada cerrada.</li>
 *   <li>{@code 409 RF-090} si alguna jornada de la fecha no está en {@code BORRADOR}.</li>
 *   <li>{@code 409 RF-006} sin parámetros vigentes (DEC-190).</li>
 *   <li>{@code 422 VALIDACION} [PROPUESTA] si la fecha no tiene emparejamientos vigentes.</li>
 *   <li>{@code 409 DUPLICADO} [PROPUESTA] si un grupo ya tiene una jornada manual con el número de la
 *       fecha (DEC-208).</li>
 *   <li>{@code 409 RF-046} [PROPUESTA] al regenerar, si un partido de la fecha ya no se puede cancelar
 *       (solo {@code PROGRAMADO} y {@code SUSPENDIDO} pasan a {@code CANCELADO}, DEC-208).</li>
 * </ol>
 *
 * <p><b>Flujo</b>, en una transacción (RNF-004): al regenerar, los partidos de la fecha pasan a
 * {@code CANCELADO} con el motivo y su historial, sus designaciones pierden la vigencia y los
 * emparejamientos se sueltan (nada se borra, RF-044); se crean las jornadas de la fecha que falten;
 * se arma la {@link SolicitudAsignacion} con las lecturas ordenadas de
 * {@link AsignacionFechaRepository} y se corre {@link MotorAsignacion}. Cada candidato se confirma con
 * {@link ValidadorConflictos} (las reglas de H-12): si aún tiene conflicto no se inserta y queda sin
 * asignar con ese código (DEC-207). Si {@link ValidadorLocalia} advierte, se guarda el motivo
 * «Asignación automática: …» del motor, o uno con la advertencia si el motor no propuso. Se guardan las
 * causas de los no asignados, {@code generada_por}, {@code generada_en} y
 * {@code parametros_generacion} (RF-041) y se audita {@code GENERAR_FECHA} sin datos personales.
 *
 * <p><b>Árbitro central automático (H-34.2, DEC-217 a DEC-219).</b> Después de insertar, y solo si la
 * sesión tiene además {@code arbitraje.designacion:AUTOMATICA} en ámbito de liga, se pide a
 * {@link DesignadorArbitroCentral} el central de los partidos existentes no cancelados (solo en
 * {@code PENDIENTES}: así un partido cuyo central se retiró sin sustituto lo vuelve a recibir) y de
 * los recién insertados. Sin ese permiso la asignación sigue igual, sin designaciones y con una
 * advertencia (DEC-218). Los partidos sin árbitro elegible van en {@code sinArbitro} con la causa de
 * DEC-216, y un central que declaró no estar disponible en ese horario deja una advertencia
 * {@code RF-022} (DEC-219).
 *
 * <p><b>Candados (DEC-217).</b> Orden total: fecha → jornadas → partidos existentes de la fecha →
 * canchas → árbitros. Los partidos se bloquean en los dos modos con {@code FOR UPDATE OF p}, antes de
 * cualquier escritura y antes de bloquear canchas: el ajuste de un partido (RF-040) bloquea
 * partido → cancha, y bloquear los partidos después de las canchas formaría un ciclo con él. Los
 * árbitros los bloquea {@link DesignadorArbitroCentral} al final (DEC-215).
 *
 * <p>Refs: H-33.2, H-34.2, RF-032, RF-035, RF-036, RF-039, RF-041, RF-042, RF-044, RN-021, VAL-003, VAL-004,
 * VAL-005, VAL-006, VAL-007, VAL-027, DEC-179, DEC-180, DEC-181, DEC-182, DEC-206, DEC-207, DEC-208,
 * DEC-215, DEC-217 a DEC-219, ADR-0006, ADR-0015
 */
@Service
public class AsignacionFechaService {

    private static final String EDITAR = "programacion.jornada:EDITAR";
    /** Permiso adicional para designar el árbitro central al asignar (DEC-182, DEC-218). */
    private static final String AUTOMATICA = "arbitraje.designacion:AUTOMATICA";
    /** [PROPUESTA] Advertencia de una asignación sin {@link #AUTOMATICA} (DEC-218). */
    static final String SIN_DESIGNACION_AUTOMATICA = "No se designaron árbitros centrales: tu sesión no tiene el "
            + "permiso arbitraje.designacion:AUTOMATICA. La Comisión de Arbitraje los designa en F-24.";
    /** Código de la advertencia de un central que declaró no estar disponible (DEC-204, DEC-219). */
    private static final String NO_DISPONIBLE = "RF-022";
    private static final String MODULO = "M-07";
    private static final String ENTIDAD = "fechas_programacion";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    /** El mismo mínimo de los motivos de la programación (RN-021, DEC-189). */
    private static final int MINIMO_MOTIVO = 10;

    private final AsignacionFechaRepository asignacion;
    private final FechaRepository fechas;
    private final JornadaRepository jornadas;
    private final ConflictoRepository conflictos;
    private final LocaliaRepository localiaRepository;
    private final ValidadorConflictos validador;
    private final ValidadorLocalia localia;
    private final FechaService fechaService;
    private final DesignadorArbitroCentral designador;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final JsonMapper json;
    private final UUID ligaId;

    AsignacionFechaService(AsignacionFechaRepository asignacion, FechaRepository fechas, JornadaRepository jornadas,
            ConflictoRepository conflictos, LocaliaRepository localiaRepository, ValidadorConflictos validador,
            ValidadorLocalia localia, FechaService fechaService, DesignadorArbitroCentral designador,
            Autorizacion autorizacion, AuditoriaService auditoria, MotivoOperacion motivoOperacion, JsonMapper json,
            SiglimPropiedades propiedades) {
        this.asignacion = asignacion;
        this.fechas = fechas;
        this.jornadas = jornadas;
        this.conflictos = conflictos;
        this.localiaRepository = localiaRepository;
        this.validador = validador;
        this.localia = localia;
        this.fechaService = fechaService;
        this.designador = designador;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.json = json;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Asigna cancha y horario a los partidos de la fecha y, con {@code arbitraje.designacion:AUTOMATICA},
     * su árbitro central. El orden de errores está en la descripción de la clase.
     *
     * <p>Candados, en este orden total (DEC-217): fecha → jornadas → partidos existentes de la fecha
     * (en los dos modos) → canchas (al confirmar cada candidato) → árbitros (en
     * {@link DesignadorArbitroCentral}). El ajuste RF-040 bloquea partido → cancha; por eso los partidos
     * van antes que las canchas.
     *
     * @return la fecha actualizada con los totales, los no asignados, los sin árbitro, las advertencias
     *         y el resumen
     */
    @Transactional
    public AsignacionFechaDto asignar(UUID id, AsignarFechaRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        if (peticion == null || peticion.modo() == null) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "Indica el modo de la asignación: PENDIENTES o REGENERAR.");
        }
        ModoAsignacion modo = peticion.modo();
        String motivo = peticion.motivo() == null || peticion.motivo().isBlank() ? null : peticion.motivo().strip();
        if (modo == ModoAsignacion.REGENERAR && (motivo == null || motivo.length() < MINIMO_MOTIVO)) {
            throw ReglaNegocioException.invalida("RN-021", "Escribe un motivo de al menos " + MINIMO_MOTIVO
                    + " caracteres para regenerar la fecha: sus partidos se cancelan con ese motivo.");
        }

        if (!fechas.bloquear(ligaId, id)) {
            throw new RecursoNoEncontradoException("Fecha " + id);
        }
        List<DatosJornada> deLaFecha = jornadas.bloquearJornadasDeFecha(id);
        DatosFecha fecha = fechas.buscar(ligaId, id).orElseThrow(() -> new RecursoNoEncontradoException("Fecha " + id));
        if (fechas.temporadaCerrada(fecha.temporadaId())) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada está cerrada: sus fechas son de solo lectura.");
        }
        for (DatosJornada jornada : deLaFecha) {
            if (jornada.estadoPublicacion() != EstadoJornada.BORRADOR) {
                throw ReglaNegocioException.conflicto("RF-090",
                        "La asignación automática solo opera sobre una fecha en borrador; en la fecha "
                                + fecha.numero() + " la jornada " + jornada.numero() + " está "
                                + jornada.estadoPublicacion().texto() + ".");
            }
        }
        ParametrosAsignacion parametros = asignacion.parametrosVigentes(fecha.temporadaId())
                .orElseThrow(() -> ReglaNegocioException.conflicto("RF-006",
                        "La temporada no tiene parámetros deportivos; regístralos para fijar el horario de "
                                + "juego y el bloque de cancha antes de asignar la fecha."));
        List<GrupoDeLaFecha> grupos = asignacion.gruposDeLaFecha(fecha.temporadaId(), id, fecha.numero());
        if (grupos.isEmpty()) {
            // [PROPUESTA] Sin calendario no hay nada que asignar.
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.VALIDACION,
                    "La fecha " + fecha.numero() + " no tiene emparejamientos en el calendario vigente; "
                            + "genera el calendario antes de asignarla.");
        }
        for (GrupoDeLaFecha grupo : grupos) {
            if (grupo.jornadaId() == null && grupo.numeroOcupado()) {
                throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                        "El grupo «" + grupo.nombre() + "» ya tiene una jornada " + fecha.numero()
                                + " registrada a mano, fuera de la fecha; cámbiale el número o programa ese "
                                + "grupo a mano.");
            }
        }
        // DEC-217: los partidos se bloquean en los dos modos, antes de cualquier escritura y antes que las
        // canchas.
        List<PartidoDeLaFecha> existentes = asignacion.partidosDeLaFecha(id);
        List<PartidoDeLaFecha> aCancelar = modo == ModoAsignacion.REGENERAR ? existentes : List.of();
        for (PartidoDeLaFecha partido : aCancelar) {
            if (partido.estado() != EstadoPartido.PROGRAMADO && partido.estado() != EstadoPartido.SUSPENDIDO) {
                throw ReglaNegocioException.conflicto("RF-046",
                        "El partido «" + partido.localNombre() + " vs. " + partido.visitanteNombre() + "» está "
                                + partido.estado().name() + " y no se puede cancelar para regenerar la fecha.");
            }
        }

        // ── Escrituras ──────────────────────────────────────────────────────────────────────────
        UUID perfil = UsuarioActual.idRequerido();
        if (motivo != null) {
            motivoOperacion.fijar(motivo);
        }
        if (modo == ModoAsignacion.REGENERAR) {
            aCancelar.forEach(partido -> asignacion.cancelarPartido(partido.id()));
            asignacion.retirarDesignaciones(aCancelar.stream().map(PartidoDeLaFecha::id).toList(), motivo);
            asignacion.soltarEmparejamientos(fecha.temporadaId(), fecha.numero());
        }
        Map<UUID, UUID> jornadaPorGrupo = new LinkedHashMap<>();
        List<String> jornadasCreadas = new ArrayList<>();
        for (GrupoDeLaFecha grupo : grupos) {
            UUID jornadaId = grupo.jornadaId();
            if (jornadaId == null) {
                jornadaId = jornadas.insertarJornadaDeFecha(grupo.id(), id, fecha.numero(), perfil);
                jornadasCreadas.add(jornadaId.toString());
            }
            jornadaPorGrupo.put(grupo.id(), jornadaId);
        }

        Map<UUID, EmparejamientoPendiente> pendientes = new LinkedHashMap<>();
        asignacion.pendientes(fecha.temporadaId(), fecha.numero())
                .forEach(pendiente -> pendientes.put(pendiente.id(), pendiente));
        ResultadoAsignacion propuesta = MotorAsignacion.asignar(solicitud(fecha, parametros, pendientes));

        List<NoAsignado> noAsignados = new ArrayList<>();
        for (PartidoNoAsignado sinLugar : propuesta.noAsignados()) {
            asignacion.marcarNoAsignado(sinLugar.emparejamientoId(), sinLugar.codigo(), sinLugar.causa());
            noAsignados.add(new NoAsignado(sinLugar.emparejamientoId(), sinLugar.codigo(), sinLugar.causa()));
        }
        List<UUID> insertados = new ArrayList<>();
        for (PartidoAsignado propuesto : propuesta.asignados()) {
            EmparejamientoPendiente pendiente = pendientes.get(propuesto.emparejamientoId());
            CanchaDeLiga cancha = jornadas.bloquearCancha(ligaId, propuesto.canchaId())
                    .orElseThrow(() -> new IllegalStateException("La cancha propuesta no es de la liga."));
            List<Conflicto> encontrados = validador.evaluar(pendiente.grupoId(), null, pendiente.local(),
                    pendiente.visitante(), cancha, propuesto.inicio());
            if (!encontrados.isEmpty()) {
                // DEC-207: el validador de H-12 manda; el candidato no se inserta.
                Conflicto primero = encontrados.getFirst();
                asignacion.marcarNoAsignado(pendiente.id(), primero.codigo(), primero.mensaje());
                noAsignados.add(new NoAsignado(pendiente.id(), primero.codigo(), primero.mensaje()));
                continue;
            }
            UUID jornadaId = jornadaPorGrupo.get(pendiente.grupoId());
            String motivoLocalia = motivoLocalia(propuesto,
                    localia.evaluar(pendiente.grupoId(), jornadaId, pendiente.local(), cancha.id()));
            UUID partidoId = jornadas.insertarPartido(jornadaId, pendiente.local().id(), pendiente.visitante().id(),
                    cancha.id(), propuesto.inicio(), motivoLocalia, perfil, true);
            jornadas.ligarEmparejamiento(pendiente.id(), partidoId);
            insertados.add(partidoId);
        }

        // DEC-217, DEC-218: árbitro central automático, solo con el permiso adicional.
        LinkedHashSet<UUID> porDesignar = new LinkedHashSet<>();
        if (modo == ModoAsignacion.PENDIENTES) {
            existentes.forEach(partido -> porDesignar.add(partido.id()));
        }
        porDesignar.addAll(insertados);
        boolean designacionAutomatica = autorizacion.permite(AUTOMATICA, ObjetivoAutorizacion.deLiga());
        ResultadoCentrales centrales = designacionAutomatica
                ? designador.designarCentrales(List.copyOf(porDesignar))
                : ResultadoCentrales.vacio();
        List<SinArbitro> sinArbitro = centrales.sinCentral().stream()
                .map(sin -> new SinArbitro(sin.partidoId(), sin.codigo(), sin.causa()))
                .toList();
        FechaDto detalleFecha = fechaService.detalle(id);
        List<String> advertencias = new ArrayList<>(propuesta.advertencias());
        if (!designacionAutomatica) {
            advertencias.add(SIN_DESIGNACION_AUTOMATICA);
        }
        advertencias.addAll(advertenciasNoDisponible(centrales.designados(), detalleFecha));

        Totales totales = new Totales(insertados.size(), noAsignados.size(), aCancelar.size(),
                centrales.designados().size(), sinArbitro.size());
        Map<String, Object> generacion = new LinkedHashMap<>();
        generacion.put("versionParametros", parametros.version());
        generacion.put("modo", modo.name());
        if (motivo != null) {
            generacion.put("motivo", motivo);
        }
        generacion.put("asignados", totales.asignados());
        generacion.put("noAsignados", totales.noAsignados());
        generacion.put("cancelados", totales.cancelados());
        generacion.put("designacionAutomatica", designacionAutomatica);
        generacion.put("centrales", totales.centrales());
        generacion.put("sinArbitro", totales.sinArbitro());
        asignacion.registrarGeneracion(id, perfil, json.writeValueAsString(generacion));

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("temporadaId", fecha.temporadaId().toString());
        detalle.put("numero", fecha.numero());
        detalle.put("modo", modo.name());
        detalle.put("versionParametros", parametros.version());
        detalle.put("asignados", totales.asignados());
        detalle.put("noAsignados", totales.noAsignados());
        detalle.put("cancelados", totales.cancelados());
        detalle.put("designacionAutomatica", designacionAutomatica);
        detalle.put("centrales", totales.centrales());
        detalle.put("sinArbitro", totales.sinArbitro());
        detalle.put("jornadasCreadas", jornadasCreadas);
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "GENERAR_FECHA", ENTIDAD, id.toString(), motivo,
                detalle));

        List<ResumenDia> resumen = propuesta.resumen().stream()
                .map(dia -> new ResumenDia(dia.dia(), dia.franjasLibres(), dia.bloqueMinutos(), dia.partidosRequeridos()))
                .toList();
        return new AsignacionFechaDto(detalleFecha, totales, noAsignados, sinArbitro, List.copyOf(advertencias),
                resumen);
    }

    /**
     * DEC-219: una advertencia por cada central designado que declaró no estar disponible en ese horario
     * (RF-022), con los nombres de los equipos tomados del detalle de la fecha.
     */
    private static List<String> advertenciasNoDisponible(List<CentralDesignado> designados, FechaDto fecha) {
        Map<UUID, FilaPartido> porId = new LinkedHashMap<>();
        if (fecha != null && fecha.bloques() != null) {
            fecha.bloques().forEach(bloque -> bloque.partidos().forEach(fila -> porId.put(fila.id(), fila)));
        }
        List<String> advertencias = new ArrayList<>();
        for (CentralDesignado designado : designados) {
            if (!designado.advertencias().contains(NO_DISPONIBLE)) {
                continue;
            }
            FilaPartido fila = porId.get(designado.partidoId());
            String partido = fila == null ? "Un partido"
                    : fila.equipoLocal().nombre() + " vs " + fila.equipoVisitante().nombre();
            // [PROPUESTA] Texto de la advertencia (DEC-219).
            advertencias.add(partido + ": el árbitro central designado declaró que no está disponible en ese "
                    + "horario (RF-022). La Comisión puede cambiarlo en F-24.");
        }
        return advertencias;
    }

    /**
     * El motivo de localía que se guarda (DEC-207): solo si {@link ValidadorLocalia} advierte; el del motor
     * si lo propuso y, si no, uno con las advertencias del validador.
     */
    static String motivoLocalia(PartidoAsignado propuesto, List<Advertencia> advertencias) {
        if (advertencias.isEmpty()) {
            return null;
        }
        if (propuesto.motivoLocalia() != null) {
            return propuesto.motivoLocalia();
        }
        return MotorAsignacion.PREFIJO_LOCALIA
                + advertencias.stream().map(Advertencia::mensaje).collect(Collectors.joining(" "));
    }

    // ── Solicitud del motor ─────────────────────────────────────────────────────────────────────

    private SolicitudAsignacion solicitud(DatosFecha fecha, ParametrosAsignacion parametros,
            Map<UUID, EmparejamientoPendiente> pendientes) {
        List<LocalDate> dias = fecha.dias();
        OffsetDateTime desde = dias.getFirst().atStartOfDay(ZONA_DE_NEGOCIO).toOffsetDateTime();
        OffsetDateTime hasta = dias.getLast().plusDays(1).atStartOfDay(ZONA_DE_NEGOCIO).toOffsetDateTime();
        OffsetDateTime referenciaSanciones = dias.getFirst().atTime(parametros.horaInicioJuego())
                .atZone(ZONA_DE_NEGOCIO).toOffsetDateTime();

        Map<UUID, List<PeriodoCancha>> periodos = new LinkedHashMap<>();
        for (PeriodoDeCancha periodo : asignacion.periodos(ligaId, desde, hasta)) {
            periodos.computeIfAbsent(periodo.canchaId(), cancha -> new ArrayList<>())
                    .add(new PeriodoCancha(periodo.tipo(), periodo.desde(), periodo.hasta()));
        }
        List<CanchaAsignable> canchas = new ArrayList<>();
        for (CanchaDeAsignacion cancha : asignacion.canchas(ligaId)) {
            canchas.add(new CanchaAsignable(cancha.id(), cancha.nombre(), cancha.activa(), cancha.unidadDeportiva(),
                    periodos.getOrDefault(cancha.id(), List.of())));
        }

        List<PartidoPorColocar> partidos = new ArrayList<>();
        for (EmparejamientoPendiente pendiente : pendientes.values()) {
            List<SancionDeEquipo> sanciones = conflictos.buscarSancionVigente(pendiente.grupoId(),
                    pendiente.local().id(), pendiente.visitante().id(), referenciaSanciones);
            CategoriaAsignable categoria = new CategoriaAsignable(pendiente.categoriaId(), pendiente.categoriaNombre(),
                    pendiente.orden(), pendiente.diasJuego(), pendiente.bloqueMinutos());
            List<UUID> delLocal = localiaRepository.canchasDelClub(pendiente.local().clubId(), pendiente.grupoId(),
                    pendiente.local().id()).stream().map(LocaliaRepository.CanchaLocalia::id).toList();
            partidos.add(new PartidoPorColocar(pendiente.id(), pendiente.grupoId(), categoria,
                    equipo(pendiente.local(), sanciones), equipo(pendiente.visitante(), sanciones),
                    Set.copyOf(delLocal)));
        }

        return new SolicitudAsignacion(dias, parametros.horaInicioJuego(), parametros.horaFinJuego(), ZONA_DE_NEGOCIO,
                esFechaFinal(fecha, parametros), canchas, partidos,
                asignacion.ocupacion(ligaId, desde, hasta));
    }

    /** VAL-006 con los mismos mensajes de {@link ValidadorConflictos}: baja y sanciones vigentes. */
    private static EquipoAsignable equipo(EquipoDeLiga equipo, List<SancionDeEquipo> sanciones) {
        List<String> causas = new ArrayList<>();
        if ("BAJA".equals(equipo.estado())) {
            causas.add(ValidadorConflictos.mensajeEquipoDeBaja(equipo));
        }
        sanciones.stream()
                .filter(sancion -> sancion.equipoId().equals(equipo.id()))
                .map(ValidadorConflictos::mensajeSancion)
                .forEach(causas::add);
        return new EquipoAsignable(equipo.id(), equipo.nombre(), causas.isEmpty() ? null : String.join(" ", causas));
    }

    /**
     * DEC-179, RN-049: la fecha es de las últimas {@code fechas_finales_unidad_deportiva}. Sin
     * {@code fechas_fase_regular}, el total sale del calendario vigente.
     */
    private boolean esFechaFinal(DatosFecha fecha, ParametrosAsignacion parametros) {
        Integer finales = parametros.fechasFinales();
        if (finales == null || finales <= 0) {
            return false;
        }
        int total = parametros.fechasFaseRegular() != null
                ? parametros.fechasFaseRegular()
                : asignacion.ultimaFechaDelCalendario(fecha.temporadaId());
        return fecha.numero() > total - finales;
    }
}
