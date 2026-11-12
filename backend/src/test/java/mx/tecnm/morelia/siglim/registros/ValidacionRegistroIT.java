package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.CrearPersonaRequest;
import mx.tecnm.morelia.siglim.organizacion.PersonaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * H-10.2 contra PostgreSQL con RLS y {@code tg_registros_validar}: criterios 3 y 5 de H-10. La liga
 * activa un registro con todos sus documentos aprobados y queda el historial con el motivo; con un
 * documento pendiente responde {@code 422 VAL-001}; el rechazo guarda las observaciones y deja dar de
 * alta otro registro de la persona; y el trigger impide que un usuario de club valide (VAL-015) o que
 * la liga active sin documentos (VAL-001) desde la base (DEC-101).
 */
class ValidacionRegistroIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
    private static final String OBSERVACIONES = "La CURP no corresponde a la persona del registro";

    @Autowired
    private RegistroJugadorService registros;

    @Autowired
    private DocumentoRegistroService documentos;

    @Autowired
    private PersonaService personas;

    @Autowired
    private MotivoOperacion motivoOperacion;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Test
    @DisplayName("H-10 criterios 3 y 5 · RF-109 · VAL-001: con todos los documentos aprobados la liga activa el registro")
    void activaConDocumentosAprobados() {
        Escenario escenario = escenario();
        UUID registro = escenario.registro();
        List<DocumentoDto> cargados = cargarTodos(escenario);

        iniciarSesion(escenario.secretario());
        for (DocumentoDto documento : cargados) {
            documentos.validar(documento.id(), new ValidarDocumentoRequest(DecisionDocumento.APROBADO, null));
        }
        RegistroJugadorDto activo = registros.validar(registro, new ValidarRegistroRequest(DecisionRegistro.ACTIVO, null));

        assertThat(activo.estado()).isEqualTo(EstadoRegistro.ACTIVO);
        assertThat(activo.validadoPor().id()).isEqualTo(escenario.secretario());
        assertThat(activo.validadoPor().nombre()).isEqualTo("Secretario de prueba");
        assertThat(activo.validadoEn()).isNotNull();
        assertThat(activo.observaciones()).isNull();
        Map<String, Object> fila = jdbc.sql("""
                        SELECT estado::text AS estado, validado_por, validado_en, observaciones
                          FROM siglim.registros_jugador WHERE id = :id
                        """).param("id", registro).query().singleRow();
        assertThat(fila.get("estado")).isEqualTo("ACTIVO");
        assertThat(fila.get("validado_por")).isEqualTo(escenario.secretario());
        assertThat(fila.get("validado_en")).isNotNull();
        assertThat(fila.get("observaciones")).isNull();
        assertThat(ultimoHistorial(registro)).isEqualTo("PENDIENTE_VALIDACION → ACTIVO: Registro validado por la liga");

        String auditoria = jdbc.sql("""
                        SELECT accion || ' ' || detalle::text || ' ' || motivo
                          FROM siglim.auditoria WHERE entidad = 'registros_jugador' AND entidad_id = :id
                           AND accion = 'VALIDAR_REGISTRO_JUGADOR'
                        """).param("id", registro.toString()).query(String.class).single();
        // El evento va sin motivo; AuditoriaService guarda el que se fijó con MotivoOperacion.
        assertThat(auditoria).contains("\"estado\": \"ACTIVO\"", "\"estadoAnterior\": \"PENDIENTE_VALIDACION\"")
                .endsWith(" Registro validado por la liga").doesNotContain("Nombre", "Validacion", "1990-04-04");
    }

    @Test
    @DisplayName("H-10 criterio 3 · VAL-001: con un documento pendiente responde 422 con faltantes y el registro sigue pendiente")
    void documentoPendiente() {
        Escenario escenario = escenario();
        List<DocumentoDto> cargados = cargarTodos(escenario);

        iniciarSesion(escenario.secretario());
        for (DocumentoDto documento : cargados.subList(0, 2)) {
            documentos.validar(documento.id(), new ValidarDocumentoRequest(DecisionDocumento.APROBADO, null));
        }
        assertThatThrownBy(() -> registros.validar(escenario.registro(),
                new ValidarRegistroRequest(DecisionRegistro.ACTIVO, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-001");
                    assertThat(error.propiedades().get("faltantes")).isEqualTo(List.of(
                            new RegistroJugadorService.DocumentoFaltante(cargados.get(2).tipoDocumento(), 2, 2, 1)));
                });

        assertThat(estado(escenario.registro())).isEqualTo("PENDIENTE_VALIDACION");
    }

    @Test
    @DisplayName("H-10 criterio 5 · RF-109 · DEC-100: el rechazo guarda las observaciones y deja dar de alta otro registro")
    void rechazoConObservaciones() {
        Escenario escenario = escenario();

        iniciarSesion(escenario.secretario());
        RegistroJugadorDto rechazado = registros.validar(escenario.registro(),
                new ValidarRegistroRequest(DecisionRegistro.RECHAZADO, "  " + OBSERVACIONES + "  "));

        assertThat(rechazado.estado()).isEqualTo(EstadoRegistro.RECHAZADO);
        assertThat(rechazado.observaciones()).isEqualTo(OBSERVACIONES);
        assertThat(ultimoHistorial(escenario.registro())).isEqualTo("PENDIENTE_VALIDACION → RECHAZADO: " + OBSERVACIONES);
        assertThat(jdbc.sql("""
                        SELECT motivo FROM siglim.auditoria
                         WHERE entidad_id = :id AND accion = 'VALIDAR_REGISTRO_JUGADOR'
                        """).param("id", escenario.registro().toString()).query(String.class).single())
                .isEqualTo(OBSERVACIONES);

        iniciarSesion(escenario.directiva());
        RegistroJugadorDto nuevo = registros.registrar(new CrearRegistroJugadorRequest(escenario.persona(),
                escenario.equipo()));
        assertThat(nuevo.id()).isNotEqualTo(escenario.registro());
        assertThat(nuevo.estado()).isEqualTo(EstadoRegistro.PENDIENTE_VALIDACION);
        assertThat(nuevo.validadoPor()).isNull();
    }

    @Test
    @DisplayName("DEC-101 · VAL-015 · VAL-001: el trigger impide validar a un usuario de club y activar sin documentos")
    void triggerComoUltimaGarantia() {
        Escenario escenario = escenario();
        String activar = "UPDATE siglim.registros_jugador SET estado = 'ACTIVO' WHERE id = :id";

        iniciarSesion(escenario.directiva());
        assertThatThrownBy(() -> enTransaccion(() -> {
            motivoOperacion.fijar("La Directiva intenta activar");
            jdbc.sql(activar).param("id", escenario.registro()).update();
        })).hasStackTraceContaining("VAL-015");

        iniciarSesion(escenario.secretario());
        assertThatThrownBy(() -> enTransaccion(() -> {
            motivoOperacion.fijar("Activación directa sin documentos");
            jdbc.sql(activar).param("id", escenario.registro()).update();
        })).hasStackTraceContaining("VAL-001");

        assertThat(estado(escenario.registro())).isEqualTo("PENDIENTE_VALIDACION");
    }

    // ── Escenario ───────────────────────────────────────────────────────────────────────────────

    /** Registro pendiente de un adulto en una categoría LIBRE, con la Directiva de su club y el Secretario. */
    private record Escenario(UUID registro, UUID persona, UUID equipo, UUID directiva, UUID secretario) {
    }

    private Escenario escenario() {
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
        String curp = "VALI" + digitos + "HMNRRS0" + ThreadLocalRandom.current().nextInt(10);
        UUID persona = personas.registrar(new CrearPersonaRequest(curp, "Nombre", "Validacion", null,
                LocalDate.parse("1990-04-04"), null, null)).id();
        UUID registro = registros.registrar(new CrearRegistroJugadorRequest(persona, equipo)).id();
        SecurityContextHolder.clearContext();

        return new Escenario(registro, persona, equipo, directiva, perfilDeSecretario());
    }

    /** La Directiva carga la identificación y las dos fotografías que exige LIBRE. */
    private List<DocumentoDto> cargarTodos(Escenario escenario) {
        iniciarSesion(escenario.directiva());
        return List.of(
                documentos.cargar(escenario.registro(), tipo("IDENTIFICACION"), JPEG),
                documentos.cargar(escenario.registro(), tipo("FOTOGRAFIA"), JPEG),
                documentos.cargar(escenario.registro(), tipo("FOTOGRAFIA"), JPEG));
    }

    private void enTransaccion(Runnable accion) {
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> accion.run());
    }

    private String estado(UUID registro) {
        return jdbc.sql("SELECT estado::text FROM siglim.registros_jugador WHERE id = :id")
                .param("id", registro).query(String.class).single();
    }

    private String ultimoHistorial(UUID registro) {
        return jdbc.sql("""
                        SELECT estado_anterior::text || ' → ' || estado_nuevo::text || ': ' || motivo
                          FROM siglim.registro_estado_historial WHERE registro_id = :id
                         ORDER BY id DESC LIMIT 1
                        """).param("id", registro).query(String.class).single();
    }

    private UUID club(UUID temporada) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club Validación " + UUID.randomUUID()).query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.afiliaciones (club_id, temporada_id, folio, colores_uniforme, estado,
                                                         capturada_por)
                        VALUES (:club, :temporada, :folio, 'Azul', 'APROBADA', :perfil)
                        """)
                .param("club", club).param("temporada", temporada)
                .param("folio", "V-" + ThreadLocalRandom.current().nextInt(1_000_000_000))
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

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}
