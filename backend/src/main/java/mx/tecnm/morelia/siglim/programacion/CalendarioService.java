package mx.tecnm.morelia.siglim.programacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
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
import mx.tecnm.morelia.siglim.programacion.CalendarioGeneradoDto.Advertencia;
import mx.tecnm.morelia.siglim.programacion.CalendarioGeneradoDto.GrupoGenerado;
import mx.tecnm.morelia.siglim.programacion.CalendarioGeneradoDto.GrupoOmitido;
import mx.tecnm.morelia.siglim.programacion.CalendarioRepository.FilaCalendario;
import mx.tecnm.morelia.siglim.programacion.CalendarioRepository.GrupoCalendario;
import mx.tecnm.morelia.siglim.programacion.CalendarioRepository.JornadaManual;
import mx.tecnm.morelia.siglim.programacion.CalendarioRepository.ParametrosCalendario;
import mx.tecnm.morelia.siglim.programacion.CalendarioRepository.TemporadaCalendario;
import mx.tecnm.morelia.siglim.programacion.FechaRepository.FechaDeTemporada;
import mx.tecnm.morelia.siglim.programacion.PartidoDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendario de la temporada: genera por grupo los emparejamientos por el método del círculo y las
 * fechas de programación que les corresponden, y consulta el calendario vigente (H-32.2).
 *
 * <ul>
 *   <li><b>Permisos (ADR-0007).</b> Generar exige {@code programacion.jornada:EDITAR} y consultar
 *       {@code programacion.jornada:CONSULTAR}, ambos en el ámbito {@code LIGA}.</li>
 *   <li><b>Orden de validación de la generación.</b> permiso ({@code 403}) → cuerpo ({@code 400
 *       VALIDACION}) → temporada de la liga ({@code 404}) → temporada cerrada ({@code 409 RF-003}) →
 *       sin parámetros vigentes ({@code 409 RF-006}, DEC-190) → {@code grupoIds} ajenos a la temporada
 *       ({@code 422 REFERENCIA_INVALIDA}) → reemplazo sin motivo de al menos 10 caracteres ({@code 400
 *       RN-021}) → algún grupo con un emparejamiento vigente ligado a un partido o con una jornada
 *       ligada a una fecha ({@code 409 RN-020}, con la extensión {@code grupos}) → ningún grupo con al
 *       menos 2 equipos {@code ACTIVO} ({@code 422 VALIDACION}) → {@code fechaInicio} anterior al
 *       inicio de la temporada o enviada cuando alguna fecha ya tiene jornadas ({@code 422
 *       VALIDACION}) → escribir.</li>
 *   <li><b>Generación (DEC-174, DEC-185, DEC-190).</b> Por grupo, los equipos {@code ACTIVO} por
 *       {@code id} y las vueltas de {@code siglim.parametros_vigentes}, con
 *       {@link GeneradorCalendario}. Un grupo con menos de 2 equipos activos se omite y se informa en
 *       {@code gruposOmitidos} (DEC-187): la excepción del dominio nunca llega al cliente.</li>
 *   <li><b>Regeneración (DEC-189, RN-020, RN-021).</b> Si el grupo ya tiene calendario vigente, sus
 *       filas se marcan {@code vigente = false} con quién, cuándo y el motivo, y después se insertan
 *       las nuevas; nada se borra. Con partidos ligados o jornadas de fecha responde {@code 409 RN-020}
 *       sin escribir; {@code tg_emparejamientos_reemplazo} es la última garantía.</li>
 *   <li><b>Fechas (DEC-193).</b> Se crean las fechas 1…F que falten ({@code F} = la última fecha del
 *       calendario vigente de la temporada) con {@link DiasDeFecha}. Si ninguna fecha tiene jornadas y
 *       se envía {@code fechaInicio}, se recalculan los días de todas; sin {@code fechaInicio}, las
 *       existentes conservan sus días y las nuevas siguen semana a semana desde la última. Las fechas
 *       mayores que F se conservan y no se listan. No se crean jornadas: las crea la asignación de H-33
 *       (DEC-188).</li>
 *   <li><b>Advertencias (no bloquean).</b> El último día pasa del fin de la temporada; un grupo tiene
 *       jornadas manuales cuyo número coincide con una de sus fechas.</li>
 *   <li><b>Transacción y candados (RNF-004).</b> Todo en una transacción, con la temporada leída
 *       {@code FOR UPDATE}: dos generaciones de la misma temporada no se cruzan. Los emparejamientos
 *       de cada grupo y las fechas nuevas se insertan en una sola sentencia.</li>
 *   <li><b>Trazabilidad (RF-041, RF-093, DEC-192).</b> {@code creado_por} de emparejamientos y fechas
 *       es la sesión; auditoría {@code GENERAR_CALENDARIO} en M-07 sobre {@code temporadas}, con el
 *       motivo si lo hay y un detalle solo de identificadores y conteos. {@code generada_*} de la
 *       fecha queda para la asignación de H-33.</li>
 * </ul>
 *
 * <p>Refs: H-32.2, RF-034, RF-035, RF-037, RF-041, RN-012, RN-018, RN-020, RN-021, DEC-174, DEC-175,
 * DEC-177, DEC-186, DEC-187, DEC-188, DEC-189, DEC-190, DEC-191, DEC-192, DEC-193, ADR-0015
 */
