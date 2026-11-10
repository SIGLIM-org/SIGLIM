package mx.tecnm.morelia.siglim.canchas;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.canchas.CanchaRepository.DatosCancha;
import mx.tecnm.morelia.siglim.canchas.OcupacionDto.BloqueOcupacionDto;
import mx.tecnm.morelia.siglim.canchas.OcupacionDto.CanchaResumenDto;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de canchas, disponibilidad, bloqueos y ocupación.
 *
 * <ul>
 *   <li><b>Ámbito.</b> Una cancha de club pertenece a ese club; las de la liga o externas solo las
 *       alcanza el ámbito {@code LIGA}. Una cancha inexistente o de otra liga responde 404.</li>
 *   <li><b>Bloqueo y mantenimiento sobre partidos (PEN-007, [PROPUESTA] B-12.1 D1).</b> Se hacen en
 *       dos pasos: sin confirmación responden {@code 409 RF-030} aceptable con los partidos afectados
 *       y no guardan; con confirmación guardan. El mantenimiento (RF-031) es un bloqueo tipificado y
 *       hereda la regla de RF-030, también su código (B-12.1 D2). Los partidos <b>nunca</b> se modifican:
 *       se reprograman a mano.</li>
 *   <li><b>{@code DISPONIBLE} es informativo (D3).</b> Una cancha activa sin bloqueo se puede usar
 *       aunque no tenga {@code DISPONIBLE}, como hace {@code tg_partidos_programacion}; aquí no se
 *       calcula «fuera de disponibilidad» (VAL-005).</li>
 *   <li><b>Candado.</b> Toda escritura toma primero el candado de la cancha para que dos bloqueos,
 *       o un bloqueo y una desactivación, no se crucen. La programación (H-12) debe tomar el mismo.</li>
 *   <li><b>Sede (RF-028, [PROPUESTA]).</b> Una cancha puede quedar sin sede. En el {@code PATCH},
 *       {@code sedeId} la asigna y {@code quitarSede} la quita; los dos juntos son {@code 400} (D2).
 *       Una sede inexistente o de otra liga se rechaza antes de escribir con
 *       {@code 422 REFERENCIA_INVALIDA}; en el filtro del listado no es error y sale vacío (D4).</li>
 * </ul>
 *
 * <p>Refs: H-07.1, B-11.1, B-12.1, RF-027, RF-028, RF-029, RF-030, RF-031, RN-014, RN-021, VAL-005,
 * PEN-007, ADR-0007, ADR-0011
 */
@Service
public class CanchaService {

    private static final String CONSULTAR = "canchas.cancha:CONSULTAR";
    private static final String EDITAR = "canchas.cancha:EDITAR";
    private static final String MODULO = "M-06";
    private static final String ENTIDAD_CANCHA = "canchas";
    private static final String ENTIDAD_DISPONIBILIDAD = "disponibilidad_canchas";
    private static final String RF_DISPONIBILIDAD = "RF-029";
    private static final String RF_BLOQUEO = "RF-030";

    /** [PROPUESTA] Convenciones de API §5, D11. */
    private static final int MINIMO_MOTIVO = 10;
    /** [PROPUESTA] Rango máximo de las consultas: alcanza para el calendario de F-21 (D6). */
    private static final Duration RANGO_MAXIMO = Duration.ofDays(31);

    private final CanchaRepository canchas;
    private final SedeRepository sedes;
    private final DisponibilidadCanchaRepository disponibilidades;
    private final ConsultaPartidosEnCancha partidos;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final MotivoOperacion motivoOperacion;
    private final Clock reloj;
    private final UUID ligaId;

