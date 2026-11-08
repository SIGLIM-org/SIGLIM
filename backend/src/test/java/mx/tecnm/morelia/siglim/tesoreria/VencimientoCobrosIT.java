package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.EjecutorSistema;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.tesoreria.eventos.CobroVencido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * B-31.1 contra PostgreSQL real: la tarea de vencimiento con el {@code UPDATE} condicional, RLS y el
 * trigger {@code tg_cobros_estado} con el perfil Sistema, y la auditoría.
 *
 * <p>La tarea se construye con los beans reales y un reloj fijo a las 23:30 de México del día de hoy
 * (05:30 UTC del día siguiente): así «hoy» es el mismo día de negocio que usan las demás pruebas, cuyos
 * cobros vencen en el futuro y no se ven afectados. Los eventos se capturan con un publicador propio.
 * Los cobros se preparan por SQL como dueño del esquema, con su estado y fecha límite.
 */
class VencimientoCobrosIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final BigDecimal MONTO = new BigDecimal("1500.00");
    private static final byte[] PDF = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);

    @Autowired
    private CobroRepository cobros;

    @Autowired
    private AuditoriaService auditoria;

    @Autowired
    private MotivoOperacion motivoOperacion;

    @Autowired
    private EjecutorSistema ejecutorSistema;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Autowired
    private SiglimPropiedades propiedades;

    @Autowired
    private VencimientoCobrosService tareaDelContexto;

    @Autowired
    private PagoSimuladoService simulados;

    @Autowired
    private PagoService pagos;

    private final List<CobroVencido> publicados = new ArrayList<>();
    private final LocalDate hoy = LocalDate.now(Clock.system(ZONA_DE_NEGOCIO));

    /**
     * Spring Test deja una petición simulada en el hilo de la prueba, y {@link EjecutorSistema} se niega
     * a correr dentro de una petición. Se retira para reproducir el hilo del planificador.
     */
    @BeforeEach
    void comoElHiloDelPlanificador() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** Tarea con reloj fijo a las 23:30 de México de {@link #hoy}, que en UTC ya es el día siguiente. */
    private VencimientoCobrosService tarea() {
        Instant nocheEnMexico = hoy.atTime(23, 30).atZone(ZONA_DE_NEGOCIO).toInstant();
        return new VencimientoCobrosService(cobros, auditoria, motivoOperacion,
                evento -> publicados.add((CobroVencido) evento), ejecutorSistema, transacciones,
                Clock.fixed(nocheEnMexico, ZoneOffset.UTC), propiedades, true);
    }

    // ── 1 y 2: la regla fecha_limite < hoy ───────────────────────────────────────────────────

    @Test
    @DisplayName("RF-141 · DEC-60: un cobro PENDIENTE con fecha límite de ayer queda VENCIDO y auditado por el Sistema")
    void venceElDeAyer() {
        UUID cobro = cobro(club(), "PENDIENTE", hoy.minusDays(1));

        tarea().vencer();

        assertThat(estado(cobro)).isEqualTo("VENCIDO");
        List<Auditado> auditados = auditoria(cobro);
        assertThat(auditados).hasSize(1);
        Auditado auditado = auditados.getFirst();
        assertThat(auditado.perfilId()).as("perfil Sistema de la semilla").isEqualTo(EjecutorSistema.PERFIL_SISTEMA);
        assertThat(auditado.modulo()).isEqualTo("M-13");
        assertThat(auditado.motivo()).isEqualTo("vencimiento automático por fecha límite");
        assertThat(auditado.detalle()).contains("\"estadoAnterior\": \"PENDIENTE\"", "\"estadoNuevo\": \"VENCIDO\"",
                "\"fechaLimite\": \"" + hoy.minusDays(1) + "\"", "\"monto\": 1500.00");
    }

    @Test
    @DisplayName("DEC-60: el día límite aún se paga: a las 23:30 de México (05:30 UTC del día siguiente) sigue PENDIENTE")
    void elDiaLimiteSiguePendiente() {
        UUID cobro = cobro(club(), "PENDIENTE", hoy);

        tarea().vencer();

        assertThat(estado(cobro)).isEqualTo("PENDIENTE");
        assertThat(auditoria(cobro)).isEmpty();
    }

    // ── 3: otros estados ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ERS §4.3.5: PAGADO, RECHAZADO y VENCIDO con fecha límite pasada no cambian ni se auditan")
    void otrosEstadosNoCambian() {
        UUID club = club();
        UUID pagado = cobro(club, "PAGADO", hoy.minusDays(3));
        UUID rechazado = cobro(club, "RECHAZADO", hoy.minusDays(3));
        UUID vencido = cobro(club, "VENCIDO", hoy.minusDays(3));

        tarea().vencer();

        assertThat(estado(pagado)).isEqualTo("PAGADO");
        assertThat(estado(rechazado)).isEqualTo("RECHAZADO");
        assertThat(estado(vencido)).isEqualTo("VENCIDO");
        assertThat(auditoria(pagado)).isEmpty();
        assertThat(auditoria(rechazado)).isEmpty();
        assertThat(auditoria(vencido)).isEmpty();
        assertThat(publicados).extracting(CobroVencido::cobroId).doesNotContain(pagado, rechazado, vencido);
    }

    // ── 4 y 5: idempotencia y recuperación ───────────────────────────────────────────────────

    @Test
    @DisplayName("ADR-0009 §2: ejecutar dos veces seguidas vence una sola vez y deja una sola auditoría")
    void idempotente() {
        UUID cobro = cobro(club(), "PENDIENTE", hoy.minusDays(1));
        VencimientoCobrosService tarea = tarea();

        tarea.vencer();
        int segunda = tarea.vencer();

        assertThat(segunda).isZero();
        assertThat(estado(cobro)).isEqualTo("VENCIDO");
        assertThat(auditoria(cobro)).hasSize(1);
    }

    @Test
    @DisplayName("ADR-0009 §3: un cobro que venció hace 10 días también se vence en la primera ejecución")
    void sePoneAlCorriente() {
        UUID cobro = cobro(club(), "PENDIENTE", hoy.minusDays(10));

        tarea().vencer();

        assertThat(estado(cobro)).isEqualTo("VENCIDO");
        assertThat(auditoria(cobro)).hasSize(1);
    }

    // ── 6: eventos ───────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-141: publica un CobroVencido por cada cobro vencido y ninguno si no hubo cambios")
    void publicaUnEventoPorCobro() {
        UUID club = club();
        UUID ayer = cobro(club, "PENDIENTE", hoy.minusDays(1));
        UUID anteayer = cobro(club, "PENDIENTE", hoy.minusDays(2));
        UUID vigente = cobro(club, "PENDIENTE", hoy);
        VencimientoCobrosService tarea = tarea();

        tarea.vencer();

        assertThat(publicados).filteredOn(evento -> evento.clubId().equals(club))
                .containsExactlyInAnyOrder(
                        new CobroVencido(LIGA, ayer, club, null, TipoCobro.MANUAL, MONTO, hoy.minusDays(1)),
                        new CobroVencido(LIGA, anteayer, club, null, TipoCobro.MANUAL, MONTO, hoy.minusDays(2)));
        assertThat(publicados).extracting(CobroVencido::cobroId).doesNotContain(vigente);

        publicados.clear();
        tarea.vencer();
        assertThat(publicados).as("segunda ejecución sin cambios").isEmpty();
    }

    // ── 7: VAL-028 sobre un cobro vencido por la tarea ───────────────────────────────────────

    @Test
    @DisplayName("VAL-028: un cobro vencido por la tarea no se paga en línea, pero sí admite el pago externo")
    void vencidoPorLaTareaSoloAdmitePagoExterno() {
        UUID club = club();
        UUID cobro = cobro(club, "PENDIENTE", hoy.minusDays(1));
        tarea().vencer();
        assertThat(estado(cobro)).isEqualTo("VENCIDO");

        iniciarSesion(perfilDeDirectiva(club));
        assertThatThrownBy(() -> simulados.iniciar(cobro))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("VAL-028");
                });

        iniciarSesion(perfilConRol("TESORERO"));
        PagoDto pago = pagos.registrar(cobro, new RegistrarPagoRequest(MedioPago.EFECTIVO, MONTO, null, null), PDF);

        assertThat(pago).isNotNull();
        assertThat(estado(cobro)).isEqualTo("PAGADO");
    }

    // ── Configuración de pruebas ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-60: en las pruebas la tarea del contexto está deshabilitada y no corre al arrancar")
    void deshabilitadaEnPruebas() {
        UUID cobro = cobro(club(), "PENDIENTE", hoy.minusDays(1));

        tareaDelContexto.ejecutarAlArrancar();
        tareaDelContexto.ejecutarProgramada();

        assertThat(estado(cobro)).isEqualTo("PENDIENTE");
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    /** Cobro manual al club insertado como dueño del esquema; el periodo empieza 30 días antes del límite. */
    private UUID cobro(UUID club, String estado, LocalDate fechaLimite) {
        return jdbc.sql("""
                        INSERT INTO siglim.cobros (liga_id, club_id, tipo, concepto, monto, periodo_inicio,
                                                   fecha_limite, estado)
                        VALUES (:liga, :club, 'MANUAL', 'Cuota de prueba', :monto, :inicio, :limite,
                                CAST(:estado AS siglim.estado_cobro))
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("club", club)
                .param("monto", MONTO)
                .param("inicio", fechaLimite.minusDays(30))
                .param("limite", fechaLimite)
                .param("estado", estado)
                .query(UUID.class).single();
    }

    private String estado(UUID cobro) {
        return jdbc.sql("SELECT estado::text FROM siglim.cobros WHERE id = :id")
                .param("id", cobro).query(String.class).single();
    }

    private List<Auditado> auditoria(UUID cobro) {
        return jdbc.sql("""
                        SELECT perfil_id, modulo, motivo, detalle::text AS detalle
                          FROM siglim.auditoria
                         WHERE accion = 'VENCER_COBRO' AND entidad = 'cobros' AND entidad_id = :id
                        """)
                .param("id", cobro.toString())
                .query(Auditado.class)
                .list();
    }

    private UUID club() {
        return jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA)
                .param("nombre", "Club vencimiento " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class).single();
    }

    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
        return perfil;
    }

    private UUID perfilDeDirectiva(UUID club) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil).param("club", club).update();
        return perfil;
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private record Auditado(UUID perfilId, String modulo, String motivo, String detalle) {
    }
}
