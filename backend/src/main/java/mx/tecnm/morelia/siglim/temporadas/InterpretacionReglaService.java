package mx.tecnm.morelia.siglim.temporadas;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.InterpretacionReglaRepository.DatosInterpretacion;
import mx.tecnm.morelia.siglim.temporadas.TemporadaRepository.DatosTemporada;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interpretaciones administrativas de las reglas de la temporada (RF-007, RN-006): cuando una regla
 * no es explícita, la interpretación no se convierte en regla permanente, se registra como
 * {@code PENDIENTE} hasta que el Presidente la autoriza o la rechaza.
 *
 * <ul>
 *   <li><b>Nace pendiente.</b> El alta no admite estado: siempre escribe {@code PENDIENTE} y toma la
 *       autoría de la sesión, nunca del cuerpo (RF-007).</li>
 *   <li><b>Solo el Presidente resuelve (D1).</b> Registrar exige
 *       {@code temporadas.interpretacion:REGISTRAR}; resolver, {@code …:APROBAR}. Los dos permisos
 *       son datos: el código no compara nombres de rol (ADR-0007).</li>
 *   <li><b>Quien la registra no la resuelve (D2, DEC-57).</b> Separación de funciones: aunque
 *       una cuenta tenga los dos permisos, no puede autorizar su propia interpretación. Se responde
 *       {@code 403 RF-007} y no se escribe ni se audita nada.</li>
 *   <li><b>Resolución definitiva (D3).</b> Solo se resuelve una interpretación {@code PENDIENTE}, y
 *       la resolución es {@code AUTORIZADA} o {@code RECHAZADA}; repetirla responde
 *       {@code 409 RF-007}. El rechazo exige observaciones, porque son el motivo del registro.</li>
 *   <li><b>Nunca toca las reglas versionadas (D4).</b> Esta tabla es un registro aparte: ni la
 *       resolución ni el alta escriben {@code parametros_temporada} (RF-006, RF-007). Autorizar una
 *       interpretación no cambia puntos, desempates ni ningún otro cálculo.</li>
 *   <li><b>Temporada cerrada (D5, DEC-59).</b> Por RN-005 el histórico de la temporada no se
 *       modifica: registrar una interpretación de una temporada {@code CERRADA} responde
 *       {@code 409 RF-003}. Pero lo ya registrado sigue vivo para poder resolverse en el mismo
 *       {@code estado_interpretacion}, sin escribir nada en {@code temporadas} ni en los
 *       parámetros versionados. La Liga debe confirmar que esta lectura no rompe RN-005.</li>
 *   <li><b>Consulta (D6).</b> La necesitan quien registra y quien resuelve, y el catálogo inicial no
 *       define un permiso de consulta para M-02: el listado acepta cualquiera de los dos, con ámbito
 *       de liga. Un ámbito de club no alcanza una interpretación de la liga (VAL-024) y su página
 *       llega vacía, sin que el total delate filas.</li>
 *   <li><b>Auditoría (D7, RF-093).</b> {@code REGISTRAR_INTERPRETACION} y
 *       {@code RESOLVER_INTERPRETACION} sobre {@code interpretaciones_regla}, con la temporada, los
 *       estados y, al rechazar, las observaciones como motivo; nunca se copia el texto completo.</li>
 * </ul>
 *
 * <p>La tabla {@code interpretaciones_regla} no tiene trigger de estado: la máquina de estados se
 * valida aquí, con el candado de la fila para que dos resoluciones simultáneas no se pisen.
 *
 * <p>Refs: B-03.1, RF-003, RF-006, RF-007, RF-093, RN-005, RN-006, VAL-015, VAL-024, ADR-0007,
 * ADR-0011
 */
@Service
public class InterpretacionReglaService {

    private static final String REGISTRAR = "temporadas.interpretacion:REGISTRAR";
    private static final String APROBAR = "temporadas.interpretacion:APROBAR";
    private static final String MODULO = "M-02";
    private static final String ENTIDAD = "interpretaciones_regla";

