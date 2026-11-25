package mx.tecnm.morelia.siglim.programacion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import mx.tecnm.morelia.siglim.programacion.FechaDto.Bloque;
import mx.tecnm.morelia.siglim.programacion.FechaDto.Descanso;
import mx.tecnm.morelia.siglim.programacion.FechaDto.FilaPartido;
import mx.tecnm.morelia.siglim.programacion.FechaDto.SinAsignar;
import mx.tecnm.morelia.siglim.programacion.FechaRepository.DatosFecha;
import mx.tecnm.morelia.siglim.programacion.FechaRepository.EmparejamientoDeFecha;
import mx.tecnm.morelia.siglim.programacion.FechaRepository.GrupoDeFecha;
import mx.tecnm.morelia.siglim.programacion.FechaRepository.PartidoDeFecha;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosJornada;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosPartido;
import mx.tecnm.morelia.siglim.programacion.PartidoDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.programacion.VerificacionFechaDto.Aviso;
import mx.tecnm.morelia.siglim.programacion.eventos.JornadaPublicada;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vista por fecha, verificación, envío y dictamen de la fecha completa (H-35.1, DEC-175).
 *
 * <ul>
 *   <li><b>Consulta (F-22, F-23).</b> Con {@code programacion.jornada:CONSULTAR} en el ámbito
 *       {@code LIGA}. El detalle arma un bloque por «Categoría – Grupo» con los partidos vigentes
 *       (cancha, horario, árbitro central e insignias «Automático», «Ajustado» y «Localía»), los
 *       descansos y los emparejamientos sin asignar con su causa (DEC-180). Los partidos
 *       {@code CANCELADO} no aparecen (DEC-181).</li>
 *   <li><b>Verificación sin guardar.</b> Revisa los partidos de todas las jornadas con la misma
 *       regla del alta y el ajuste ({@link ValidadorConflictos}); devuelve como advertencias la
 *       localía, los partidos sin árbitro y los emparejamientos sin asignar.</li>
 *   <li><b>Envío (RF-121, RF-090).</b> Con {@code programacion.jornada:ENVIAR}: todas las jornadas
 *       pasan juntas de {@code BORRADOR} a {@code REVISION} en una transacción, solo si ninguna tiene
 *       conflictos duros ({@code 409 RF-039} con la lista). Los sin asignar y los sin árbitro no
 *       bloquean.</li>
 *   <li><b>Dictamen (RF-121).</b> Con {@code programacion.jornada:APROBAR}: el Presidente publica o
 *       devuelve todas las jornadas juntas. Devolver exige observaciones ({@code 400 RF-121}). Al
 *       publicar, cada jornada emite {@link JornadaPublicada} en la misma transacción (cobros, B-30)
 *       y aparece en {@code v_publica_programacion}.</li>
 *   <li><b>Separación de funciones (RF-121, RN-053).</b> No dictamina quien creó, generó o envió la
 *       fecha o alguna de sus jornadas, ni quien además tiene {@code programacion.jornada:EDITAR}
 *       ({@code 403 RF-121}). {@code ck_jornadas_aprobacion} es la última garantía.</li>
 *   <li><b>Candados.</b> La fila de la fecha y luego sus jornadas, en orden de {@code id}, se leen
 *       con {@code FOR UPDATE}: un alta, un ajuste o la asignación automática no se cruzan con el
 *       envío o el dictamen.</li>
 *   <li><b>Auditoría (RF-093).</b> {@code ENVIAR_FECHA_A_APROBACION}, {@code APROBAR_FECHA} y
 *       {@code DEVOLVER_FECHA} sobre {@code fechas_programacion}, con los identificadores de las
 *       jornadas; cada jornada deja además su paso en {@code historial_publicacion} por el trigger.</li>
 * </ul>
 *
 * <p>Refs: H-35.1, RF-023, RF-033, RF-037, RF-039, RF-040, RF-042, RF-090, RF-093, RF-121, RN-053,
 * UI-009, DD-008, DEC-175, DEC-180, DEC-181, DEC-182, ADR-0007
 */
