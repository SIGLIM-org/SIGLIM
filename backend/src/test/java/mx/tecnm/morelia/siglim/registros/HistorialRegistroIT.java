package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.CrearPersonaRequest;
import mx.tecnm.morelia.siglim.organizacion.PersonaService;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * B-07.1 contra PostgreSQL con RLS y {@code tg_registros_historial}: el historial de un registro
 * lista el alta, la validación y la baja del más antiguo al más reciente, con su motivo y su
 * usuario; la Directiva del club lo ve, la de otro club recibe {@code 404} igual que un registro
 * inexistente, y una cuenta sin el permiso recibe {@code 403} (RF-018, VAL-015, VAL-024).
 */
class HistorialRegistroIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
    private static final String OBSERVACIONES = "Documentos revisados en ventanilla";
    private static final String MOTIVO_BAJA = "El jugador se mudó de ciudad";

    @Autowired
    private RegistroJugadorService registros;

    @Autowired
    private DocumentoRegistroService documentos;

    @Autowired
    private PersonaService personas;

    @Test
    @DisplayName("RF-018 · B-07.1: el Secretario ve el alta, la validación y la baja en orden, con motivo y usuario")
    void historialEnOrden() {
        Escenario escenario = escenarioDeBaja();

        iniciarSesion(escenario.secretario());
        List<CambioEstadoRegistroDto> historial = registros.historial(escenario.registro());

        assertThat(historial).extracting(CambioEstadoRegistroDto::estadoAnterior)
                .containsExactly(null, EstadoRegistro.PENDIENTE_VALIDACION, EstadoRegistro.ACTIVO);
        assertThat(historial).extracting(CambioEstadoRegistroDto::estadoNuevo)
                .containsExactly(EstadoRegistro.PENDIENTE_VALIDACION, EstadoRegistro.ACTIVO, EstadoRegistro.BAJA);
        assertThat(historial).extracting(CambioEstadoRegistroDto::motivo)
                .containsExactly("Alta del registro", OBSERVACIONES, MOTIVO_BAJA);
        assertThat(historial).extracting(CambioEstadoRegistroDto::cambiadoPor).containsExactly(
                new ReferenciaDto(escenario.directiva(), "Directiva de prueba"),
                new ReferenciaDto(escenario.secretario(), "Secretario de prueba"),
                new ReferenciaDto(escenario.directiva(), "Directiva de prueba"));
        assertThat(historial).extracting(CambioEstadoRegistroDto::cambiadoEn)
                .allSatisfy(momento -> assertThat(momento.getOffset().getTotalSeconds()).isEqualTo(-6 * 3600))
                .isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    @DisplayName("RF-018 · VAL-024: la Directiva ve el historial de su club; la de otro club recibe 404 como uno inexistente")
    void ambitoClub() {
        Escenario escenario = escenarioDeBaja();

        iniciarSesion(escenario.directiva());
        List<CambioEstadoRegistroDto> propio = registros.historial(escenario.registro());
        assertThat(propio).hasSize(3);
        assertThat(propio.get(1).cambiadoPor().id()).isEqualTo(escenario.secretario());

        UUID otroClub = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Otro club " + UUID.randomUUID()).query(UUID.class).single();
        iniciarSesion(perfilDeDirectiva(otroClub));
        assertThatThrownBy(() -> registros.historial(escenario.registro()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> registros.historial(UUID.randomUUID()))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("VAL-015: una cuenta sin registros.registro:CONSULTAR recibe 403")
    void sinPermiso() {
        Escenario escenario = escenarioPendiente();

        iniciarSesion(crearPerfil("Sin roles"));
        assertThatThrownBy(() -> registros.historial(escenario.registro()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.codigo()).isEqualTo("VAL-015");
                });
    }

    // ── Escenario ───────────────────────────────────────────────────────────────────────────────

    /** Registro de un adulto en una categoría LIBRE, con la Directiva de su club y el Secretario. */
    private record Escenario(UUID registro, UUID directiva, UUID secretario) {
    }

    /** El registro activado por el Secretario y dado de baja por la Directiva: tres cambios. */
    private Escenario escenarioDeBaja() {
        Escenario escenario = escenarioActivo();
        iniciarSesion(escenario.directiva());
        registros.darDeBaja(escenario.registro(), new BajaRegistroRequest(MOTIVO_BAJA));
        SecurityContextHolder.clearContext();
        return escenario;
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
        String curp = "HIST" + digitos + "HMNRRS0" + ThreadLocalRandom.current().nextInt(10);
        UUID persona = personas.registrar(new CrearPersonaRequest(curp, "Nombre", "Historiado", null,
                LocalDate.parse("1990-04-04"), null, null)).id();
        UUID registro = registros.registrar(new CrearRegistroJugadorRequest(persona, equipo)).id();
        SecurityContextHolder.clearContext();

        return new Escenario(registro, directiva, perfilDeSecretario());
    }



    private UUID club(UUID temporada) {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club Historial " + UUID.randomUUID()).query(UUID.class).single();
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


    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}