    /** Mínimo de las observaciones de un rechazo, igual que el motivo de RN-021 (DEC-58). */
    private static final int MINIMO_OBSERVACIONES = 10;

    private static final int TAMANO_MAXIMO = 100;

    private final InterpretacionReglaRepository interpretaciones;
    private final TemporadaRepository temporadas;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final UUID ligaId;

    InterpretacionReglaService(InterpretacionReglaRepository interpretaciones, TemporadaRepository temporadas,
            Autorizacion autorizacion, AuditoriaService auditoria, Clock reloj, SiglimPropiedades propiedades) {
        this.interpretaciones = interpretaciones;
        this.temporadas = temporadas;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Interpretaciones de la liga, de la más reciente a la más antigua (RF-007, D6).
     *
     * @param temporadaId filtro opcional por temporada
     * @param estado      filtro opcional por estado; {@code PENDIENTE} es el que usa el Presidente
     * @param pagina      página, desde 0
     * @param tamano      filas por página, de 1 a 100
     */
    @Transactional(readOnly = true)
    public Pagina<InterpretacionReglaDto> listar(UUID temporadaId, EstadoInterpretacion estado, int pagina,
            int tamano) {
        AlcanceAutorizacion alcance = alcanceConsulta();
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw invalida("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }
        long desplazamiento = (long) pagina * tamano;
        List<InterpretacionReglaDto> contenido = interpretaciones
                .listar(ligaId, temporadaId, estado, tamano, desplazamiento, alcance.todaLaLiga())
                .stream()
                .map(InterpretacionReglaDto::desde)
                .toList();
        return Pagina.de(contenido, pagina, tamano,
                interpretaciones.contar(ligaId, temporadaId, estado, alcance.todaLaLiga()));
    }

    /**
     * Registra una interpretación en {@code PENDIENTE} con la autoría de la sesión (RF-007).
     *
     * <p>Orden: permiso ({@code 403}) → descripción ({@code 400 VALIDACION}) → temporada, con
     * candado ({@code 404}) → temporada cerrada ({@code 409 RF-003}).
     *
     * @throws ReglaNegocioException 409 {@code RF-003} si la temporada está cerrada
     */
    @Transactional
    public InterpretacionReglaDto registrar(CrearInterpretacionRequest peticion) {
        autorizacion.exigir(REGISTRAR, ObjetivoAutorizacion.deLiga());
        if (peticion == null || peticion.temporadaId() == null) {
            throw invalida("Indica la temporada a la que aplica la interpretación.");
        }
        String descripcion = descripcionValida(peticion.descripcion());

        // El candado de la temporada serializa con su cierre: no se registra una interpretación de
        // una temporada que se está cerrando al mismo tiempo (RF-003).
        DatosTemporada temporada = temporadas.buscarParaActualizar(ligaId, peticion.temporadaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Temporada " + peticion.temporadaId()));
        if (temporada.estado() == EstadoTemporada.CERRADA) {
            throw ReglaNegocioException.conflicto("RF-003",
                    "La temporada «" + temporada.nombre()
                            + "» está cerrada: no admite interpretaciones nuevas.");
        }

        UUID id = interpretaciones.insertar(temporada.id(), descripcion, UsuarioActual.idRequerido());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("temporadaId", temporada.id().toString());
        detalle.put("estado", EstadoInterpretacion.PENDIENTE.name());
        auditar("REGISTRAR_INTERPRETACION", id, null, detalle);
        return aDto(buscar(id));
    }

    /**
     * Aplica la resolución del Presidente: {@code PENDIENTE → AUTORIZADA | RECHAZADA} (RF-007).
     *
     * <p>Orden: interpretación, con candado ({@code 404}) → permiso ({@code 403 VAL-015} o
     * {@code 404}) → estado ({@code 400 VALIDACION} o {@code 409 RF-007}) → separación de funciones
     * ({@code 403 RF-007}) → observaciones del rechazo ({@code 400 RF-007}).
     *
     * @throws ReglaNegocioException 409 {@code RF-007} si ya estaba resuelta; 403 {@code RF-007} si
     *                               la resuelve quien la registró; 400 {@code RF-007} si la rechaza
     *                               sin observaciones suficientes
     */
    @Transactional
    public InterpretacionReglaDto resolver(UUID id, ResolverInterpretacionRequest peticion) {
        DatosInterpretacion actual = buscarConCandado(id);
        autorizacion.exigir(APROBAR, ObjetivoAutorizacion.deLiga());
        if (actual.estado().resuelta()) {
            throw ReglaNegocioException.conflicto("RF-007",
                    "La interpretación ya está " + actual.estado().texto()
                            + ": una resolución no se reabre.");
        }
        EstadoInterpretacion estado = peticion == null ? null : peticion.estado();
        if (estado == null) {
            throw invalida("Indica si la interpretación se autoriza o se rechaza.");
        }
        if (!estado.resuelta()) {
            throw invalida("La resolución solo puede ser AUTORIZADA o RECHAZADA.");
        }

        UUID perfil = UsuarioActual.idRequerido();
        if (perfil.equals(actual.registradaPor())) {
            throw ReglaNegocioException.prohibida("RF-007",
                    "Quien registró la interpretación no puede resolverla: la autorización corresponde"
                            + " al Presidente.");
        }

        String observaciones = estado == EstadoInterpretacion.RECHAZADA
                ? observacionesValidas(peticion.observaciones())
                : textoOpcional(peticion.observaciones());
        interpretaciones.resolver(id, estado, perfil, OffsetDateTime.now(reloj), observaciones);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("temporadaId", actual.temporadaId().toString());
        detalle.put("estadoAnterior", actual.estado().name());
        detalle.put("estado", estado.name());
        auditar("RESOLVER_INTERPRETACION", id,
                estado == EstadoInterpretacion.RECHAZADA ? observaciones : null, detalle);
        return aDto(buscar(id));
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private DatosInterpretacion buscar(UUID id) {
        return interpretaciones.buscar(ligaId, id)
                .orElseThrow(() -> noExiste(id));
    }

    private DatosInterpretacion buscarConCandado(UUID id) {
        return interpretaciones.buscarParaActualizar(ligaId, id)
                .orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Interpretación de regla " + id);
    }

    /**
     * Consulta con cualquiera de los dos permisos de la interpretación y ámbito de liga (D6). Un
     * ámbito de club no la alcanza: se responde como un recurso fuera de ámbito (VAL-024).
     */
    private AlcanceAutorizacion alcanceConsulta() {
        boolean asignacionSinAlcance = false;
        for (String permiso : List.of(REGISTRAR, APROBAR)) {
            try {
                AlcanceAutorizacion alcance = autorizacion.alcance(permiso);
                if (alcance.todaLaLiga()) {
                    return alcance;
                }
                asignacionSinAlcance = true;
            } catch (ReglaNegocioException sinPermiso) {
                // Este permiso no está asignado: se prueba el otro.
            }
        }
        if (asignacionSinAlcance) {
            throw Autorizacion.fueraDeAmbito(REGISTRAR);
        }
        throw Autorizacion.sinPermiso();
    }

    private static String descripcionValida(String descripcion) {
        String texto = descripcion == null ? "" : descripcion.strip();
        if (texto.isEmpty()) {
            throw invalida("La descripción de la interpretación es obligatoria.");
        }
        return texto;
    }

    private static String observacionesValidas(String observaciones) {
        String texto = observaciones == null ? "" : observaciones.strip();
        if (texto.length() < MINIMO_OBSERVACIONES) {
            throw ReglaNegocioException.invalida("RF-007",
                    "Escribe observaciones de al menos " + MINIMO_OBSERVACIONES
                            + " caracteres para rechazar la interpretación.");
        }
        return texto;
    }

    private static String textoOpcional(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.strip();
    }

    private static InterpretacionReglaDto aDto(DatosInterpretacion datos) {
        return InterpretacionReglaDto.desde(datos);
    }

    private void auditar(String accion, UUID entidadId, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, entidadId.toString(), motivo,
                detalle));
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