@Service
public class FechaService {

    private static final String CONSULTAR = "programacion.jornada:CONSULTAR";
    private static final String EDITAR = "programacion.jornada:EDITAR";
    private static final String ENVIAR = "programacion.jornada:ENVIAR";
    private static final String APROBAR = "programacion.jornada:APROBAR";
    private static final String MODULO = "M-07";
    private static final String ENTIDAD = "fechas_programacion";
    private static final int TAMANO_MAXIMO = 100;
    /** [PROPUESTA] El mismo mínimo de las observaciones de la jornada (H-13.1). */
    private static final int MINIMO_MOTIVO = 10;
    /** Código de la advertencia de partido sin árbitro central (DEC-182): designar árbitros. */
    static final String CODIGO_SIN_ARBITRO = "RF-023";
    /**
     * [PROPUESTA] Código de un emparejamiento que la asignación automática todavía no procesó: aún no
     * tiene la causa de DEC-180, solo falta programarlo (RF-033).
     */
    static final String CODIGO_PENDIENTE = "RF-033";

    private final FechaRepository fechas;
    private final JornadaRepository jornadas;
    private final ValidadorConflictos validador;
    private final ValidadorLocalia localia;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final ApplicationEventPublisher eventos;
    private final UUID ligaId;

    FechaService(FechaRepository fechas, JornadaRepository jornadas, ValidadorConflictos validador,
            ValidadorLocalia localia, Autorizacion autorizacion, AuditoriaService auditoria,
            MotivoOperacion motivoOperacion, ApplicationEventPublisher eventos, SiglimPropiedades propiedades) {
        this.fechas = fechas;
        this.jornadas = jornadas;
        this.validador = validador;
        this.localia = localia;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.eventos = eventos;
        this.ligaId = propiedades.ligaId();
    }

    // ── Consulta ────────────────────────────────────────────────────────────────────────────────

