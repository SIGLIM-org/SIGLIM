package mx.tecnm.morelia.siglim.temporadas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * B-02.1 contra PostgreSQL real, con {@link ConvocatoriaService} completo: autorización por
 * permiso, el {@code UNIQUE (temporada_id)} de {@code convocatorias}, la temporada cerrada
 * (RF-003) y la auditoría.
 */
class ConvocatoriasIT extends BaseDatosPrueba {

    private static final LocalDate INICIO = LocalDate.parse("2032-08-01");
    private static final LocalDate FIN = LocalDate.parse("2032-12-15");
    private static final String MOTIVO = "Fin del calendario deportivo";
    private static final String ENLACE = "https://liga.mx/convocatoria-2032.pdf";

    @Autowired
    private ConvocatoriaService convocatorias;

    @Autowired
    private TemporadaService temporadas;

    @BeforeEach
    void cerrarTemporadasActivas() {
        jdbc.sql("UPDATE siglim.temporadas SET estado = 'CERRADA' WHERE estado = 'ACTIVA'").update();
    }

    @Test
    @DisplayName("B-02.1: el Secretario asocia y reemplaza la convocatoria y queda en la temporada")
    void secretarioAsociaYReemplaza() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = nuevaTemporada();
        UUID secretario = perfilConRol("SECRETARIO");
        iniciarSesion(secretario);

        ConvocatoriaDto primera = convocatorias.reemplazar(temporada.id(),
                new ReemplazarConvocatoriaRequest("Convocatoria 2032", "2026-1", ENLACE));

        assertThat(primera.temporadaId()).isEqualTo(temporada.id());
        assertThat(primera.referencia()).isEqualTo("Convocatoria 2032");
        assertThat(primera.version()).isEqualTo("2026-1");
        assertThat(primera.documentoUrl()).isEqualTo(ENLACE);
        assertThat(temporadas.consultar(temporada.id()).convocatoria()).isEqualTo(primera);

        ConvocatoriaDto segunda = convocatorias.reemplazar(temporada.id(),
                new ReemplazarConvocatoriaRequest("Convocatoria 2032 revisada", null, null));

        assertThat(segunda.id()).as("reemplaza la misma fila").isEqualTo(primera.id());
        assertThat(segunda.version()).isNull();
        assertThat(segunda.documentoUrl()).isNull();
        assertThat(temporadas.consultar(temporada.id()).convocatoria().referencia())
                .isEqualTo("Convocatoria 2032 revisada");
        assertThat(contarConvocatorias(temporada.id())).isEqualTo(1);

        List<Evento> eventos = eventosDe(primera.id());
        assertThat(eventos).extracting(Evento::accion)
                .containsExactly("ASOCIAR_CONVOCATORIA", "ASOCIAR_CONVOCATORIA");
        assertThat(eventos).allSatisfy(evento -> assertThat(evento.perfilId()).isEqualTo(secretario));

        // El reemplazo sobrescribe la fila: la convocatoria anterior solo queda en la auditoría.
        String detalle = jdbc.sql("""
                        SELECT detalle::text
                          FROM siglim.auditoria
                         WHERE modulo = 'M-02' AND entidad = 'convocatorias' AND entidad_id = :id
                         ORDER BY id DESC
                         LIMIT 1
                        """)
                .param("id", primera.id().toString())
                .query(String.class)
                .single();
        assertThat(detalle).contains("Convocatoria 2032", "2026-1", ENLACE);
    }

    @Test
    @DisplayName("RF-003: una temporada cerrada no admite convocatoria")
    void temporadaCerrada() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = temporadas.cerrar(crearYActivar().id(), new CerrarTemporadaRequest(MOTIVO));
        iniciarSesion(perfilConRol("SECRETARIO"));

        assertThatThrownBy(() -> convocatorias.reemplazar(temporada.id(),
                new ReemplazarConvocatoriaRequest("Convocatoria 2032", null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        assertThat(contarConvocatorias(temporada.id())).isZero();
    }

    @Test
    @DisplayName("VAL-015: el Programador consulta temporadas pero no asocia su convocatoria")
    void programadorSinPermiso() {
        iniciarSesionComoAdministrador();
        TemporadaDto temporada = nuevaTemporada();
        iniciarSesion(perfilConRol("PROGRAMADOR"));

        assertThatThrownBy(() -> convocatorias.reemplazar(temporada.id(),
                new ReemplazarConvocatoriaRequest("Convocatoria 2032", null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        assertThat(contarConvocatorias(temporada.id())).isZero();
    }

    @Test
    @DisplayName("VAL-024: una temporada inexistente responde 404")
    void temporadaInexistente() {
        iniciarSesionComoAdministrador();

        assertThatThrownBy(() -> convocatorias.reemplazar(UUID.randomUUID(),
                new ReemplazarConvocatoriaRequest("Convocatoria 2032", null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private TemporadaDto nuevaTemporada() {
        return temporadas.crear(new CrearTemporadaRequest("Convocatoria " + UUID.randomUUID(), INICIO, FIN));
    }

    private TemporadaDto crearYActivar() {
        TemporadaDto temporada = nuevaTemporada();
        jdbc.sql("INSERT INTO siglim.parametros_temporada (temporada_id, motivo) VALUES (:id, 'Parámetros de prueba')")
                .param("id", temporada.id()).update();
        UUID categoriaTemporada = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT t.liga_id, :nombre, 'LIBRE',
                               coalesce((SELECT max(orden) FROM siglim.categorias), 0) + 1
                          FROM siglim.temporadas t
                         WHERE t.id = :temporada
                        RETURNING id
                        """)
                .param("nombre", "Categoría " + UUID.randomUUID())
                .param("temporada", temporada.id())
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria)
                        """)
                .param("temporada", temporada.id()).param("categoria", categoriaTemporada).update();
        jdbc.sql("""
                        INSERT INTO siglim.grupos (categoria_temporada_id, nombre)
                        SELECT ct.id, 'A' FROM siglim.categorias_temporada ct
                         WHERE ct.temporada_id = :temporada
                         LIMIT 1
                        """)
                .param("temporada", temporada.id()).update();
        return temporadas.activar(temporada.id());
    }

    private long contarConvocatorias(UUID temporada) {
        return jdbc.sql("SELECT count(*) FROM siglim.convocatorias WHERE temporada_id = :id")
                .param("id", temporada).query(Long.class).single();
    }

    private List<Evento> eventosDe(UUID convocatoria) {
        return jdbc.sql("""
                        SELECT accion, perfil_id
                          FROM siglim.auditoria
                         WHERE modulo = 'M-02' AND entidad = 'convocatorias' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", convocatoria.toString())
                .query((fila, numero) -> new Evento(fila.getString("accion"),
                        fila.getObject("perfil_id", UUID.class)))
                .list();
    }

    private UUID perfilConRol(String rol) {
        UUID perfil = jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
        return perfil;
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private record Evento(String accion, UUID perfilId) {
    }
}