    CanchaService(CanchaRepository canchas, SedeRepository sedes, DisponibilidadCanchaRepository disponibilidades,
            ConsultaPartidosEnCancha partidos, Autorizacion autorizacion, AuditoriaService auditoria,
            MotivoOperacion motivoOperacion, Clock reloj, SiglimPropiedades propiedades) {
        this.canchas = canchas;
        this.sedes = sedes;
        this.disponibilidades = disponibilidades;
        this.partidos = partidos;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.motivoOperacion = motivoOperacion;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /** Listado filtrado por ámbito en el SQL, también en el total (RF-027, ADR-0007). */
    @Transactional(readOnly = true)
    public Pagina<CanchaDto> listar(FiltroCanchas filtro) {
        AlcanceAutorizacion alcance = autorizacion.alcance(CONSULTAR);
        filtro.validar();
        return Pagina.de(canchas.listar(ligaId, filtro, alcance), filtro.pagina(), filtro.tamano(),
                canchas.contar(ligaId, filtro, alcance));
    }

    /** Detalle de una cancha (RF-027). */
    @Transactional(readOnly = true)
    public CanchaDto consultar(UUID id) {
        CanchaDto cancha = buscar(id);
        autorizacion.exigir(CONSULTAR, objetivo(cancha));
        return cancha;
    }

    /** Registra una cancha con su ubicación, propietario y, si se indica, su sede (RF-027, RF-028). */
    @Transactional
    public CanchaDto crear(CrearCanchaRequest peticion) {
        autorizacion.exigir(EDITAR, objetivo(peticion.propietario(), peticion.clubId()));
        DatosCancha datos = new DatosCancha(peticion.nombre().strip(), peticion.ubicacion(),
                textoOpcional(peticion.direccion()), peticion.propietario(), peticion.clubId(),
                textoOpcional(peticion.propietarioExterno()), textoOpcional(peticion.caracteristicas()),
                peticion.sedeId());
        validarPropietario(datos);
        validarSede(datos.sedeId());
        validarNombreLibre(datos.nombre(), null);

        UUID id = canchas.insertar(ligaId, datos);

        auditar("REGISTRAR_CANCHA", ENTIDAD_CANCHA, id, null, detalleDatos(datos));
        return buscar(id);
    }

    /**
     * Edita una cancha, le asigna o quita la sede (RF-028, D2), y la activa o desactiva con motivo
     * (RF-027, RN-021). Desactivarla con partidos en curso o futuros sigue el flujo de dos pasos del
     * bloqueo (D8, [PROPUESTA]).
     */
    @Transactional
    public CanchaDto actualizar(UUID id, ActualizarCanchaRequest peticion) {
        CanchaDto actual = buscarConCandado(id);
        autorizacion.exigir(EDITAR, objetivo(actual));
        DatosCancha datos = resultante(actual, peticion);
        UUID clubActual = clubId(actual);
        if (!Objects.equals(datos.clubId(), clubActual)) {
            autorizacion.exigir(EDITAR, objetivo(datos.propietario(), datos.clubId()));
        }
        validarPropietario(datos);
        if (!Objects.equals(datos.sedeId(), sedeId(actual))) {
            validarSede(datos.sedeId());
        }
        if (!datos.nombre().equals(actual.nombre())) {
            validarNombreLibre(datos.nombre(), id);
        }

        boolean activa = peticion.activa() == null ? actual.activa() : peticion.activa();
        boolean cambiaActiva = activa != actual.activa();
        List<PartidoEnCanchaDto> afectados = List.of();
        if (cambiaActiva) {
            fijarMotivo(peticion.motivo());
            if (!activa) {
                afectados = partidos.listarProgramadosDesde(id, OffsetDateTime.now(reloj));
                exigirConfirmacion(afectados, peticion.confirmarPartidosAfectados(),
                        "Desactivar la cancha afecta a %d partido(s) en curso o futuros, que no se reprogramarán solos.");
            }
        }

        List<String> campos = camposCambiados(actual, datos);
        if (campos.isEmpty() && !cambiaActiva) {
            return actual;
        }
        canchas.actualizar(id, datos, activa);

        if (!campos.isEmpty()) {
            auditar("EDITAR_CANCHA", ENTIDAD_CANCHA, id, null, Map.of("campos", campos));
        }
        if (cambiaActiva) {
            auditar(activa ? "ACTIVAR_CANCHA" : "DESACTIVAR_CANCHA", ENTIDAD_CANCHA, id, null,
                    detallePartidos(new LinkedHashMap<>(), afectados));
        }
        return buscar(id);
    }

    /** Registros de disponibilidad, bloqueo y mantenimiento en un rango (RF-029, RF-030). */
    @Transactional(readOnly = true)
    public List<DisponibilidadDto> listarDisponibilidad(UUID canchaId, OffsetDateTime desde, OffsetDateTime hasta) {
        CanchaDto cancha = buscar(canchaId);
        autorizacion.exigir(CONSULTAR, objetivo(cancha));
        return disponibilidades.listar(canchaId, rango(desde, hasta));
    }

    /**
     * Registra disponibilidad (RF-029), un bloqueo (RF-030) o un periodo de mantenimiento (RF-031).
     *
     * <ul>
     *   <li>Un {@code DISPONIBLE} no se traslapa con otro; su motivo es opcional.</li>
     *   <li>{@code BLOQUEO} y {@code MANTENIMIENTO} son restricciones: exigen motivo de al menos 10
     *       caracteres, que se fija en la transacción ({@code 400 RN-021}; [PROPUESTA] B-12.1 D5), y
     *       pueden traslaparse con cualquier registro (D4 de H-07.1; [PROPUESTA] B-12.1 D3).</li>
     *   <li>Sobre partidos {@code PROGRAMADO} o {@code REPROGRAMADO} siguen el flujo de dos pasos:
     *       sin confirmación, {@code 409 RF-030} aceptable con {@code partidosAfectados}; con
     *       {@code confirmarPartidosAfectados}, se guardan sin tocar los partidos ([PROPUESTA] B-12.1
     *       D1, D2 y D6).</li>
     *   <li>Se auditan como {@code REGISTRAR_DISPONIBILIDAD}, {@code BLOQUEAR_CANCHA} o
     *       {@code REGISTRAR_MANTENIMIENTO} ([PROPUESTA] B-12.1 D4).</li>
     * </ul>
     *
     * <p>El trigger de VAL-005 rechaza después cualquier partido que caiga en un bloqueo o mantenimiento.
     */
    @Transactional
    public RegistroDisponibilidadDto registrarDisponibilidad(UUID canchaId, RegistrarDisponibilidadRequest peticion) {
        CanchaDto cancha = buscarConCandado(canchaId);
        autorizacion.exigir(EDITAR, objetivo(cancha));
        TipoDisponibilidad tipo = peticion.tipo();
        Intervalo periodo = peticion.periodo();
        validarOrden(periodo);

        String motivo = textoOpcional(peticion.motivo());
        List<PartidoEnCanchaDto> afectados = List.of();
        if (esRestriccion(tipo)) {
            motivo = fijarMotivo(motivo);
            afectados = partidos.listarProgramados(canchaId, periodo);
            exigirConfirmacion(afectados, peticion.confirmarPartidosAfectados(), mensajeAfectados(tipo));
        } else {
            disponibilidades.buscarDisponibleTraslapado(canchaId, periodo).ifPresent(existente -> {
                throw ReglaNegocioException.conflicto(RF_DISPONIBILIDAD,
                        "La cancha ya tiene disponibilidad registrada de " + existente.periodo().comoTexto() + ".");
            });
            if (motivo != null) {
                motivoOperacion.fijar(motivo);
            }
        }

        DisponibilidadDto registro =
                disponibilidades.insertar(canchaId, tipo, periodo, motivo, UsuarioActual.idRequerido());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("canchaId", canchaId.toString());
        detalle.put("tipo", tipo.name());
        detalle.put("desde", periodo.desde().toString());
        detalle.put("hasta", periodo.hasta().toString());
        auditar(accionAuditoria(tipo), ENTIDAD_DISPONIBILIDAD, registro.id(), motivo, detallePartidos(detalle, afectados));
        return new RegistroDisponibilidadDto(registro, afectados);
    }

    /**
     * Ocupación de la cancha en un rango: registros de disponibilidad y partidos no cancelados,
     * ordenados por inicio (apoyo a RF-039). No calcula «fuera de disponibilidad» (D3).
     */
    @Transactional(readOnly = true)
    public OcupacionDto consultarOcupacion(UUID canchaId, OffsetDateTime desde, OffsetDateTime hasta) {
        CanchaDto cancha = buscar(canchaId);
        autorizacion.exigir(CONSULTAR, objetivo(cancha));
        Intervalo rango = rango(desde, hasta);

        List<BloqueOcupacionDto> bloques = new ArrayList<>();
        for (DisponibilidadDto registro : disponibilidades.listar(canchaId, rango)) {
            bloques.add(new BloqueOcupacionDto(TipoBloqueOcupacion.valueOf(registro.tipo().name()),
                    registro.periodo(), registro.motivo(), null));
        }
        for (PartidoEnCanchaDto partido : partidos.listarNoCancelados(canchaId, rango)) {
            bloques.add(new BloqueOcupacionDto(TipoBloqueOcupacion.PARTIDO, partido.periodo(), null, partido));
        }
        bloques.sort(Comparator.comparing(bloque -> bloque.periodo().desde().toInstant()));
        return new OcupacionDto(new CanchaResumenDto(cancha.id(), cancha.nombre()), cancha.activa(), rango, bloques);
    }

    // ── Reglas ─────────────────────────────────────────────────────────────────────────────────

    private CanchaDto buscar(UUID id) {
        return canchas.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private CanchaDto buscarConCandado(UUID id) {
        return canchas.buscarParaActualizar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Cancha " + id);
    }

    private static ObjetivoAutorizacion objetivo(CanchaDto cancha) {
        return objetivo(cancha.propietario(), clubId(cancha));
    }

    private static ObjetivoAutorizacion objetivo(PropietarioCancha propietario, UUID clubId) {
        return propietario == PropietarioCancha.CLUB && clubId != null
                ? ObjetivoAutorizacion.deClubes(clubId)
                : ObjetivoAutorizacion.deLiga();
    }

    private static UUID clubId(CanchaDto cancha) {
        return cancha.club() == null ? null : cancha.club().id();
    }

    private static UUID sedeId(CanchaDto cancha) {
        return cancha.sede() == null ? null : cancha.sede().id();
    }

    /** Estado de la cancha después del {@code PATCH}: lo ausente conserva su valor. */
    private static DatosCancha resultante(CanchaDto actual, ActualizarCanchaRequest peticion) {
        if (peticion.nombre() != null && peticion.nombre().isBlank()) {
            throw invalida("El nombre no puede estar vacío.");
        }
        boolean quitarSede = Boolean.TRUE.equals(peticion.quitarSede());
        if (quitarSede && peticion.sedeId() != null) {
            throw invalida("Indica una sede o pide quitarla, pero no las dos cosas a la vez.");
        }
        UUID sedeId;
        if (quitarSede) {
            sedeId = null;
        } else {
            sedeId = peticion.sedeId() == null ? sedeId(actual) : peticion.sedeId();
        }
        String nombre = peticion.nombre() == null ? actual.nombre() : peticion.nombre().strip();
        UbicacionCancha ubicacion = peticion.ubicacion() == null ? actual.ubicacion() : peticion.ubicacion();
        String direccion = peticion.direccion() == null ? actual.direccion() : textoOpcional(peticion.direccion());
        String caracteristicas = peticion.caracteristicas() == null
                ? actual.caracteristicas() : textoOpcional(peticion.caracteristicas());
        PropietarioCancha propietario;
        UUID clubId;
        String externo;
        if (peticion.propietario() != null) {
            propietario = peticion.propietario();
            clubId = peticion.clubId();
            externo = textoOpcional(peticion.propietarioExterno());
        } else {
            propietario = actual.propietario();
            clubId = peticion.clubId() == null ? clubId(actual) : peticion.clubId();
            externo = peticion.propietarioExterno() == null
                    ? actual.propietarioExterno() : textoOpcional(peticion.propietarioExterno());
        }
        return new DatosCancha(nombre, ubicacion, direccion, propietario, clubId, externo, caracteristicas, sedeId);
    }

    /** {@code CLUB} lleva club, {@code EXTERNO} lleva nombre y {@code LIGA} ninguno (RF-027). */
    private static void validarPropietario(DatosCancha datos) {
        switch (datos.propietario()) {
            case CLUB -> {
                if (datos.clubId() == null || datos.propietarioExterno() != null) {
                    throw invalida("Una cancha de club debe indicar el club y no un propietario externo.");
                }
            }
            case EXTERNO -> {
                if (datos.propietarioExterno() == null || datos.clubId() != null) {
                    throw invalida("Una cancha externa debe indicar el propietario externo y no un club.");
                }
            }
            case LIGA -> {
                if (datos.clubId() != null || datos.propietarioExterno() != null) {
                    throw invalida("Una cancha de la liga no lleva club ni propietario externo.");
                }
            }
        }
    }

    /**
     * La sede indicada existe y es de esta liga; sin sede no hay nada que validar (RF-028). Se valida
     * antes de escribir para explicar el error en lugar de depender de la llave foránea (D4).
     */
    private void validarSede(UUID sedeId) {
        if (sedeId != null && sedes.buscar(ligaId, sedeId).isEmpty()) {
            throw ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                    "La sede indicada no existe. Actualiza la página y elige otra.");
        }
    }

    private void validarNombreLibre(String nombre, UUID excepto) {
        if (canchas.existeNombre(ligaId, nombre, excepto)) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "Ya existe una cancha llamada «" + nombre + "» en la liga.");
        }
    }

    private static void validarOrden(Intervalo periodo) {
        if (!periodo.desde().isBefore(periodo.hasta())) {
            throw invalida("El inicio del periodo debe ser anterior a su fin.");
        }
    }

    private static Intervalo rango(OffsetDateTime desde, OffsetDateTime hasta) {
        if (desde == null || hasta == null) {
            throw invalida("Indica las fechas desde y hasta.");
        }
        Intervalo rango = new Intervalo(desde, hasta);
        validarOrden(rango);
        if (Duration.between(desde, hasta).compareTo(RANGO_MAXIMO) > 0) {
            throw invalida("El rango de consulta es de " + RANGO_MAXIMO.toDays() + " días como máximo.");
        }
        return rango;
    }

    /** Valida el motivo obligatorio y lo fija en la transacción para el historial (RN-021). */
    private String fijarMotivo(String motivo) {
        String texto = motivo == null ? "" : motivo.strip();
        if (texto.length() < MINIMO_MOTIVO) {
            throw ReglaNegocioException.invalida("RN-021",
                    "Escribe un motivo de al menos " + MINIMO_MOTIVO + " caracteres.");
        }
        motivoOperacion.fijar(texto);
        return texto;
    }

    /**
     * {@code BLOQUEO} y {@code MANTENIMIENTO} restringen el uso de la cancha; {@code DISPONIBLE} es
     * informativo (RF-030, RF-031).
     */
    private static boolean esRestriccion(TipoDisponibilidad tipo) {
        return switch (tipo) {
            case BLOQUEO, MANTENIMIENTO -> true;
            case DISPONIBLE -> false;
        };
    }

    /** Mensaje del {@code 409 RF-030} según el tipo de restricción ([PROPUESTA] B-12.1 D6). */
    private static String mensajeAfectados(TipoDisponibilidad tipo) {
        String sujeto = tipo == TipoDisponibilidad.MANTENIMIENTO ? "El mantenimiento" : "El bloqueo";
        return sujeto + " afecta a %d partido(s), que no se reprogramarán solos.";
    }

    /** Acción de auditoría de cada tipo de registro ([PROPUESTA] B-12.1 D4). */
    private static String accionAuditoria(TipoDisponibilidad tipo) {
        return switch (tipo) {
            case DISPONIBLE -> "REGISTRAR_DISPONIBILIDAD";
            case BLOQUEO -> "BLOQUEAR_CANCHA";
            case MANTENIMIENTO -> "REGISTRAR_MANTENIMIENTO";
        };
    }

    /** Sin confirmación y con partidos afectados: {@code 409 RF-030} aceptable, sin guardar (PEN-007). */
    private static void exigirConfirmacion(List<PartidoEnCanchaDto> afectados, Boolean confirmado, String mensaje) {
        if (!afectados.isEmpty() && !Boolean.TRUE.equals(confirmado)) {
            throw ReglaNegocioException.conflicto(RF_BLOQUEO, mensaje.formatted(afectados.size()))
                    .con("aceptable", true)
                    .con("partidosAfectados", afectados);
        }
    }

    private static List<String> camposCambiados(CanchaDto actual, DatosCancha datos) {
        List<String> campos = new ArrayList<>();
        if (!datos.nombre().equals(actual.nombre())) {
            campos.add("nombre");
        }
        if (datos.ubicacion() != actual.ubicacion()) {
            campos.add("ubicacion");
        }
        if (!Objects.equals(datos.direccion(), actual.direccion())) {
            campos.add("direccion");
        }
        if (datos.propietario() != actual.propietario()
                || !Objects.equals(datos.clubId(), clubId(actual))
                || !Objects.equals(datos.propietarioExterno(), actual.propietarioExterno())) {
            campos.add("propietario");
        }
        if (!Objects.equals(datos.caracteristicas(), actual.caracteristicas())) {
            campos.add("caracteristicas");
        }
        if (!Objects.equals(datos.sedeId(), sedeId(actual))) {
            campos.add("sede");
        }
        return campos;
    }

    // ── Auditoría: el detalle no lleva la dirección ni el propietario externo, que pueden ser de una persona ───

    private void auditar(String accion, String entidad, UUID entidadId, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, entidad, entidadId.toString(), motivo, detalle));
    }

    private static Map<String, Object> detalleDatos(DatosCancha datos) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("ubicacion", datos.ubicacion().name());
        detalle.put("propietario", datos.propietario().name());
        if (datos.clubId() != null) {
            detalle.put("clubId", datos.clubId().toString());
        }
        if (datos.sedeId() != null) {
            detalle.put("sedeId", datos.sedeId().toString());
        }
        return detalle;
    }

    private static Map<String, Object> detallePartidos(Map<String, Object> detalle, List<PartidoEnCanchaDto> afectados) {
        detalle.put("partidosAfectados", afectados.size());
        detalle.put("partidosAfectadosIds", afectados.stream().map(partido -> partido.id().toString()).toList());
        return detalle;
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