    /**
     * Fechas de la temporada por número, con su estado derivado y sus conteos (DEC-175).
     *
     * @throws ReglaNegocioException 400 {@code VALIDACION} sin {@code temporadaId} o con paginación inválida
     * @throws RecursoNoEncontradoException si la temporada no existe en la liga
     */
    @Transactional(readOnly = true)
    public Pagina<FechaDto> listar(UUID temporadaId, int pagina, int tamano) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        if (temporadaId == null) {
            throw invalida("Indica la temporada con el filtro temporadaId.");
        }
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw invalida("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        if (!fechas.existeTemporada(ligaId, temporadaId)) {
            throw new RecursoNoEncontradoException("Temporada " + temporadaId);
        }
        long desplazamiento = (long) pagina * tamano;
        List<FechaDto> contenido = fechas.listar(ligaId, temporadaId, tamano, desplazamiento).stream()
                .map(datos -> aDto(datos, List.of(), null))
                .toList();
        return Pagina.de(contenido, pagina, tamano, fechas.contar(ligaId, temporadaId));
    }

    /** Vista por fecha de F-22 y F-23: bloques por categoría y grupo (UI-009, DEC-175). */
    @Transactional(readOnly = true)
    public FechaDto consultar(UUID id) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        return detalle(id);
    }

    /**
     * Revisa todas las jornadas de la fecha sin escribir nada (RF-039, DEC-175).
     *
     * @return los conflictos duros que impiden el envío y las advertencias que no lo impiden
     * @throws RecursoNoEncontradoException si la fecha no existe en la liga
     */
    @Transactional(readOnly = true)
    public VerificacionFechaDto verificar(UUID id) {
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        DatosFecha fecha = fechas.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
        List<DatosJornada> deLaFecha = jornadas.listarJornadasDeFecha(id);
        List<Aviso> advertencias = new ArrayList<>();
        for (DatosJornada jornada : deLaFecha) {
            advertencias.addAll(deJornada(jornada.id(),
                    JornadaService.advertenciasDe(localia, jornadas.listarPartidos(jornada.id()))));
        }
        for (PartidoDeFecha partido : fechas.listarPartidos(id)) {
            if (partido.arbitroId() == null) {
                advertencias.add(new Aviso(partido.jornadaId(), partido.id(), partido.emparejamientoId(),
                        CODIGO_SIN_ARBITRO, "Sin árbitro central: " + partido.equipoLocalNombre() + " vs "
                                + partido.equipoVisitanteNombre() + ". La Comisión de Arbitraje lo designa en F-24."));
            }
        }
        Map<UUID, UUID> jornadaDeGrupo = new LinkedHashMap<>();
        deLaFecha.forEach(jornada -> jornadaDeGrupo.put(jornada.grupoId(), jornada.id()));
        for (EmparejamientoDeFecha emparejamiento : fechas.listarSinPartido(fecha.temporadaId(), fecha.numero())) {
            if (!emparejamiento.esDescanso()) {
                advertencias.add(new Aviso(jornadaDeGrupo.get(emparejamiento.grupoId()), null, emparejamiento.id(),
                        codigoSinAsignar(emparejamiento), mensajeSinAsignar(emparejamiento)));
            }
        }
        return new VerificacionFechaDto(conflictosDe(deLaFecha), advertencias);
    }

    // ── Publicación ─────────────────────────────────────────────────────────────────────────────

    /**
     * Envía la fecha completa a aprobación del Presidente (RF-121, RF-090, DEC-175).
     *
     * <p>Orden: permiso → fecha existe ({@code 404}) → con jornadas ({@code 409 RF-121}) → todas en
     * borrador ({@code 409 RF-090}) → al menos un partido vigente ({@code 409 RF-121}) → ningún
     * conflicto duro en ninguna jornada ({@code 409 RF-039} con la extensión {@code conflictos}).
     * Todas las jornadas pasan a {@code REVISION} en la misma transacción.
     */
    @Transactional
    public FechaDto enviarAAprobacion(UUID id) {
        autorizacion.exigir(ENVIAR, ObjetivoAutorizacion.deLiga());
        List<DatosJornada> deLaFecha = bloquear(id);
        DatosFecha fecha = buscar(id);
        if (deLaFecha.isEmpty()) {
            throw ReglaNegocioException.conflicto("RF-121",
                    "La fecha " + fecha.numero() + " no tiene jornadas; asigna sus partidos antes de enviarla.");
        }
        exigirEstado(fecha, deLaFecha, EstadoJornada.BORRADOR, "Solo una fecha en borrador se envía a aprobación");
        if (fecha.partidos() == 0) {
            // [PROPUESTA] Igual que la jornada (H-13.1): una fecha sin partidos no se somete a aprobación.
            throw ReglaNegocioException.conflicto("RF-121",
                    "La fecha " + fecha.numero() + " no tiene partidos programados; asigna al menos uno "
                            + "antes de enviarla a aprobación.");
        }
        List<Aviso> conflictos = conflictosDe(deLaFecha);
        if (!conflictos.isEmpty()) {
            throw ReglaNegocioException.conflicto("RF-039",
                    "La fecha tiene " + conflictos.size() + (conflictos.size() == 1 ? " conflicto" : " conflictos")
                            + " sin resolver: " + conflictos.getFirst().mensaje())
                    .con("conflictos", conflictos);
        }

        UUID perfil = UsuarioActual.idRequerido();
        for (DatosJornada jornada : deLaFecha) {
            if (jornadas.enviarAAprobacion(jornada.id(), perfil) == 0) {
                throw cambioConcurrente();
            }
        }

        Map<String, Object> detalle = detalleDe(deLaFecha, EstadoJornada.BORRADOR, EstadoJornada.REVISION);
        detalle.put("partidos", fecha.partidos());
        detalle.put("sinAsignar", fecha.sinAsignar());
        auditar("ENVIAR_FECHA_A_APROBACION", id, null, detalle);
        return detalle(id);
    }

    /**
     * Aplica el dictamen del Presidente a la fecha completa (RF-121, CU-007, DEC-175).
     *
     * <p>Orden: permiso → fecha existe ({@code 404}) → con jornadas y todas en revisión
     * ({@code 409 RF-090}) → decisión presente ({@code 400}) → separación de funciones
     * ({@code 403 RF-121}) → observaciones de la devolución ({@code 400 RF-121}).
     */
    @Transactional
    public FechaDto resolver(UUID id, ResolverFechaRequest peticion) {
        autorizacion.exigir(APROBAR, ObjetivoAutorizacion.deLiga());
        List<DatosJornada> deLaFecha = bloquear(id);
        DatosFecha fecha = buscar(id);
        if (deLaFecha.isEmpty()) {
            throw ReglaNegocioException.conflicto("RF-090",
                    "La fecha " + fecha.numero() + " no tiene jornadas en revisión.");
        }
        exigirEstado(fecha, deLaFecha, EstadoJornada.REVISION, "Solo una fecha en revisión se aprueba o se devuelve");
        DecisionJornada decision = peticion == null ? null : peticion.decision();
        if (decision == null) {
            throw invalida("Indica si la fecha se publica o se devuelve.");
        }
        UUID perfil = UsuarioActual.idRequerido();
        exigirSeparacionDeFunciones(fecha, deLaFecha, perfil);

        if (decision == DecisionJornada.PUBLICADO) {
            String nota = textoOpcional(peticion.observaciones());
            if (nota != null) {
                motivoOperacion.fijar(nota);
            }
            for (DatosJornada jornada : deLaFecha) {
                if (jornadas.resolver(jornada.id(), EstadoJornada.PUBLICADO, perfil, nota) == 0) {
                    throw cambioConcurrente();
                }
            }
            auditar("APROBAR_FECHA", id, null, detalleDe(deLaFecha, EstadoJornada.REVISION, EstadoJornada.PUBLICADO));
            for (DatosJornada jornada : deLaFecha) {
                eventos.publishEvent(new JornadaPublicada(ligaId, jornada.id(), perfil));
            }
        } else {
            String motivo = observacionesValidas(peticion.observaciones());
            motivoOperacion.fijar(motivo);
            for (DatosJornada jornada : deLaFecha) {
                if (jornadas.resolver(jornada.id(), EstadoJornada.BORRADOR, null, motivo) == 0) {
                    throw cambioConcurrente();
                }
            }
            auditar("DEVOLVER_FECHA", id, motivo,
                    detalleDe(deLaFecha, EstadoJornada.REVISION, EstadoJornada.BORRADOR));
        }
        return detalle(id);
    }

    /**
     * Quien programa no aprueba (RF-121, Apéndice E): ni quien creó o generó la fecha, ni quien creó
     * o envió alguna de sus jornadas, ni quien además puede editar la programación.
     */
    private void exigirSeparacionDeFunciones(DatosFecha fecha, List<DatosJornada> deLaFecha, UUID perfil) {
        boolean programo = perfil.equals(fecha.creadoPor()) || perfil.equals(fecha.generadaPor())
                || deLaFecha.stream().anyMatch(j -> perfil.equals(j.enviadaPor()) || perfil.equals(j.creadoPor()));
        if (programo) {
            throw ReglaNegocioException.prohibida("RF-121",
                    "Quien programó o envió la fecha no puede dictaminarla: la aprobación corresponde al Presidente.");
        }
        if (autorizacion.permite(EDITAR, ObjetivoAutorizacion.deLiga())) {
            throw ReglaNegocioException.prohibida("RF-121",
                    "Tu cuenta también puede editar la programación; quien programa no puede aprobar la fecha.");
        }
    }

    /** Todas las jornadas deben estar en el estado de origen; si no, nombra la primera que no lo está. */
    private static void exigirEstado(DatosFecha fecha, List<DatosJornada> deLaFecha, EstadoJornada esperado,
            String regla) {
        for (DatosJornada jornada : deLaFecha) {
            if (jornada.estadoPublicacion() != esperado) {
                throw ReglaNegocioException.conflicto("RF-090",
                        regla + "; en la fecha " + fecha.numero() + " la jornada " + jornada.numero()
                                + " está " + jornada.estadoPublicacion().texto() + ".");
            }
        }
    }

    // ── Reglas y lecturas ───────────────────────────────────────────────────────────────────────

    /** Bloquea la fecha y después sus jornadas, siempre en ese orden. */
    private List<DatosJornada> bloquear(UUID id) {
        if (!fechas.bloquear(ligaId, id)) {
            throw noExiste(id);
        }
        return jornadas.bloquearJornadasDeFecha(id);
    }

    private DatosFecha buscar(UUID id) {
        return fechas.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    /** Conflictos duros de todas las jornadas con la regla del alta y el ajuste (RF-039). */
    private List<Aviso> conflictosDe(List<DatosJornada> deLaFecha) {
        List<Aviso> conflictos = new ArrayList<>();
        for (DatosJornada jornada : deLaFecha) {
            List<DatosPartido> partidos = jornadas.listarPartidos(jornada.id());
            conflictos.addAll(deJornada(jornada.id(), JornadaService.conflictosDe(validador, partidos)));
        }
        return conflictos;
    }

    private static List<Aviso> deJornada(UUID jornadaId, List<VerificacionJornadaDto.Aviso> avisos) {
        return avisos.stream()
                .map(aviso -> new Aviso(jornadaId, aviso.partidoId(), null, aviso.codigo(), aviso.mensaje()))
                .toList();
    }

    private static String codigoSinAsignar(EmparejamientoDeFecha emparejamiento) {
        return emparejamiento.codigoNoAsignado() == null ? CODIGO_PENDIENTE : emparejamiento.codigoNoAsignado();
    }

    private static String mensajeSinAsignar(EmparejamientoDeFecha emparejamiento) {
        String partido = emparejamiento.equipoLocalNombre() + " vs " + emparejamiento.equipoVisitanteNombre();
        return emparejamiento.causaNoAsignado() == null
                ? "Sin asignar: " + partido + ". Falta programar cancha y horario."
                : "Sin asignar: " + partido + " — " + emparejamiento.causaNoAsignado();
    }

    private static String observacionesValidas(String observaciones) {
        String texto = observaciones == null ? "" : observaciones.strip();
        if (texto.length() < MINIMO_MOTIVO) {
            throw ReglaNegocioException.invalida("RF-121",
                    "Escribe observaciones de al menos " + MINIMO_MOTIVO
                            + " caracteres para devolver la fecha al Programador.");
        }
        return texto;
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    /** El {@code UPDATE} condicionado no encontró el estado leído con candado: no debería ocurrir. */
    private static ReglaNegocioException cambioConcurrente() {
        return ReglaNegocioException.conflicto("RF-090",
                "La fecha cambió de estado mientras se procesaba; vuelve a consultarla.");
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Fecha " + id);
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }

    // ── Representación ──────────────────────────────────────────────────────────────────────────

    private FechaDto detalle(UUID id) {
        DatosFecha fecha = buscar(id);
        List<DatosJornada> deLaFecha = jornadas.listarJornadasDeFecha(id);
        List<PartidoDeFecha> partidos = fechas.listarPartidos(id);
        List<EmparejamientoDeFecha> sinPartido = fechas.listarSinPartido(fecha.temporadaId(), fecha.numero());

        List<Bloque> bloques = new ArrayList<>();
        for (GrupoDeFecha grupo : fechas.listarGrupos(id, fecha.temporadaId(), fecha.numero())) {
            List<FilaPartido> filas = partidos.stream()
                    .filter(partido -> partido.grupoId().equals(grupo.grupoId()))
                    .map(FechaService::aFila)
                    .toList();
            List<Descanso> descansos = new ArrayList<>();
            List<SinAsignar> pendientes = new ArrayList<>();
            for (EmparejamientoDeFecha emparejamiento : sinPartido) {
                if (!emparejamiento.grupoId().equals(grupo.grupoId())) {
                    continue;
                }
                ReferenciaDto local = new ReferenciaDto(emparejamiento.equipoLocalId(),
                        emparejamiento.equipoLocalNombre());
                if (emparejamiento.esDescanso()) {
                    descansos.add(new Descanso(emparejamiento.id(), local));
                } else {
                    pendientes.add(new SinAsignar(emparejamiento.id(), local,
                            new ReferenciaDto(emparejamiento.equipoVisitanteId(), emparejamiento.equipoVisitanteNombre()),
                            emparejamiento.codigoNoAsignado(), emparejamiento.causaNoAsignado()));
                }
            }
            bloques.add(new Bloque(new ReferenciaDto(grupo.categoriaId(), grupo.categoriaNombre()),
                    new ReferenciaDto(grupo.grupoId(), grupo.grupoNombre()), grupo.diasJuego(),
                    grupo.jornadaId(), grupo.estadoJornada(), filas, descansos, pendientes));
        }
        return aDto(fecha, deLaFecha, bloques);
    }

    /**
     * Los datos de publicación salen de la jornada modificada al último: el envío y el dictamen las
     * cambian todas juntas, con el mismo perfil y las mismas observaciones.
     */
    private static FechaDto aDto(DatosFecha fecha, List<DatosJornada> deLaFecha, List<Bloque> bloques) {
        DatosJornada referencia = deLaFecha.stream()
                .max(Comparator.comparing(DatosJornada::actualizadoEn))
                .orElse(null);
        return new FechaDto(fecha.id(), fecha.temporadaId(), fecha.numero(), fecha.dias(), fecha.estado(),
                fecha.jornadas(), fecha.partidos(), fecha.sinAsignar(), fecha.generadaEn(),
                referencia == null ? null : referencia.enviadaPor(),
                referencia == null ? null : referencia.enviadaEn(),
                referencia == null ? null : referencia.aprobadaPor(),
                referencia == null ? null : referencia.aprobadaEn(),
                referencia == null ? null : referencia.publicadaEn(),
                referencia == null ? null : referencia.observaciones(),
                bloques);
    }

    private static FilaPartido aFila(PartidoDeFecha partido) {
        return new FilaPartido(partido.id(), partido.jornadaId(), partido.emparejamientoId(),
                new ReferenciaDto(partido.equipoLocalId(), partido.equipoLocalNombre()),
                new ReferenciaDto(partido.equipoVisitanteId(), partido.equipoVisitanteNombre()),
                new ReferenciaDto(partido.canchaId(), partido.canchaNombre()),
                partido.inicio(), partido.finBloque(), partido.estado(),
                partido.arbitroId() == null ? null : new ReferenciaDto(partido.arbitroId(), partido.arbitroNombre()),
                partido.designacionAutomatica(), partido.asignadoAutomaticamente(), partido.ajustado(),
                partido.motivoLocalia());
    }

    // ── Auditoría (RF-093): solo identificadores ───────────────────────────────────────────────

    private static Map<String, Object> detalleDe(List<DatosJornada> deLaFecha, EstadoJornada anterior,
            EstadoJornada nuevo) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", anterior.name());
        detalle.put("estado", nuevo.name());
        detalle.put("jornadas", deLaFecha.stream().map(jornada -> jornada.id().toString()).toList());
        return detalle;
    }

    private void auditar(String accion, UUID fechaId, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, fechaId.toString(), motivo, detalle));
    }
}