@Service
public class CalendarioService {

    private static final String CONSULTAR = "programacion.jornada:CONSULTAR";
    private static final String EDITAR = "programacion.jornada:EDITAR";
    private static final String MODULO = "M-07";
    private static final String ENTIDAD = "temporadas";
    /** [PROPUESTA] El mismo mínimo de los demás motivos del módulo (RN-021). */
    private static final int MINIMO_MOTIVO = 10;
    private static final int EQUIPOS_MINIMOS = 2;
    static final String FECHAS_DESPUES_DEL_FIN = "FECHAS_DESPUES_DEL_FIN";
    static final String JORNADAS_MANUALES = "JORNADAS_MANUALES";

    private final CalendarioRepository calendario;
    private final FechaRepository fechas;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final UUID ligaId;

    CalendarioService(CalendarioRepository calendario, FechaRepository fechas, Autorizacion autorizacion,
            AuditoriaService auditoria, MotivoOperacion motivoOperacion, SiglimPropiedades propiedades) {
        this.calendario = calendario;
        this.fechas = fechas;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.ligaId = propiedades.ligaId();
    }

    // ── Generación ──────────────────────────────────────────────────────────────────────────────

    /**
     * Genera o regenera el calendario de los grupos de la temporada (RF-034, DEC-174, DEC-189).
     *
     * @param peticion cuerpo opcional; nulo equivale a {@code {}}
     * @return grupos generados y omitidos, fechas y advertencias
     */
    @Transactional
    public CalendarioGeneradoDto generar(UUID temporadaId, GenerarCalendarioRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        GenerarCalendarioRequest cuerpo = peticion == null ? new GenerarCalendarioRequest(null, null, null) : peticion;
        Set<UUID> pedidos = gruposPedidos(cuerpo.grupoIds());
        String motivo = cuerpo.motivo() == null || cuerpo.motivo().isBlank() ? null : cuerpo.motivo().strip();

        TemporadaCalendario temporada = calendario.bloquearTemporada(ligaId, temporadaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Temporada " + temporadaId));
        if (temporada.cerrada()) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + temporada.nombre() + "» está cerrada: su calendario es de solo lectura.");
        }
        ParametrosCalendario parametros = calendario.parametrosVigentes(temporadaId)
                .orElseThrow(() -> ReglaNegocioException.conflicto("RF-006",
                        "La temporada «" + temporada.nombre() + "» no tiene parámetros deportivos; regístralos "
                                + "para fijar las vueltas antes de generar el calendario."));

        List<GrupoCalendario> elegidos = gruposElegidos(calendario.listarGrupos(temporadaId), pedidos);
        List<GrupoCalendario> conCalendario = elegidos.stream().filter(GrupoCalendario::conCalendario).toList();
        if (!conCalendario.isEmpty() && (motivo == null || motivo.length() < MINIMO_MOTIVO)) {
            throw ReglaNegocioException.invalida("RN-021",
                    "Ya hay calendario en " + nombres(conCalendario) + "; escribe un motivo de al menos "
                            + MINIMO_MOTIVO + " caracteres para reemplazarlo.");
        }
        exigirSinProgramacion(elegidos);

