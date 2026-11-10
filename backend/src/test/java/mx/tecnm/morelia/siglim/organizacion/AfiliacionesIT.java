package mx.tecnm.morelia.siglim.organizacion;

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
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-08.1 contra PostgreSQL real, con {@link AfiliacionService} completo: autorización con la matriz
 * de la migración, SQL, restricciones y auditoría. Demuestra los criterios de H-08 que cubren los
 * endpoints: folio y estado inicial, resolución con perfil y fecha, rechazo con observaciones, y
 * ámbito por club con {@code 404} para otro club (RF-101, VAL-024).
 *
 * <p>La temporada y la categoría se crean por SQL como dueño del esquema: su CRUD es de M-02. Se
 * usan temporadas en {@code CONFIGURACION} para no chocar con la única temporada activa de RN-058.
 */
class AfiliacionesIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final String COLORES = "Azul y blanco";
    private static final String OBSERVACIONES = "Faltan las firmas de la directiva";

    @Autowired
    private AfiliacionService afiliaciones;

    @Autowired
    private ClubService clubes;

    // ── Criterio 1 y 2: folio, estado y resolución ───────────────────────────────────────────

    @Test
    @DisplayName("H-08 criterios 1 y 2 · RF-101: el SECRETARIO captura con folio y el PRESIDENTE aprueba")
    void secretarioCapturaYPresidenteAprueba() {
        TemporadaPrueba temporada = temporada(EstadoTemporada.CONFIGURACION);
        iniciarSesionComoAdministrador();
        ClubDto primero = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));
        ClubDto segundo = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));

        UUID secretario = perfilConRol("SECRETARIO");
        iniciarSesion(secretario);
        AfiliacionDto afiliacionA = afiliaciones.crear(new CrearAfiliacionRequest(primero.id(),
                temporada.id(), " " + COLORES + " ", List.of(temporada.categoriaId())));
        AfiliacionDto afiliacionB = afiliaciones.crear(new CrearAfiliacionRequest(segundo.id(),
                temporada.id(), COLORES, List.of(temporada.categoriaId())));

        String prefijo = "AF-" + temporada.fechaInicio().getYear() + "-";
        assertThat(afiliacionA.estado()).isEqualTo(EstadoAfiliacion.SOLICITADA);
        assertThat(afiliacionA.folio()).isEqualTo(prefijo + "0001");
        assertThat(afiliacionB.folio()).isEqualTo(prefijo + "0002");
        assertThat(afiliacionA.coloresUniformes()).isEqualTo(COLORES);
        assertThat(afiliacionA.categorias()).extracting(AfiliacionDto.ReferenciaDto::id)
                .containsExactly(temporada.categoriaId());
        assertThat(afiliacionA.capturadaPor().id()).isEqualTo(secretario);
        assertThat(eventos(afiliacionA.id())).extracting(Evento::accion)
                .containsExactly("REGISTRAR_AFILIACION");
        assertThat(eventos(afiliacionA.id()).getFirst().perfilId()).isEqualTo(secretario);

        assertThatThrownBy(() -> afiliaciones.crear(new CrearAfiliacionRequest(primero.id(),
                temporada.id(), COLORES, List.of(temporada.categoriaId()))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "DUPLICADO").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains(afiliacionA.folio());
                });

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);
        AfiliacionDto aprobada = afiliaciones.resolver(afiliacionA.id(),
                new ResolverAfiliacionRequest(DecisionAfiliacion.APROBADA, "Revisado en sesión"));

        assertThat(aprobada.estado()).isEqualTo(EstadoAfiliacion.APROBADA);
        assertThat(aprobada.resueltaPor().id()).isEqualTo(presidente);
        assertThat(aprobada.resueltaEn()).isNotNull();
        assertThat(aprobada.observaciones()).isEqualTo("Revisado en sesión");
        assertThat(eventos(afiliacionA.id())).extracting(Evento::accion)
                .containsExactly("REGISTRAR_AFILIACION", "APROBAR_AFILIACION");
        assertThat(eventos(afiliacionA.id()).getLast().perfilId()).isEqualTo(presidente);

        assertThatThrownBy(() -> afiliaciones.resolver(afiliacionA.id(),
                new ResolverAfiliacionRequest(DecisionAfiliacion.RECHAZADA, OBSERVACIONES)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-101"));
        assertThatThrownBy(() -> afiliaciones.actualizar(afiliacionA.id(),
                new ActualizarAfiliacionRequest("Rojo", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-101"));

        assertThat(afiliaciones.listar(new FiltroAfiliaciones(temporada.id(), null, 0, 20)).totalElementos())
                .isEqualTo(2);
        assertThat(afiliaciones.listar(new FiltroAfiliaciones(temporada.id(), EstadoAfiliacion.APROBADA, 0, 20))
                .contenido()).extracting(AfiliacionDto::id).containsExactly(afiliacionA.id());
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.afiliaciones WHERE temporada_id = :id")
                .param("id", temporada.id()).query(Long.class).single()).isEqualTo(2);
    }

    @Test
    @DisplayName("H-08 criterio 2 · RF-101: el rechazo exige observaciones y las guarda con el motivo")
    void presidenteRechazaConObservaciones() {
        TemporadaPrueba temporada = temporada(EstadoTemporada.CONFIGURACION);
        iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));
        iniciarSesion(perfilConRol("SECRETARIO"));
        AfiliacionDto afiliacion = afiliaciones.crear(new CrearAfiliacionRequest(club.id(), temporada.id(),
                COLORES, List.of(temporada.categoriaId())));

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);
        assertThatThrownBy(() -> afiliaciones.resolver(afiliacion.id(),
                new ResolverAfiliacionRequest(DecisionAfiliacion.RECHAZADA, "corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RF-101"));
        assertThat(afiliaciones.consultar(afiliacion.id()).estado()).isEqualTo(EstadoAfiliacion.SOLICITADA);

        AfiliacionDto rechazada = afiliaciones.resolver(afiliacion.id(),
                new ResolverAfiliacionRequest(DecisionAfiliacion.RECHAZADA, " " + OBSERVACIONES + " "));

        assertThat(rechazada.estado()).isEqualTo(EstadoAfiliacion.RECHAZADA);
        assertThat(rechazada.observaciones()).isEqualTo(OBSERVACIONES);
        assertThat(rechazada.resueltaPor().id()).isEqualTo(presidente);
        assertThat(rechazada.resueltaEn()).isNotNull();
        List<Evento> eventos = eventos(afiliacion.id());
        assertThat(eventos).extracting(Evento::accion)
                .containsExactly("REGISTRAR_AFILIACION", "RECHAZAR_AFILIACION");
        assertThat(eventos.getLast().motivo()).isEqualTo(OBSERVACIONES);
        assertThat(eventos.getLast().perfilId()).isEqualTo(presidente);
    }

    // ── Criterio 3: ámbito por club ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("H-08 criterio 3 · RF-101, VAL-024: la Directiva solo ve y opera el tarjetón de su club")
    void directivaSoloVeSuClub() {
        TemporadaPrueba temporada = temporada(EstadoTemporada.CONFIGURACION);
        iniciarSesionComoAdministrador();
        ClubDto propio = clubes.crear(new CrearClubRequest("Propio " + UUID.randomUUID()));
        ClubDto ajeno = clubes.crear(new CrearClubRequest("Ajeno " + UUID.randomUUID()));
        UUID directiva = perfilDeDirectiva(propio.id());

        iniciarSesion(perfilConRol("SECRETARIO"));
        AfiliacionDto dePropio = afiliaciones.crear(new CrearAfiliacionRequest(propio.id(), temporada.id(),
                COLORES, List.of(temporada.categoriaId())));
        AfiliacionDto delAjeno = afiliaciones.crear(new CrearAfiliacionRequest(ajeno.id(), temporada.id(),
                COLORES, List.of(temporada.categoriaId())));

        iniciarSesion(directiva);
        assertThat(afiliaciones.listar(new FiltroAfiliaciones(temporada.id(), null, 0, 100)).contenido())
                .extracting(AfiliacionDto::id).containsExactly(dePropio.id());
        assertThat(afiliaciones.listar(new FiltroAfiliaciones(temporada.id(), null, 0, 100)).totalElementos())
                .isEqualTo(1);
        assertThat(afiliaciones.consultar(dePropio.id()).club().id()).isEqualTo(propio.id());
        assertThatThrownBy(() -> afiliaciones.consultar(delAjeno.id()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> afiliaciones.crear(new CrearAfiliacionRequest(ajeno.id(), temporada.id(),
                COLORES, List.of(temporada.categoriaId()))))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> afiliaciones.resolver(dePropio.id(),
                new ResolverAfiliacionRequest(DecisionAfiliacion.APROBADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);
        assertThat(afiliaciones.listar(new FiltroAfiliaciones(temporada.id(), null, 0, 100)).contenido())
                .extracting(AfiliacionDto::id).containsExactlyInAnyOrder(dePropio.id(), delAjeno.id());
        assertThat(afiliaciones.resolver(delAjeno.id(),
                new ResolverAfiliacionRequest(DecisionAfiliacion.APROBADA, null)).estado())
                .isEqualTo(EstadoAfiliacion.APROBADA);

        iniciarSesion(perfilConRol("SECRETARIO"));
        assertThat(afiliaciones.listar(new FiltroAfiliaciones(temporada.id(), null, 0, 100)).totalElementos())
                .isEqualTo(2);
    }

    // ── Referencias y temporada cerrada ─────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-101: una categoría de otra temporada responde 422 con categoriasInvalidas")
    void categoriaDeOtraTemporada() {
        TemporadaPrueba temporada = temporada(EstadoTemporada.CONFIGURACION);
        TemporadaPrueba otra = temporada(EstadoTemporada.CONFIGURACION);
        iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));
        iniciarSesion(perfilConRol("SECRETARIO"));

        assertThatThrownBy(() -> afiliaciones.crear(new CrearAfiliacionRequest(club.id(), temporada.id(),
                COLORES, List.of(otra.categoriaId()))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA").accept(excepcion);
                    assertThat(excepcion.propiedades()).containsEntry("categoriasInvalidas",
                            List.of(otra.categoriaId().toString()));
                });
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.afiliaciones WHERE temporada_id = :id")
                .param("id", temporada.id()).query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("RF-003: una temporada cerrada no admite tarjetones nuevos (409)")
    void temporadaCerrada() {
        TemporadaPrueba temporada = temporada(EstadoTemporada.CERRADA);
        iniciarSesionComoAdministrador();
        ClubDto club = clubes.crear(new CrearClubRequest("Club " + UUID.randomUUID()));
        iniciarSesion(perfilConRol("SECRETARIO"));

        assertThatThrownBy(() -> afiliaciones.crear(new CrearAfiliacionRequest(club.id(), temporada.id(),
                COLORES, List.of(temporada.categoriaId()))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        assertThat(jdbc.sql("SELECT count(*) FROM siglim.afiliaciones WHERE temporada_id = :id")
                .param("id", temporada.id()).query(Long.class).single()).isZero();
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    /** Temporada con una categoría asignada, en el estado pedido (sin pasar por activación). */
    private TemporadaPrueba temporada(EstadoTemporada estado) {
        LocalDate inicio = LocalDate.of(2033, 8, 1);
        String nombre = "Temporada " + UUID.randomUUID();
        UUID id = jdbc.sql("""
                        INSERT INTO siglim.temporadas (liga_id, nombre, fecha_inicio, estado)
                        VALUES (:liga, :nombre, :inicio, CAST(:estado AS siglim.estado_temporada))
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", nombre)
                .param("inicio", inicio)
                .param("estado", estado.name())
                .query(UUID.class)
                .single();
        UUID categoria = jdbc.sql("""
                        INSERT INTO siglim.categorias (liga_id, nombre, tipo, orden)
                        SELECT :liga, :nombre, 'LIBRE', coalesce(max(orden), 0) + 1 FROM siglim.categorias
                        RETURNING id
                        """)
                .param("liga", LIGA)
                .param("nombre", "Categoría " + UUID.randomUUID())
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        INSERT INTO siglim.categorias_temporada (temporada_id, categoria_id)
                        VALUES (:temporada, :categoria)
                        """)
                .param("temporada", id)
                .param("categoria", categoria)
                .update();
        return new TemporadaPrueba(id, categoria, nombre, inicio);
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class)
                .single();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado. */
    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil)
                .param("rol", rol)
                .update();
        return perfil;
    }

    /** Perfil con el rol DIRECTIVA_CLUB de la matriz, en ámbito CLUB de ese club. */
    private UUID perfilDeDirectiva(UUID club) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        SELECT :perfil, id, liga_id, 'CLUB', :club FROM siglim.roles WHERE codigo = 'DIRECTIVA_CLUB'
                        """)
                .param("perfil", perfil)
                .param("club", club)
                .update();
        return perfil;
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }

    private List<Evento> eventos(UUID afiliacion) {
        return jdbc.sql("""
                        SELECT accion, perfil_id, motivo
                          FROM siglim.auditoria
                         WHERE modulo = 'M-03' AND entidad = 'afiliaciones' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", afiliacion.toString())
                .query((fila, numero) -> new Evento(fila.getString("accion"),
                        fila.getObject("perfil_id", UUID.class), fila.getString("motivo")))
                .list();
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private record TemporadaPrueba(UUID id, UUID categoriaId, String nombre, LocalDate fechaInicio) {
    }

    private record Evento(String accion, UUID perfilId, String motivo) {
    }
}
