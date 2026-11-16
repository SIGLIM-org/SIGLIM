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
import mx.tecnm.morelia.siglim.programacion.VerificacionJornadaDto.Aviso;
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
 *   <li><b>Verificación sin guardar (H-12.3).</b> {@code POST /jornadas/{id}/verificacion} revisa
 *       todos los partidos con la misma regla del alta y el ajuste y devuelve cada conflicto con su
 *       partido. Las advertencias de localía (RF-120, VAL-027) son backlog B-15; hasta entonces la
 *       lista de advertencias va vacía.</li>
 *   <li><b>Publicación (H-13.1, RF-121, RF-090).</b> {@code BORRADOR → REVISION → PUBLICADO}, con
 *       la devolución {@code REVISION → BORRADOR}. El envío exige {@code programacion.jornada:ENVIAR},
 *       al menos un partido vigente y ningún conflicto duro ({@code 409 RF-039} con la lista). El
 *       dictamen exige {@code programacion.jornada:APROBAR}; devolver exige observaciones
 *       ({@code 400 RF-121}). El trigger {@code tg_jornadas_publicacion} valida la misma máquina de
 *       estados y deja cada paso en {@code historial_publicacion}.</li>
 *   <li><b>Separación de funciones (RF-121, RN-053).</b> Quien programa no aprueba: se rechaza con
 *       {@code 403 RF-121} si quien dictamina creó o envió la jornada, o si además tiene
 *       {@code programacion.jornada:EDITAR} (par incompatible del Apéndice E). La restricción
 *       {@code ck_jornadas_aprobacion} es la última garantía.</li>
 *   <li><b>Auditoría (RF-093).</b> {@code REGISTRAR_JORNADA}, {@code REGISTRAR_PARTIDO},
 *       {@code AJUSTAR_PARTIDO}, {@code ENVIAR_JORNADA_A_APROBACION}, {@code APROBAR_JORNADA} y
 *       {@code DEVOLVER_JORNADA} en M-07, con identificadores y sin datos personales.</li>
 * </ul>
 *
 * <p>Refs: H-12.1, H-12.3, H-13.1, RF-033, RF-036, RF-039, RF-040, RF-093, RF-090, RF-120, RF-121, RN-018,
 * RN-021, RN-053, VAL-003 a VAL-006, ADR-0007, ADR-0011
 */
@Service
public class JornadaService {

    private static final String CONSULTAR = "programacion.jornada:CONSULTAR";
    private static final String EDITAR = "programacion.jornada:EDITAR";
    private static final String ENVIAR = "programacion.jornada:ENVIAR";
    private static final String APROBAR = "programacion.jornada:APROBAR";
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
     * Revisa todos los partidos de la jornada y devuelve los conflictos duros sin escribir nada
     * (H-12.3, RF-039).
     *
     * <p>Usa {@link ValidadorConflictos#evaluar}, la misma regla que el alta y el ajuste, para que
     * la verificación y el guardado no discrepen. Los partidos cancelados no se revisan: no ocupan
     * cancha ni bloquean equipos. No exige que la jornada esté en {@code BORRADOR} porque es de
     * solo lectura; el alta y el ajuste sí lo exigen.
     *
     * @return los conflictos de todos los partidos y la lista de advertencias, vacía hasta que el
     *         backlog B-15 implemente la localía (RF-120, VAL-027)
     * @throws RecursoNoEncontradoException si la jornada no existe en la liga
     */
    @Transactional(readOnly = true)
    public VerificacionJornadaDto verificar(UUID id) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        jornadas.buscarJornada(ligaId, id).orElseThrow(() -> noExisteJornada(id));
        return new VerificacionJornadaDto(conflictosDe(jornadas.listarPartidos(id)), List.of());
    }

