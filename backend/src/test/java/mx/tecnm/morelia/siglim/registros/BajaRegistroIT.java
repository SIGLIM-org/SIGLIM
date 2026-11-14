package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.CrearPersonaRequest;
import mx.tecnm.morelia.siglim.organizacion.PersonaService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-23.1 contra PostgreSQL con RLS, {@code tg_registros_validar}, {@code ck_registros_baja} y
 * {@code tg_registros_historial}: la Directiva da de baja un registro {@code ACTIVO} con motivo, que
 * queda en el historial con su usuario; las observaciones de la validación no cambian; sin motivo se
 * rechaza sin tocar el registro; solo un registro activo admite baja; y el ámbito y el permiso se
 * respetan (RF-014, RN-021, DEC-118 a DEC-124).
 */
class BajaRegistroIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
    private static final String OBSERVACIONES = "Documentos revisados en ventanilla";
    private static final String MOTIVO = "El jugador se mudó de ciudad";

    @Autowired
    private RegistroJugadorService registros;

    @Autowired
    private DocumentoRegistroService documentos;

    @Autowired
    private PersonaService personas;

    @Test
    @DisplayName("H-23 · RF-014 · RN-021: la Directiva da de baja un registro ACTIVO; queda el historial con su motivo y usuario")
    void daDeBajaUnRegistroActivo() {
        Escenario escenario = escenarioActivo();

        iniciarSesion(escenario.directiva());
        RegistroJugadorDto deBaja = registros.darDeBaja(escenario.registro(), new BajaRegistroRequest("  " + MOTIVO + "  "));

        LocalDate hoy = LocalDate.now(ZoneId.of("America/Mexico_City"));
        assertThat(deBaja.estado()).isEqualTo(EstadoRegistro.BAJA);
        assertThat(deBaja.bajaEn()).isEqualTo(hoy);
        assertThat(deBaja.motivoBaja()).isEqualTo(MOTIVO);
        assertThat(deBaja.observaciones()).isEqualTo(OBSERVACIONES);
        assertThat(deBaja.validadoPor().id()).isEqualTo(escenario.secretario());

        Map<String, Object> fila = jdbc.sql("""
                        SELECT estado::text AS estado, baja_en, observaciones, validado_por
                          FROM siglim.registros_jugador WHERE id = :id
                        """).param("id", escenario.registro()).query().singleRow();
        assertThat(fila.get("estado")).isEqualTo("BAJA");
        assertThat(fila.get("baja_en").toString()).isEqualTo(hoy.toString());
        assertThat(fila.get("observaciones")).isEqualTo(OBSERVACIONES);
        assertThat(fila.get("validado_por")).isEqualTo(escenario.secretario());

        Map<String, Object> historial = jdbc.sql("""
                        SELECT estado_anterior::text || ' → ' || estado_nuevo::text || ': ' || motivo AS cambio,
                               cambiado_por
                          FROM siglim.registro_estado_historial WHERE registro_id = :id
                         ORDER BY id DESC LIMIT 1
                        """).param("id", escenario.registro()).query().singleRow();
        assertThat(historial.get("cambio")).isEqualTo("ACTIVO → BAJA: " + MOTIVO);
        assertThat(historial.get("cambiado_por")).isEqualTo(escenario.directiva());

        RegistroJugadorDetalleDto detalle = registros.consultar(escenario.registro());
        assertThat(detalle.estado()).isEqualTo(EstadoRegistro.BAJA);
        assertThat(detalle.bajaEn()).isEqualTo(hoy);
        assertThat(detalle.motivoBaja()).isEqualTo(MOTIVO);
        assertThat(detalle.observaciones()).isEqualTo(OBSERVACIONES);

        String auditoria = jdbc.sql("""
                        SELECT detalle::text || ' ' || motivo
                          FROM siglim.auditoria WHERE entidad = 'registros_jugador' AND entidad_id = :id
                           AND accion = 'DAR_BAJA_REGISTRO_JUGADOR'
                        """).param("id", escenario.registro().toString()).query(String.class).single();
        assertThat(auditoria).contains("\"estadoAnterior\": \"ACTIVO\"", "\"estado\": \"BAJA\"",
                        "\"bajaEn\": \"" + hoy + "\"")
                .endsWith(" " + MOTIVO).doesNotContain("Nombre", "Bajado", "1990-04-04");
    }

    @Test
    @DisplayName("RN-021 · DEC-121: sin motivo responde 400 y el registro sigue ACTIVO sin historial nuevo")
    void sinMotivo() {
        Escenario escenario = escenarioActivo();
        long cambios = cambiosDeEstado(escenario.registro());

        iniciarSesion(escenario.directiva());
        esperar(() -> registros.darDeBaja(escenario.registro(), new BajaRegistroRequest("   ")),
                HttpStatus.BAD_REQUEST, "RN-021");

        assertThat(estado(escenario.registro())).isEqualTo("ACTIVO");
        assertThat(cambiosDeEstado(escenario.registro())).isEqualTo(cambios);
    }

    @Test
    @DisplayName("RF-017 · DEC-118: un registro PENDIENTE_VALIDACION no se da de baja")
    void pendienteNoSeDaDeBaja() {
        Escenario escenario = escenarioPendiente();

        iniciarSesion(escenario.directiva());
        esperar(() -> registros.darDeBaja(escenario.registro(), new BajaRegistroRequest(MOTIVO)),
                HttpStatus.CONFLICT, "RF-017");

        assertThat(estado(escenario.registro())).isEqualTo("PENDIENTE_VALIDACION");
    }

    @Test
    @DisplayName("VAL-024 · VAL-015 · DEC-119: la Directiva de otro club recibe 404 y el Secretario 403")
    void ambitoYPermiso() {
        Escenario escenario = escenarioActivo();

        UUID otroClub = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Otro club " + UUID.randomUUID()).query(UUID.class).single();
        iniciarSesion(perfilDeDirectiva(otroClub));
        assertThatThrownBy(() -> registros.darDeBaja(escenario.registro(), new BajaRegistroRequest(MOTIVO)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        iniciarSesion(escenario.secretario());
        esperar(() -> registros.darDeBaja(escenario.registro(), new BajaRegistroRequest(MOTIVO)),
                HttpStatus.FORBIDDEN, "VAL-015");

        assertThat(estado(escenario.registro())).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("RF-017 · DEC-118: una segunda baja responde 409 y no agrega historial")
    void segundaBaja() {
        Escenario escenario = escenarioActivo();

        iniciarSesion(escenario.directiva());
        registros.darDeBaja(escenario.registro(), new BajaRegistroRequest(MOTIVO));
        long cambios = cambiosDeEstado(escenario.registro());

        assertThatThrownBy(() -> registros.darDeBaja(escenario.registro(), new BajaRegistroRequest(MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("RF-017");
                    assertThat(error.getMessage()).isEqualTo("El registro ya está dado de baja.");
                });
        assertThat(cambiosDeEstado(escenario.registro())).isEqualTo(cambios);
    }

    // ── Escenario ───────────────────────────────────────────────────────────────────────────────

    /** Registro de un adulto en una categoría LIBRE, con la Directiva de su club y el Secretario. */
    private record Escenario(UUID registro, UUID directiva, UUID secretario) {
    }

    /** El registro pendiente con sus documentos aprobados y activado por el Secretario con observaciones. */
    private Escenario escenarioActivo() {
        Escenario escenario = escenarioPendiente();
        iniciarSesion(escenario.directiva());
        List<DocumentoDto> cargados = List.of(
                documentos.cargar(escenario.registro(), tipo("IDENTIFICACION"), JPEG),
                documentos.cargar(escenario.registro(), tipo("FOTOGRAFIA"), JPEG),
                documentos.cargar(escenario.registro(), tipo("FOTOGRAFIA"), JPEG));

        iniciarSesion(escenario.secretario());
        for (DocumentoDto documento : cargados) {
            documentos.validar(documento.id(), new ValidarDocumentoRequest(DecisionDocumento.APROBADO, null));
        }
        registros.validar(escenario.registro(), new ValidarRegistroRequest(DecisionRegistro.ACTIVO, OBSERVACIONES));
        SecurityContextHolder.clearContext();
        return escenario;
    }

    private Escenario escenarioPendiente() {
        UUID temporada = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio)
                        VALUES (:liga, :nombre, DATE '2035-08-01') RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Temporada " + UUID.randomUUID()).query(UUID.class).single();
        UUID club = club(temporada);
        UUID equipo = equipoLibre(club, temporada);
        UUID directiva = perfilDeDirectiva(club);

        iniciarSesion(directiva);
        String digitos = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        String curp = "BAJA" + digitos + "HMNRRS0" + ThreadLocalRandom.current().nextInt(10);
        UUID persona = personas.registrar(new CrearPersonaRequest(curp, "Nombre", "Bajado", null,
                LocalDate.parse("1990-04-04"), null, null)).id();
        UUID registro = registros.registrar(new CrearRegistroJugadorRequest(persona, equipo)).id();
        SecurityContextHolder.clearContext();

        return new Escenario(registro, directiva, perfilDeSecretario());
    }

    private String estado(UUID registro) {
        return jdbc.sql("SELECT estado::text FROM siglim.registros_jugador WHERE id = :id")
                .param("id", registro).query(String.class).single();
    }

    private long cambiosDeEstado(UUID registro) {
        return jdbc.sql("SELECT count(*) FROM siglim.registro_estado_historial WHERE registro_id = :id")
                .param("id", registro).query(Long.class).single();
    }

    private UUID club(UUID temporada) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club Baja " + UUID.randomUUID()).query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.afiliaciones (club_id, temporada_id, folio, colores_uniforme, estado,
                                                         capturada_por)
                        VALUES (:club, :temporada, :folio, 'Azul', 'APROBADA', :perfil)
                        """)
                .param("club", club).param("temporada", temporada)
                .param("folio", "B-" + ThreadLocalRandom.current().nextInt(1_000_000_000))
                .param("perfil", crearPerfil("Capturista de prueba")).update();
        return club;
    }

    private UUID equipoLibre(UUID club, UUID temporada) {
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA).param("nombre", "Libre " + UUID.randomUUID()).query(UUID.class).single();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria) RETURNING id
                        """)
                .param("temporada", temporada).param("categoria", categoria).query(UUID.class).single();
        UUID grupo = jdbc.sql("INSERT INTO siglim.grupos (categoria_temporada_id, nombre) VALUES (:ct, 'A') RETURNING id")
                .param("ct", categoriaTemporada).query(UUID.class).single();
        return jdbc.sql("INSERT INTO siglim.equipos (club_id, grupo_id, nombre) VALUES (:club, :grupo, :nombre) RETURNING id")
                .param("club", club).param("grupo", grupo).param("nombre", "Libre " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private long tipo(String codigo) {
        return jdbc.sql("SELECT id FROM siglim.tipos_documento WHERE codigo = :codigo")
                .param("codigo", codigo).query(Long.class).single();
    }

    private UUID perfilDeDirectiva(UUID club) {
        UUID perfil = crearPerfil("Directiva de prueba");
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil).param("club", club).update();
        return perfil;
    }

    private UUID perfilDeSecretario() {
        UUID perfil = crearPerfil("Secretario de prueba");
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, 'LIGA' FROM siglim.roles WHERE codigo = 'SECRETARIO'
                        """)
                .param("perfil", perfil).update();
        return perfil;
    }

    private UUID crearPerfil(String nombre) {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES (:nombre) RETURNING id")
                .param("nombre", nombre).query(UUID.class).single();
    }

    private static void esperar(ThrowingCallable accion, HttpStatus estado, String codigo) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.estado()).isEqualTo(estado);
            assertThat(error.codigo()).isEqualTo(codigo);
        });
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}
