package mx.tecnm.morelia.siglim.canchas;

import static mx.tecnm.morelia.siglim.canchas.DatosSolicitudPrueba.ofertaExterna;
import static mx.tecnm.morelia.siglim.canchas.DatosSolicitudPrueba.prestamoDe;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * B-14 contra PostgreSQL real, con {@link SolicitudCanchaService} completo: matriz de permisos de la
 * migración, ámbito de club, número de oficio, restricciones de la tabla y auditoría.
 */
class SolicitudesCanchaIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final FiltroSolicitudesCancha TODAS = new FiltroSolicitudesCancha(List.of(), null, 0, 100);

    @Autowired
    private SolicitudCanchaService servicio;

    @Test
    @DisplayName("RF-117, RF-118: la Directiva captura un préstamo con oficio y el Presidente lo aprueba una sola vez")
    void prestamoAprobado() {
        UUID club = crearClub();
        iniciarSesion(perfilDeDirectiva(club));
        SolicitudCanchaDto creada = servicio.crear(prestamoDe(club));

        assertThat(creada.estado()).isEqualTo(EstadoSolicitudCancha.PENDIENTE);
        assertThat(creada.numeroOficio()).matches("OF-" + anioActual() + "-\\d{4}");
        assertThat(creada.prestamo()).isEqualTo(DatosSolicitudPrueba.prestamo());
        assertThat(creada.club().id()).isEqualTo(club);

        iniciarSesion(perfilConRol("PRESIDENTE"));
        assertThat(servicio.listar(TODAS).contenido()).extracting(SolicitudCanchaDto::id).contains(creada.id());
        SolicitudCanchaDto aprobada = servicio.resolver(creada.id(),
                new ResolverSolicitudCanchaRequest(DecisionSolicitudCancha.APROBADA, "Usar la cancha con llave"));

        assertThat(aprobada.estado()).isEqualTo(EstadoSolicitudCancha.APROBADA);
        assertThat(aprobada.resueltaEn()).isNotNull();
        assertThat(aprobada.observaciones()).isEqualTo("Usar la cancha con llave");
        assertThatThrownBy(() -> servicio.resolver(creada.id(),
                new ResolverSolicitudCanchaRequest(DecisionSolicitudCancha.NO_APROBADA, "Cambio de opinión")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-117"));
        assertThat(acciones(creada.id())).containsExactly("REGISTRAR_SOLICITUD_CANCHA", "RESOLVER_SOLICITUD_CANCHA");
        assertThat(detallesAuditoria(creada.id())).allSatisfy(detalle ->
                assertThat(detalle).doesNotContain(DatosSolicitudPrueba.TELEFONO, "Laura"));
    }

    @Test
    @DisplayName("RF-116: el Secretario captura la oferta de un externo y el Presidente la rechaza con motivo")
    void ofertaExternaRechazada() {
        iniciarSesion(perfilConRol("SECRETARIO"));
        SolicitudCanchaDto oferta = servicio.crear(ofertaExterna("Rosa Gómez"));

        assertThat(oferta.estado()).isEqualTo(EstadoSolicitudCancha.PENDIENTE_NEGOCIACION);
        assertThat(oferta.solicitanteExterno()).isEqualTo("Rosa Gómez");
        assertThat(oferta.club()).isNull();

        iniciarSesion(perfilConRol("PRESIDENTE"));
        assertThatThrownBy(() -> servicio.resolver(oferta.id(),
                new ResolverSolicitudCanchaRequest(DecisionSolicitudCancha.RECHAZADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        String motivo = "El costo excede las tarifas de la liga";
        SolicitudCanchaDto rechazada = servicio.resolver(oferta.id(),
                new ResolverSolicitudCanchaRequest(DecisionSolicitudCancha.RECHAZADA, motivo));

        assertThat(rechazada.estado()).isEqualTo(EstadoSolicitudCancha.RECHAZADA);
        assertThat(jdbc.sql("""
                        SELECT motivo FROM siglim.auditoria
                         WHERE entidad = 'solicitudes_cancha' AND entidad_id = :id AND accion = 'RESOLVER_SOLICITUD_CANCHA'
                        """).param("id", oferta.id().toString()).query(String.class).single())
                .isEqualTo(motivo);
    }

    @Test
    @DisplayName("VAL-024: la Directiva solo captura y ve los trámites de su club; las ofertas de externos no")
    void ambitoDeClub() {
        UUID propio = crearClub();
        UUID ajeno = crearClub();
        iniciarSesion(perfilConRol("SECRETARIO"));
        SolicitudCanchaDto externa = servicio.crear(ofertaExterna("Oferente " + UUID.randomUUID()));
        SolicitudCanchaDto deAjeno = servicio.crear(prestamoDe(ajeno));

        iniciarSesion(perfilDeDirectiva(propio));
        SolicitudCanchaDto mia = servicio.crear(prestamoDe(propio));

        assertThat(servicio.listar(TODAS).contenido()).extracting(SolicitudCanchaDto::id)
                .contains(mia.id()).doesNotContain(externa.id(), deAjeno.id());
        assertThatThrownBy(() -> servicio.crear(prestamoDe(ajeno)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.crear(ofertaExterna("Otro externo")))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.resolver(mia.id(),
                new ResolverSolicitudCanchaRequest(DecisionSolicitudCancha.APROBADA, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        ContextoSolicitudesCanchaDto contexto = servicio.contexto();
        assertThat(contexto.clubes()).extracting(SolicitudCanchaDto.ReferenciaDto::id).containsExactly(propio);
        assertThat(contexto.capturaExternos()).isFalse();
    }

    @Test
    @DisplayName("RF-118: los oficios son consecutivos y la carta la genera quien tiene canchas.oficio:GENERAR")
    void cartaConOficio() {
        UUID club = crearClub();
        iniciarSesion(perfilConRol("SECRETARIO"));
        SolicitudCanchaDto primera = servicio.crear(prestamoDe(club));
        SolicitudCanchaDto segunda = servicio.crear(ofertaExterna("Rosa Gómez"));

        assertThat(consecutivo(segunda.numeroOficio())).isEqualTo(consecutivo(primera.numeroOficio()) + 1);
        CartaOficioDto carta = servicio.oficio(segunda.id());
        assertThat(carta.numeroOficio()).isEqualTo(segunda.numeroOficio());
        assertThat(carta.formato()).isEqualTo(2);
        assertThat(carta.asunto()).isEqualTo("Oferta de disponibilidad de cancha deportiva.");
        assertThat(carta.liga()).isNotBlank();
        assertThat(carta.oferta()).isEqualTo(DatosSolicitudPrueba.oferta());

        iniciarSesion(perfilDeDirectiva(club));
        assertThatThrownBy(() -> servicio.oficio(primera.id()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
    }

    @Test
    @DisplayName("DEC-35: la tabla rechaza estados de otro formato y un trámite sin solicitante (última garantía)")
    void restricciones() {
        iniciarSesion(perfilConRol("SECRETARIO"));
        SolicitudCanchaDto oferta = servicio.crear(ofertaExterna("Rosa Gómez"));

        assertThatThrownBy(() -> jdbc.sql("UPDATE siglim.solicitudes_cancha SET estado = 'APROBADA' WHERE id = :id")
                .param("id", oferta.id()).update())
                .hasMessageContaining("ck_solicitudes_cancha_estado");
        assertThatThrownBy(() -> jdbc.sql(
                        "UPDATE siglim.solicitudes_cancha SET solicitante_externo = NULL WHERE id = :id")
                .param("id", oferta.id()).update())
                .hasMessageContaining("ck_solicitudes_cancha_solicitante");
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private static int anioActual() {
        return Year.now(ZoneId.of("America/Mexico_City")).getValue();
    }

    private static int consecutivo(String numeroOficio) {
        return Integer.parseInt(numeroOficio.substring(numeroOficio.lastIndexOf('-') + 1));
    }

    private UUID crearClub() {
        return jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID())
                .query(UUID.class).single();
    }

    private UUID crearPerfil() {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class).single();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado (LIGA). */
    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil();
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
        return perfil;
    }

    /** Perfil con el rol DIRECTIVA_CLUB de la matriz, en ámbito CLUB de ese club. */
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

    private List<String> acciones(UUID solicitud) {
        return jdbc.sql("""
                        SELECT accion FROM siglim.auditoria
                         WHERE modulo = 'M-06' AND entidad = 'solicitudes_cancha' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", solicitud.toString()).query(String.class).list();
    }

    private List<String> detallesAuditoria(UUID solicitud) {
        return jdbc.sql("""
                        SELECT CAST(detalle AS text) FROM siglim.auditoria
                         WHERE entidad = 'solicitudes_cancha' AND entidad_id = :id
                        """)
                .param("id", solicitud.toString()).query(String.class).list();
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }
}