    /** Conflictos duros de los partidos vigentes, con la misma regla del alta y el ajuste (RF-039). */
    private List<Aviso> conflictosDe(List<DatosPartido> partidos) {
        List<Aviso> conflictos = new ArrayList<>();
        for (DatosPartido partido : partidos) {
            if (partido.estado() == EstadoPartido.CANCELADO) {
                continue;
            }
            EquipoDeLiga local = new EquipoDeLiga(partido.equipoLocalId(),
                    partido.equipoLocalNombre(), partido.grupoId(), partido.clubLocalId(),
                    partido.equipoLocalEstado());
            EquipoDeLiga visitante = new EquipoDeLiga(partido.equipoVisitanteId(),
                    partido.equipoVisitanteNombre(), partido.grupoId(), partido.clubVisitanteId(),
                    partido.equipoVisitanteEstado());
            CanchaDeLiga cancha = new CanchaDeLiga(partido.canchaId(), partido.canchaNombre(),
                    partido.canchaActiva());
            for (ValidadorConflictos.Conflicto conflicto : validador.evaluar(partido.grupoId(),
                    partido.id(), local, visitante, cancha, partido.inicio())) {
                conflictos.add(new Aviso(partido.id(), conflicto.codigo(), conflicto.mensaje()));
            }
        }
        return conflictos;
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

    // ── Publicación (H-13.1) ────────────────────────────────────────────────────────────────────

    /**
     * Envía a aprobación del Presidente una jornada en {@code BORRADOR} (RF-121, RF-090).
     *
     * <p>Orden: permiso → jornada existe ({@code 404}) → en borrador ({@code 409 RF-090}) → con al
     * menos un partido vigente ({@code 409 RF-121}) → sin conflictos duros ({@code 409 RF-039} con la
     * extensión {@code conflictos}, como la verificación). La jornada queda bloqueada para que ningún
     * alta o ajuste se cruce con el envío.
     *
     * @return la jornada en {@code REVISION} con sus partidos
     */
    @Transactional
    public JornadaDto enviarAAprobacion(UUID id) {
        autorizacion.exigir(ENVIAR, ObjetivoAutorizacion.deLiga());
        DatosJornada jornada = jornadas.buscarJornadaParaActualizar(ligaId, id)
                .orElseThrow(() -> noExisteJornada(id));
        if (jornada.estadoPublicacion() != EstadoJornada.BORRADOR) {
            throw ReglaNegocioException.conflicto("RF-090",
                    "Solo una jornada en borrador se envía a aprobación; la jornada " + jornada.numero()
                            + " está " + jornada.estadoPublicacion().texto() + ".");
        }
        List<DatosPartido> partidos = jornadas.listarPartidos(id);
        long vigentes = partidos.stream().filter(p -> p.estado() != EstadoPartido.CANCELADO).count();
        if (vigentes == 0) {
            // [PROPUESTA] El ERS no lo dice expresamente: una jornada sin partidos no se somete a aprobación.
            throw ReglaNegocioException.conflicto("RF-121",
                    "La jornada " + jornada.numero() + " no tiene partidos programados; registra al menos uno "
                            + "antes de enviarla a aprobación.");
        }
        List<Aviso> conflictos = conflictosDe(partidos);
        if (!conflictos.isEmpty()) {
            throw ReglaNegocioException.conflicto("RF-039",
                    "La jornada tiene " + conflictos.size() + (conflictos.size() == 1 ? " conflicto" : " conflictos")
                            + " sin resolver: " + conflictos.getFirst().mensaje())
                    .con("conflictos", conflictos);
        }

        if (jornadas.enviarAAprobacion(id, UsuarioActual.idRequerido()) == 0) {
            throw cambioConcurrente();
        }

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", EstadoJornada.BORRADOR.name());
        detalle.put("estado", EstadoJornada.REVISION.name());
        detalle.put("partidos", vigentes);
        auditar("ENVIAR_JORNADA_A_APROBACION", ENTIDAD_JORNADA, id, null, detalle);
        return conPartidos(buscarJornada(id));
    }

    /**
     * Aplica el dictamen del Presidente sobre una jornada en {@code REVISION} (RF-121, CU-007).
     *
     * <p>Orden: permiso → jornada existe ({@code 404}) → en revisión ({@code 409 RF-090}) →
     * decisión presente ({@code 400}) → separación de funciones ({@code 403 RF-121}) → observaciones
     * de la devolución ({@code 400 RF-121}).
     *
     * <ul>
     *   <li>{@link DecisionJornada#PUBLICADO} fija {@code aprobada_por} y {@code aprobada_en}; el
     *       trigger fija {@code publicada_en} y la jornada aparece en {@code v_publica_programacion}.
     *       Las observaciones son opcionales.</li>
     *   <li>{@link DecisionJornada#BORRADOR} exige observaciones de al menos 10 caracteres, las
     *       guarda y las fija como motivo para que el trigger registre la devolución.</li>
     * </ul>
     */
    @Transactional
    public JornadaDto resolver(UUID id, ResolverJornadaRequest peticion) {
        autorizacion.exigir(APROBAR, ObjetivoAutorizacion.deLiga());
        DatosJornada jornada = jornadas.buscarJornadaParaActualizar(ligaId, id)
                .orElseThrow(() -> noExisteJornada(id));
        if (jornada.estadoPublicacion() != EstadoJornada.REVISION) {
            throw ReglaNegocioException.conflicto("RF-090",
                    "Solo una jornada en revisión se aprueba o se devuelve; la jornada " + jornada.numero()
                            + " está " + jornada.estadoPublicacion().texto() + ".");
        }
        DecisionJornada decision = peticion == null ? null : peticion.decision();
        if (decision == null) {
            throw invalida("Indica si la jornada se publica o se devuelve.");
        }
        UUID perfil = UsuarioActual.idRequerido();
        exigirSeparacionDeFunciones(jornada, perfil);

        if (decision == DecisionJornada.PUBLICADO) {
            String nota = textoOpcional(peticion.observaciones());
            if (nota != null) {
                motivoOperacion.fijar(nota);
            }
            if (jornadas.resolver(id, EstadoJornada.PUBLICADO, perfil, nota) == 0) {
                throw cambioConcurrente();
            }
            auditarDictamen("APROBAR_JORNADA", id, EstadoJornada.PUBLICADO, null);
        } else {
            String motivo = observacionesValidas(peticion.observaciones());
            motivoOperacion.fijar(motivo);
            if (jornadas.resolver(id, EstadoJornada.BORRADOR, null, motivo) == 0) {
                throw cambioConcurrente();
            }
            auditarDictamen("DEVOLVER_JORNADA", id, EstadoJornada.BORRADOR, motivo);
        }
        return conPartidos(buscarJornada(id));
    }

    /**
     * Quien programa no aprueba (RF-121, Apéndice E): ni quien creó o envió la jornada, ni quien
     * tiene además el permiso de editarla, aunque sus permisos vengan de otro rol.
     */
    private void exigirSeparacionDeFunciones(DatosJornada jornada, UUID perfil) {
        if (perfil.equals(jornada.enviadaPor()) || perfil.equals(jornada.creadoPor())) {
            throw ReglaNegocioException.prohibida("RF-121",
                    "Quien programó la jornada no puede dictaminarla: la aprobación corresponde al Presidente.");
        }
        if (autorizacion.permite(EDITAR, ObjetivoAutorizacion.deLiga())) {
            throw ReglaNegocioException.prohibida("RF-121",
                    "Tu cuenta también puede editar la programación; quien programa no puede aprobar la jornada.");
        }
    }

    private void auditarDictamen(String accion, UUID id, EstadoJornada estado, String motivo) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", EstadoJornada.REVISION.name());
        detalle.put("estado", estado.name());
        auditar(accion, ENTIDAD_JORNADA, id, motivo, detalle);
    }

    private JornadaDto conPartidos(DatosJornada jornada) {
        return aDto(jornada, jornadas.listarPartidos(jornada.id()).stream().map(JornadaService::aDto).toList());
    }

    /** El {@code UPDATE} condicionado no encontró el estado leído con candado: no debería ocurrir. */
    private static ReglaNegocioException cambioConcurrente() {
        return ReglaNegocioException.conflicto("RF-090",
                "La jornada cambió de estado mientras se procesaba; vuelve a consultarla.");
    }

    private static String observacionesValidas(String observaciones) {
        String texto = observaciones == null ? "" : observaciones.strip();
        if (texto.length() < MINIMO_MOTIVO) {
            throw ReglaNegocioException.invalida("RF-121",
                    "Escribe observaciones de al menos " + MINIMO_MOTIVO
                            + " caracteres para devolver la jornada al Programador.");
        }
        return texto;
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
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
                datos.estadoPublicacion(), partidos, datos.enviadaPor(), datos.enviadaEn(),
                datos.aprobadaPor(), datos.aprobadaEn(), datos.publicadaEn(), datos.observaciones(),
                datos.creadoEn(), datos.actualizadoEn());
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
