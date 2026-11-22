package mx.tecnm.morelia.siglim.tesoreria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.programacion.CrearJornadaRequest;
import mx.tecnm.morelia.siglim.programacion.CrearPartidoRequest;
import mx.tecnm.morelia.siglim.programacion.DecisionJornada;
import mx.tecnm.morelia.siglim.programacion.JornadaService;
import mx.tecnm.morelia.siglim.programacion.ResolverJornadaRequest;
import mx.tecnm.morelia.siglim.programacion.eventos.JornadaPublicada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * B-30 contra PostgreSQL real: al publicar la jornada con {@link JornadaService}, el oyente de
 * {@link JornadaPublicada} genera en la misma transacción los cobros de arbitraje y cancha de cada
 * equipo con las tarifas de la temporada y la categoría (RF-135, RF-136), sin cobro de cancha al
 * equipo que juega en la cancha de su club, y los audita.
 *
 * <p>Cada prueba crea su propia temporada, categoría, equipos y canchas: la base se comparte con las
 * demás pruebas de integración.
 */
class CobrosAutomaticosIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2031-03-10T10:00:00-06:00");
    /** Día del partido más los 7 días por defecto de {@code dias-para-pagar} [PROPUESTA]. */
    private static final LocalDate FECHA_LIMITE = LocalDate.parse("2031-03-17");

    @Autowired
    private JornadaService jornadas;

    @Autowired
    private CobroAutomaticoService cobrosAutomaticos;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Test
    @DisplayName("RF-136: al publicar la jornada, cada equipo recibe su cobro de arbitraje y de cancha según la tarifa")
    void cobrosEnCanchaDeLaLiga() {
        Grupo grupo = crearGrupo();
        fijarTarifa(grupo, "ARBITRAJE", "350.00");
        fijarTarifa(grupo, "CANCHA", "200.00");
        UUID local = crearEquipo(grupo, "Halcones");
        UUID visitante = crearEquipo(grupo, "Toros");

        UUID presidente = publicarJornadaCon(grupo, local, visitante, crearCanchaDeLaLiga());

        List<Cobro> cobros = cobrosDe(local, visitante);
        assertThat(cobros).extracting(Cobro::equipoId, Cobro::clubId, Cobro::tipo, Cobro::monto)
                .containsExactlyInAnyOrder(
                        tuple(local, clubDe(local), "ARBITRAJE", new BigDecimal("350.00")),
                        tuple(local, clubDe(local), "CANCHA", new BigDecimal("200.00")),
                        tuple(visitante, clubDe(visitante), "ARBITRAJE", new BigDecimal("350.00")),
                        tuple(visitante, clubDe(visitante), "CANCHA", new BigDecimal("200.00")));
        assertThat(cobros).allSatisfy(cobro -> {
            assertThat(cobro.estado()).isEqualTo("PENDIENTE");
            assertThat(cobro.fechaLimite()).isEqualTo(FECHA_LIMITE);
            assertThat(cobro.creadoPor()).isEqualTo(presidente);
            assertThat(cobro.concepto()).contains("Jornada 1").contains(" vs ");
        });
        assertThat(cobros).extracting(Cobro::partidoId).containsOnly(partidoDe(local));
        assertThat(cobros).allSatisfy(cobro -> assertThat(accionesAuditadas(cobro.id()))
                .containsExactly("GENERAR_COBRO"));
    }

    @Test
    @DisplayName("RF-136: el equipo que juega en la cancha de su club no recibe cobro de cancha; el rival sí")
    void sinCobroDeCanchaPropia() {
        Grupo grupo = crearGrupo();
        fijarTarifa(grupo, "ARBITRAJE", "300.00");
        fijarTarifa(grupo, "CANCHA", "150.00");
        UUID local = crearEquipo(grupo, "Águilas");
        UUID visitante = crearEquipo(grupo, "Lobos");

        publicarJornadaCon(grupo, local, visitante, crearCanchaDelClub(local));

        assertThat(cobrosDe(local, visitante)).extracting(Cobro::equipoId, Cobro::tipo)
                .containsExactlyInAnyOrder(
                        tuple(local, "ARBITRAJE"),
                        tuple(visitante, "ARBITRAJE"),
                        tuple(visitante, "CANCHA"));
    }

    @Test
    @DisplayName("RF-136 [PROPUESTA]: sin tarifa o con tarifa en cero no se genera ese cobro y la omisión se audita")
    void sinTarifaSeOmiteYSeAudita() {
        Grupo grupo = crearGrupo();
        fijarTarifa(grupo, "ARBITRAJE", "0.00");
        UUID local = crearEquipo(grupo, "Pumas");
        UUID visitante = crearEquipo(grupo, "Cóndores");

        publicarJornadaCon(grupo, local, visitante, crearCanchaDeLaLiga());

        assertThat(cobrosDe(local, visitante)).isEmpty();
        UUID jornada = jdbc.sql("SELECT jornada_id FROM siglim.partidos WHERE equipo_local_id = :local")
                .param("local", local).query(UUID.class).single();
        String resumen = jdbc.sql("""
                        SELECT detalle::text FROM siglim.auditoria
                         WHERE entidad_id = :id AND accion = 'GENERAR_COBROS_JORNADA'
                        """)
                .param("id", jornada.toString()).query(String.class).single();
        assertThat(resumen).contains("\"cobrosGenerados\": 0").contains("TARIFA_EN_CERO").contains("SIN_TARIFA");
    }

    @Test
    @DisplayName("RF-136: volver a procesar la jornada publicada no duplica los cobros")
    void generacionIdempotente() {
        Grupo grupo = crearGrupo();
        fijarTarifa(grupo, "ARBITRAJE", "350.00");
        fijarTarifa(grupo, "CANCHA", "200.00");
        UUID local = crearEquipo(grupo, "Venados");
        UUID visitante = crearEquipo(grupo, "Coyotes");
        UUID presidente = publicarJornadaCon(grupo, local, visitante, crearCanchaDeLaLiga());
        UUID jornada = jdbc.sql("SELECT jornada_id FROM siglim.partidos WHERE equipo_local_id = :local")
                .param("local", local).query(UUID.class).single();

        iniciarSesion(presidente);
        Integer nuevos = new TransactionTemplate(transacciones).execute(estado ->
                cobrosAutomaticos.alPublicarJornada(new JornadaPublicada(LIGA, jornada, presidente)));

        assertThat(nuevos).isZero();
        assertThat(cobrosDe(local, visitante)).hasSize(4);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    /** El Programador arma y envía la jornada con un partido; el Presidente la publica. */
    private UUID publicarJornadaCon(Grupo grupo, UUID local, UUID visitante, UUID cancha) {
        iniciarSesion(perfilConRol("PROGRAMADOR"));
        UUID jornada = jornadas.crear(new CrearJornadaRequest(grupo.id(), null, 1)).id();
        jornadas.registrarPartido(jornada, new CrearPartidoRequest(local, visitante, cancha, INICIO));
        jornadas.enviarAAprobacion(jornada);
        UUID presidente = iniciarSesion(perfilConRol("PRESIDENTE"));
        jornadas.resolver(jornada, new ResolverJornadaRequest(DecisionJornada.PUBLICADO, null));
        return presidente;
    }

    private record Grupo(UUID id, UUID temporada, UUID categoria) {
    }

    private record Cobro(UUID id, UUID clubId, UUID equipoId, UUID partidoId, String tipo, String concepto,
                         BigDecimal monto, LocalDate fechaLimite, String estado, UUID creadoPor) {
    }

    private List<Cobro> cobrosDe(UUID... equipos) {
        return jdbc.sql("""
                        SELECT id, club_id, equipo_id, partido_id, tipo, concepto, monto, fecha_limite, estado,
                               creado_por
                          FROM siglim.cobros
                         WHERE equipo_id = ANY(:equipos)
                        """)
                .param("equipos", new SqlArrayValue("uuid", (Object[]) equipos))
                .query((fila, numero) -> new Cobro(fila.getObject("id", UUID.class),
                        fila.getObject("club_id", UUID.class), fila.getObject("equipo_id", UUID.class),
                        fila.getObject("partido_id", UUID.class), fila.getString("tipo"), fila.getString("concepto"),
                        fila.getBigDecimal("monto"), fila.getObject("fecha_limite", LocalDate.class),
                        fila.getString("estado"), fila.getObject("creado_por", UUID.class)))
                .list();
    }

    private UUID clubDe(UUID equipo) {
        return jdbc.sql("SELECT club_id FROM siglim.equipos WHERE id = :id")
                .param("id", equipo).query(UUID.class).single();
    }

    private UUID partidoDe(UUID local) {
        return jdbc.sql("SELECT id FROM siglim.partidos WHERE equipo_local_id = :local")
                .param("local", local).query(UUID.class).single();
    }

    private List<String> accionesAuditadas(UUID entidad) {
        return jdbc.sql("SELECT accion FROM siglim.auditoria WHERE entidad_id = :id ORDER BY id")
                .param("id", entidad.toString()).query(String.class).list();
    }

    private void fijarTarifa(Grupo grupo, String concepto, String monto) {
        jdbc.sql("""
                        INSERT INTO siglim.tarifas (temporada_id, categoria_id, concepto, monto)
                        VALUES (:temporada, :categoria, CAST(:concepto AS siglim.concepto_tarifa), :monto)
                        """)
                .param("temporada", grupo.temporada())
                .param("categoria", grupo.categoria())
                .param("concepto", concepto)
                .param("monto", new BigDecimal(monto))
                .update();
    }

    private UUID iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
        return perfil;
    }

    /** Crea una cuenta con el rol sembrado, con ámbito de liga. */
    private UUID perfilConRol(String codigoRol) {
        UUID usuarioAuth = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO auth.users (id, email, aud, role)
                        VALUES (:id, :correo, 'authenticated', 'authenticated')
                        """)
                .param("id", usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .update();
        UUID perfil = jdbc.sql("""
                        INSERT INTO siglim.perfiles (usuario_auth_id, nombre_mostrado, correo)
                        VALUES (:usuario, :nombre, :correo)
                        RETURNING id
                        """)
                .param("usuario", usuarioAuth)
                .param("nombre", codigoRol + " " + usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, r.id, r.liga_id, 'LIGA'
                          FROM siglim.roles r
                         WHERE r.codigo = :rol
                        """)
                .param("perfil", perfil)
                .param("rol", codigoRol)
                .update();
        return perfil;
    }

    /** Temporada con parámetros, categoría y grupo: lo mínimo para programar un partido. */
    private Grupo crearGrupo() {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2031-08-01')
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", "Temporada " + UUID.randomUUID())
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.parametros_temporada (temporada_id, version, motivo)
                        VALUES (:temporada, 1, 'Parámetros de prueba')
                        """)
                .param("temporada", temporada).update();
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", "Categoría " + UUID.randomUUID())
                .query(UUID.class).single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria)
                        RETURNING id
                        """)
                .param("temporada", temporada)
                .param("categoria", categoria)
                .query(UUID.class).single();
        UUID grupo = jdbc.sql("""
                        INSERT INTO siglim.grupos (categoria_temporada_id, nombre)
                        VALUES (:ct, 'A')
                        RETURNING id
                        """)
                .param("ct", categoriaTemporada)
                .query(UUID.class).single();
        return new Grupo(grupo, temporada, categoria);
    }

    private UUID crearEquipo(Grupo grupo, String nombre) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID())
                .query(UUID.class).single();
        return jdbc.sql("""
                        INSERT INTO siglim.equipos (club_id, grupo_id, nombre)
                        VALUES (:club, :grupo, :nombre)
                        RETURNING id
                        """)
                .param("club", club).param("grupo", grupo.id()).param("nombre", nombre + " " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID crearCanchaDeLaLiga() {
        return jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario)
                        VALUES (:liga, :nombre, 'UNIDAD_DEPORTIVA', 'LIGA')
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Cancha " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    /** Cancha propia del club del equipo (DEC-33). */
    private UUID crearCanchaDelClub(UUID equipo) {
        return jdbc.sql("""
                        INSERT INTO siglim.canchas (liga_id, nombre, ubicacion, propietario, club_id)
                        SELECT :liga, :nombre, 'EXTERNA', 'CLUB', e.club_id FROM siglim.equipos e WHERE e.id = :equipo
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Campo " + UUID.randomUUID()).param("equipo", equipo)
                .query(UUID.class).single();
    }
}