        Map<GrupoCalendario, List<UUID>> generables = new LinkedHashMap<>();
        List<GrupoOmitido> omitidos = new ArrayList<>();
        for (GrupoCalendario grupo : elegidos) {
            List<UUID> equipos = calendario.equiposActivos(grupo.id());
            if (equipos.size() < EQUIPOS_MINIMOS) {
                omitidos.add(new GrupoOmitido(grupo.id(), grupo.nombre(), equipos.size(),
                        "Tiene " + equipos.size() + (equipos.size() == 1 ? " equipo activo" : " equipos activos")
                                + "; se necesitan al menos " + EQUIPOS_MINIMOS + " para generar su calendario."));
            } else {
                generables.put(grupo, equipos);
            }
        }
        if (generables.isEmpty()) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.VALIDACION,
                    "Ningún grupo tiene al menos " + EQUIPOS_MINIMOS + " equipos activos; registra o activa "
                            + "equipos antes de generar el calendario.");
        }
        boolean conJornadas = fechas.algunaFechaConJornadas(temporadaId);
        exigirFechaInicioValida(cuerpo.fechaInicio(), temporada, conJornadas);

        // ── Escritura ──
        UUID perfil = UsuarioActual.idRequerido();
        if (motivo != null) {
            motivoOperacion.fijar(motivo);
        }
        List<GrupoGenerado> generados = new ArrayList<>();
        int emparejamientos = 0;
        for (Map.Entry<GrupoCalendario, List<UUID>> entrada : generables.entrySet()) {
            GrupoCalendario grupo = entrada.getKey();
            List<Emparejamiento> nuevo = GeneradorCalendario.generar(entrada.getValue(), parametros.vueltas());
            if (grupo.conCalendario()) {
                calendario.reemplazar(grupo.id(), perfil, motivo);
            }
            calendario.insertar(grupo.id(), nuevo, perfil);
            emparejamientos += nuevo.size();
            int fechasDelGrupo = nuevo.stream().mapToInt(Emparejamiento::numeroFecha).max().orElse(0);
            generados.add(new GrupoGenerado(grupo.id(), grupo.nombre(), entrada.getValue().size(), fechasDelGrupo,
                    grupo.conCalendario()));
        }

        int ultima = calendario.ultimaFechaVigente(temporadaId);
        ResultadoFechas resultado = programarFechas(temporada, cuerpo.fechaInicio(), conJornadas, ultima, perfil);
        List<Advertencia> advertencias = new ArrayList<>();
        advertenciaDeFin(temporada, ultima).ifPresent(advertencias::add);
        advertencias.addAll(advertenciasDeJornadasManuales(generados));

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("versionParametros", parametros.version());
        detalle.put("vueltas", parametros.vueltas());
        if (resultado.inicio() != null) {
            detalle.put("fechaInicio", resultado.inicio().toString());
        }
        detalle.put("grupos", generados.stream().map(g -> g.grupoId().toString()).toList());
        detalle.put("reemplazados", generados.stream().filter(GrupoGenerado::reemplazado)
                .map(g -> g.grupoId().toString()).toList());
        detalle.put("omitidos", omitidos.stream().map(g -> g.grupoId().toString()).toList());
        detalle.put("emparejamientos", emparejamientos);
        detalle.put("totalFechas", ultima);
        detalle.put("fechasCreadas", resultado.creadas());
        detalle.put("fechasRecalculadas", resultado.recalculadas());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "GENERAR_CALENDARIO", ENTIDAD, temporadaId.toString(),
                motivo, detalle));

        return new CalendarioGeneradoDto(parametros.version(), parametros.vueltas(), ultima, resultado.creadas(),
                generados, omitidos, advertencias);
    }

    /** Grupos del cuerpo sin repetir; una lista vacía o con elementos nulos responde 400. */
    private static Set<UUID> gruposPedidos(List<UUID> grupoIds) {
        if (grupoIds == null) {
            return null;
        }
        if (grupoIds.isEmpty()) {
            throw invalida("Indica al menos un grupo en grupoIds, u omítelo para generar todos los de la temporada.");
        }
        if (grupoIds.stream().anyMatch(Objects::isNull)) {
            throw invalida("La lista grupoIds tiene un elemento vacío.");
        }
        return new LinkedHashSet<>(grupoIds);
    }

    /** Todos los grupos o los pedidos, en el orden de la categoría; un grupo ajeno responde 422. */
    private static List<GrupoCalendario> gruposElegidos(List<GrupoCalendario> todos, Set<UUID> pedidos) {
        if (pedidos == null) {
            return todos;
        }
        Set<UUID> deLaTemporada = todos.stream().map(GrupoCalendario::id).collect(Collectors.toSet());
        if (!deLaTemporada.containsAll(pedidos)) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                    "Alguno de los grupos indicados no pertenece a la temporada.");
        }
        return todos.stream().filter(grupo -> pedidos.contains(grupo.id())).toList();
    }

    /** RN-020: lo que ya tiene partidos o jornadas de fecha no se regenera. */
    private void exigirSinProgramacion(List<GrupoCalendario> elegidos) {
        Set<UUID> conProgramacion = Set.copyOf(calendario.gruposConProgramacion(
                elegidos.stream().map(GrupoCalendario::id).toList()));
        if (conProgramacion.isEmpty()) {
            return;
        }
        List<GrupoCalendario> bloqueados = elegidos.stream().filter(g -> conProgramacion.contains(g.id())).toList();
        throw ReglaNegocioException.conflicto("RN-020",
                        "El calendario de " + nombres(bloqueados) + " ya tiene partidos o jornadas programados y "
                                + "no se puede regenerar; quita esos grupos de grupoIds.")
                .con("grupos", bloqueados.stream()
                        .map(g -> Map.of("grupoId", g.id().toString(), "nombre", g.nombre()))
                        .toList());
    }

    private static void exigirFechaInicioValida(LocalDate fechaInicio, TemporadaCalendario temporada,
            boolean conJornadas) {
        if (fechaInicio == null) {
            return;
        }
        if (conJornadas) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.VALIDACION,
                    "Ya hay jornadas en fechas de la temporada: sus días no se recalculan y las fechas nuevas "
                            + "siguen semana a semana desde la última. Omite fechaInicio.");
        }
        if (fechaInicio.isBefore(temporada.fechaInicio())) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.VALIDACION,
                    "La fecha de inicio " + fechaInicio + " es anterior al inicio de la temporada ("
                            + temporada.fechaInicio() + ").");
        }
    }

    // ── Fechas (DEC-193) ────────────────────────────────────────────────────────────────────────

    private record ResultadoFechas(LocalDate inicio, int creadas, int recalculadas) {
    }

    /**
     * Crea las fechas 1…{@code ultima} que falten y, si corresponde, recalcula los días de todas.
     *
     * @return el inicio usado para contar (nulo si las nuevas siguieron a la última) y los conteos
     */
    private ResultadoFechas programarFechas(TemporadaCalendario temporada, LocalDate fechaInicio,
            boolean conJornadas, int ultima, UUID perfil) {
        List<FechaDeTemporada> existentes = fechas.listarDeTemporada(temporada.id());
        boolean recalcular = fechaInicio != null && !conJornadas;
        LocalDate inicio = fechaInicio == null ? temporada.fechaInicio() : fechaInicio;
        int recalculadas = 0;
        if (recalcular) {
            for (FechaDeTemporada fecha : existentes) {
                List<LocalDate> dias = DiasDeFecha.dias(inicio, fecha.numero());
                if (!dias.equals(fecha.dias())) {
                    fechas.actualizarDias(fecha.id(), dias);
                    recalculadas++;
                }
            }
        }

        Set<Integer> numeros = existentes.stream().map(FechaDeTemporada::numero).collect(Collectors.toSet());
        FechaDeTemporada ultimaExistente = existentes.stream()
                .max(Comparator.comparingInt(FechaDeTemporada::numero)).orElse(null);
        boolean siguenALaUltima = !recalcular && ultimaExistente != null;
        SortedMap<Integer, List<LocalDate>> nuevas = new TreeMap<>();
        for (int numero = 1; numero <= ultima; numero++) {
            if (numeros.contains(numero)) {
                continue;
            }
            if (siguenALaUltima && numero > ultimaExistente.numero()) {
                LocalDate siguiente = ultimaExistente.dias().getLast().plusDays(1);
                nuevas.put(numero, DiasDeFecha.dias(siguiente, numero - ultimaExistente.numero()));
            } else {
                nuevas.put(numero, DiasDeFecha.dias(inicio, numero));
            }
        }
        fechas.insertarFechas(temporada.id(), nuevas, perfil);
        return new ResultadoFechas(siguenALaUltima ? null : inicio, nuevas.size(), recalculadas);
    }

    /** El último día de las fechas 1…F después del fin de la temporada (no bloquea). */
    private Optional<Advertencia> advertenciaDeFin(TemporadaCalendario temporada, int ultima) {
        if (temporada.fechaFin() == null) {
            return Optional.empty();
        }
        return fechas.listarDeTemporada(temporada.id()).stream()
                .filter(fecha -> fecha.numero() <= ultima)
                .map(fecha -> fecha.dias().getLast())
                .max(Comparator.naturalOrder())
                .filter(dia -> dia.isAfter(temporada.fechaFin()))
                .map(dia -> new Advertencia(FECHAS_DESPUES_DEL_FIN, null,
                        "Las " + ultima + " fechas del calendario terminan el " + dia + ", después del fin de la "
                                + "temporada (" + temporada.fechaFin() + "). Revisa los días de las fechas o el fin "
                                + "de la temporada."));
    }

    /** Jornadas manuales con el número de una fecha del calendario del grupo (no bloquea). */
    private List<Advertencia> advertenciasDeJornadasManuales(List<GrupoGenerado> generados) {
        Map<UUID, GrupoGenerado> porId = new LinkedHashMap<>();
        generados.forEach(grupo -> porId.put(grupo.grupoId(), grupo));
        Map<UUID, List<Integer>> coincidencias = new LinkedHashMap<>();
        for (JornadaManual jornada : calendario.jornadasManuales(porId.keySet())) {
            if (jornada.numero() <= porId.get(jornada.grupoId()).fechas()) {
                coincidencias.computeIfAbsent(jornada.grupoId(), id -> new ArrayList<>()).add(jornada.numero());
            }
        }
        List<Advertencia> advertencias = new ArrayList<>();
        coincidencias.forEach((grupoId, numerosJornada) -> advertencias.add(new Advertencia(JORNADAS_MANUALES, grupoId,
                "El grupo «" + porId.get(grupoId).nombre() + "» tiene jornadas manuales con número "
                        + numerosJornada.stream().map(String::valueOf).collect(Collectors.joining(", "))
                        + " que coinciden con fechas del calendario; revisa que no se dupliquen partidos.")));
        return advertencias;
    }

    // ── Consulta ────────────────────────────────────────────────────────────────────────────────

    /**
     * Calendario vigente de la temporada por grupo y fecha (RF-034, RF-037). Todos los grupos, con
     * {@code generado = false} los que aún no tienen calendario.
     *
     * @throws RecursoNoEncontradoException si la temporada no existe en la liga
     */
    @Transactional(readOnly = true)
    public CalendarioDto consultar(UUID temporadaId) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        calendario.buscarTemporada(ligaId, temporadaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Temporada " + temporadaId));
        Map<UUID, SortedMap<Integer, List<FilaCalendario>>> porGrupo = new LinkedHashMap<>();
        for (FilaCalendario fila : calendario.listarVigentes(temporadaId)) {
            porGrupo.computeIfAbsent(fila.grupoId(), id -> new TreeMap<>())
                    .computeIfAbsent(fila.numeroFecha(), numero -> new ArrayList<>())
                    .add(fila);
        }
        List<CalendarioDto.GrupoDto> grupos = new ArrayList<>();
        for (GrupoCalendario grupo : calendario.listarGrupos(temporadaId)) {
            SortedMap<Integer, List<FilaCalendario>> deGrupo = porGrupo.getOrDefault(grupo.id(), new TreeMap<>());
            List<CalendarioDto.FechaDto> fechasDelGrupo = new ArrayList<>();
            deGrupo.forEach((numero, filas) -> fechasDelGrupo.add(aFecha(numero, filas)));
            grupos.add(new CalendarioDto.GrupoDto(grupo.id(), grupo.nombre(),
                    new ReferenciaDto(grupo.categoriaId(), grupo.categoriaNombre()), !deGrupo.isEmpty(),
                    fechasDelGrupo));
        }
        return new CalendarioDto(temporadaId, grupos);
    }

    private static CalendarioDto.FechaDto aFecha(int numero, List<FilaCalendario> filas) {
        List<CalendarioDto.PartidoDto> partidos = new ArrayList<>();
        CalendarioDto.EquipoDto descansa = null;
        for (FilaCalendario fila : filas) {
            if (fila.visitante() == null) {
                descansa = fila.local();
            } else {
                partidos.add(new CalendarioDto.PartidoDto(fila.id(), fila.local(), fila.visitante(), fila.partidoId()));
            }
        }
        return new CalendarioDto.FechaDto(numero, filas.getFirst().vuelta(), partidos, descansa);
    }

    // ── Auxiliares ──────────────────────────────────────────────────────────────────────────────

    private static String nombres(List<GrupoCalendario> grupos) {
        return grupos.stream()
                .map(grupo -> "«" + grupo.categoriaNombre() + " – " + grupo.nombre() + "»")
                .collect(Collectors.joining(", "));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
