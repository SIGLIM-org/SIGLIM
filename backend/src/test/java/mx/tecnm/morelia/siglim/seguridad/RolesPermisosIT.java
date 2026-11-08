package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * H-20.1 contra PostgreSQL real, con {@link RolService} completo: la edición de la matriz que
 * responde {@code 200}, el par incompatible que deja la solicitud en {@code 202}, el dictamen del
 * Presidente, la denegación VAL-022 del rol propio, la separación de funciones de DEC-04 y la
 * auditoría con su motivo.
 *
 * <p>Cada prueba crea su propio rol de liga para no alterar la matriz sembrada ni depender de las
 * demás. Los permisos se identifican por su código, como en la API.
 */
class RolesPermisosIT extends BaseDatosPrueba {

    private static final String MOTIVO = "Ajuste de la matriz aprobado por la Liga";
    private static final String MOTIVO_DICTAMEN = "El control dual revisó el cambio solicitado";
    private static final String EDITAR_JORNADA = "programacion.jornada:EDITAR";
    private static final String CONSULTAR_JORNADA = "programacion.jornada:CONSULTAR";
    private static final String APROBAR_JORNADA = "programacion.jornada:APROBAR";
    private static final String CONSULTAR_CANCHA = "canchas.cancha:CONSULTAR";
    private static final String CAPTURAR_CEDULA = "partidos.cedula:CAPTURAR";
    private static final String APROBAR_CEDULA = "partidos.cedula:APROBAR";
    private static final String REDACTAR_COMUNICADO = "publicacion.comunicado:REDACTAR";
    private static final String APROBAR_COMUNICADO = "publicacion.comunicado:APROBAR";

    @Autowired
    private RolService servicio;

    // ── Edición de la matriz (RF-095) ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("H-20 criterio 1: un cambio sin pares incompatibles responde 200, se aplica y se audita")
    void aplicaCambioSinParesIncompatibles() {
        UUID rol = crearRol();
        asignarPermiso(rol, EDITAR_JORNADA);
        asignarPermiso(rol, CONSULTAR_JORNADA);
        UUID administrador = iniciarSesionComoAdministrador();

        ResultadoCambioPermisos resultado = servicio.actualizarPermisos(rol,
                new ActualizarPermisosRolRequest(List.of(EDITAR_JORNADA, CONSULTAR_JORNADA,
                        CONSULTAR_CANCHA), MOTIVO));

        assertThat(resultado.pendiente()).isFalse();
        assertThat(permisosDeRol(rol))
                .containsExactlyInAnyOrder(EDITAR_JORNADA, CONSULTAR_JORNADA, CONSULTAR_CANCHA);
        assertThat(solicitudesDeRol(rol)).as("no hay trámite pendiente").isEmpty();

        Evento evento = unicoEvento("roles", rol.toString());
        assertThat(evento.accion()).isEqualTo("CAMBIAR_PERMISOS_ROL");
        assertThat(evento.perfilId()).isEqualTo(administrador);
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
        assertThat(evento.detalle()).contains(CONSULTAR_CANCHA);
    }

    @Test
    @DisplayName("H-20 criterio 2: un par incompatible queda SOLICITADO y no toca la matriz")
    void parIncompatibleQuedaPendiente() {
        UUID rol = crearRol();
        asignarPermiso(rol, EDITAR_JORNADA);
        UUID administrador = iniciarSesionComoAdministrador();

        ResultadoCambioPermisos resultado = servicio.actualizarPermisos(rol,
                new ActualizarPermisosRolRequest(List.of(EDITAR_JORNADA, APROBAR_JORNADA), MOTIVO));

        assertThat(resultado.pendiente()).isTrue();
        assertThat(resultado.solicitud().estado()).isEqualTo(EstadoSolicitudPermisos.SOLICITADO);
        assertThat(resultado.solicitud().solicitadoPor().id()).isEqualTo(administrador);
        assertThat(resultado.solicitud().permisosAgregados()).containsExactly(APROBAR_JORNADA);
        assertThat(permisosDeRol(rol)).as("el rol sigue con lo que tenía").containsExactly(EDITAR_JORNADA);

        FilaSolicitud solicitud = solicitud(resultado.solicitud().id());
        assertThat(solicitud.estado()).isEqualTo("SOLICITADO");
        assertThat(solicitud.resueltoPor()).isNull();
        assertThat(solicitud.permisosAgregadosIds()).containsExactly(permisoId(APROBAR_JORNADA));

        Evento evento = unicoEvento("solicitudes_cambio_permisos",
                resultado.solicitud().id().toString());
        assertThat(evento.accion()).isEqualTo("SOLICITAR_CAMBIO_PERMISOS");
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
        assertThat(evento.detalle()).contains(APROBAR_JORNADA);
    }

    // ── Dictamen del Presidente (RF-096) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("H-20 criterio 3: el Presidente aprueba y el cambio pendiente se aplica")
    void presidenteAprueba() {
        UUID rol = crearRol();
        asignarPermiso(rol, EDITAR_JORNADA);
        iniciarSesionComoAdministrador();
        UUID solicitudId = servicio.actualizarPermisos(rol,
                        new ActualizarPermisosRolRequest(List.of(EDITAR_JORNADA, APROBAR_JORNADA), MOTIVO))
                .solicitud().id();

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);
        SolicitudCambioPermisosDto resuelta = servicio.resolverSolicitud(solicitudId,
                new ResolverSolicitudCambioPermisosRequest(DecisionSolicitudPermisos.APROBADO,
                        MOTIVO_DICTAMEN));

        assertThat(resuelta.estado()).isEqualTo(EstadoSolicitudPermisos.APROBADO);
        assertThat(resuelta.resueltoPor().id()).isEqualTo(presidente);
        assertThat(resuelta.observaciones()).isEqualTo(MOTIVO_DICTAMEN);
        assertThat(permisosDeRol(rol)).containsExactlyInAnyOrder(EDITAR_JORNADA, APROBAR_JORNADA);

        Evento evento = unicoEvento("solicitudes_cambio_permisos", solicitudId.toString(),
                "APROBAR_CAMBIO_PERMISOS");
        assertThat(evento.perfilId()).isEqualTo(presidente);
        assertThat(evento.motivo()).isEqualTo(MOTIVO_DICTAMEN);
        assertThat(evento.detalle()).contains(APROBAR_JORNADA);
    }

    @Test
    @DisplayName("H-20 criterio 3: el Presidente rechaza y el cambio se descarta")
    void presidenteRechaza() {
        UUID rol = crearRol();
        asignarPermiso(rol, REDACTAR_COMUNICADO);
        iniciarSesionComoAdministrador();
        UUID solicitudId = servicio.actualizarPermisos(rol,
                        new ActualizarPermisosRolRequest(List.of(REDACTAR_COMUNICADO, APROBAR_COMUNICADO),
                                MOTIVO))
                .solicitud().id();

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);
        SolicitudCambioPermisosDto resuelta = servicio.resolverSolicitud(solicitudId,
                new ResolverSolicitudCambioPermisosRequest(DecisionSolicitudPermisos.RECHAZADO,
                        MOTIVO_DICTAMEN));

        assertThat(resuelta.estado()).isEqualTo(EstadoSolicitudPermisos.RECHAZADO);
        assertThat(permisosDeRol(rol)).as("el rechazo no aplica nada").containsExactly(REDACTAR_COMUNICADO);
        assertThat(solicitud(solicitudId).estado()).isEqualTo("RECHAZADO");
        assertThat(unicoEvento("solicitudes_cambio_permisos", solicitudId.toString(),
                "RECHAZAR_CAMBIO_PERMISOS").motivo()).isEqualTo(MOTIVO_DICTAMEN);
    }

    // ── Separación de funciones y permisos (VAL-022, DEC-04) ───────────────────────────────────

    @Test
    @DisplayName("H-20 criterio 4: el Administrador no edita su propio rol (VAL-022) y no se audita")
    void rolPropioDenegado() {
        UUID administrador = iniciarSesionComoAdministrador();
        UUID rolAdministrador = jdbc.sql("SELECT id FROM siglim.roles WHERE codigo = 'ADMINISTRADOR_GENERAL'")
                .query(UUID.class).single();
        List<String> antes = permisosDeRol(rolAdministrador);
        long eventosAntes = contarEventos("roles", rolAdministrador.toString(), "CAMBIAR_PERMISOS_ROL");

        assertThatThrownBy(() -> servicio.actualizarPermisos(rolAdministrador,
                new ActualizarPermisosRolRequest(antes, MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-022"));

        assertThat(permisosDeRol(rolAdministrador)).isEqualTo(antes);
        assertThat(contarEventos("roles", rolAdministrador.toString(), "CAMBIAR_PERMISOS_ROL"))
                .isEqualTo(eventosAntes);
        assertThat(administrador).isNotNull();
    }

    @Test
    @DisplayName("DEC-04: quien solicita el cambio no puede aprobarlo, aunque tenga los dos permisos")
    void quienSolicitaNoAprueba() {
        UUID rol = crearRol();
        asignarPermiso(rol, CAPTURAR_CEDULA);
        UUID conDosCargos = perfilConRoles("ADMINISTRADOR_GENERAL", "PRESIDENTE");
        iniciarSesion(conDosCargos);

        UUID solicitudId = servicio.actualizarPermisos(rol,
                        new ActualizarPermisosRolRequest(List.of(CAPTURAR_CEDULA, APROBAR_CEDULA), MOTIVO))
                .solicitud().id();

        assertThatThrownBy(() -> servicio.resolverSolicitud(solicitudId,
                new ResolverSolicitudCambioPermisosRequest(DecisionSolicitudPermisos.APROBADO,
                        MOTIVO_DICTAMEN)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "RF-096"));

        assertThat(solicitud(solicitudId).estado()).as("sigue pendiente").isEqualTo("SOLICITADO");
        assertThat(permisosDeRol(rol)).containsExactly(CAPTURAR_CEDULA);
        assertThat(contarEventos("solicitudes_cambio_permisos", solicitudId.toString(),
                "APROBAR_CAMBIO_PERMISOS")).as("no se auditó un dictamen inexistente").isZero();
    }

    @Test
    @DisplayName("VAL-015: el Presidente no edita la matriz y el Administrador no aprueba pares incompatibles")
    void separacionDePermisos() {
        UUID rol = crearRol();
        asignarPermiso(rol, EDITAR_JORNADA);

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);
        assertThatThrownBy(() -> servicio.actualizarPermisos(rol,
                new ActualizarPermisosRolRequest(List.of(EDITAR_JORNADA, APROBAR_JORNADA), MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));

        iniciarSesionComoAdministrador();
        UUID solicitudId = servicio.actualizarPermisos(rol,
                        new ActualizarPermisosRolRequest(List.of(EDITAR_JORNADA, APROBAR_JORNADA), MOTIVO))
                .solicitud().id();
        assertThatThrownBy(() -> servicio.resolverSolicitud(solicitudId,
                new ResolverSolicitudCambioPermisosRequest(DecisionSolicitudPermisos.APROBADO,
                        MOTIVO_DICTAMEN)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));

        assertThat(permisosDeRol(rol)).as("ninguna de las dos denegaciones escribió").containsExactly(EDITAR_JORNADA);
        assertThat(solicitud(solicitudId).estado()).isEqualTo("SOLICITADO");
    }

    // ── Listado de solicitudes (RF-096) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-096: el Presidente lista las solicitudes por estado y rol, paginadas")
    void listadoConFiltros() {
        UUID rol = crearRol();
        asignarPermiso(rol, EDITAR_JORNADA);
        iniciarSesionComoAdministrador();
        UUID pendiente = servicio.actualizarPermisos(rol,
                        new ActualizarPermisosRolRequest(List.of(EDITAR_JORNADA, APROBAR_JORNADA), MOTIVO))
                .solicitud().id();

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);

        var solicitudes = servicio.listarSolicitudes(EstadoSolicitudPermisos.SOLICITADO, rol, 0, 10);

        assertThat(solicitudes.totalElementos()).isEqualTo(1);
        assertThat(solicitudes.contenido()).singleElement().satisfies(solicitud -> {
            assertThat(solicitud.id()).isEqualTo(pendiente);
            assertThat(solicitud.rol().id()).isEqualTo(rol);
            assertThat(solicitud.estado()).isEqualTo(EstadoSolicitudPermisos.SOLICITADO);
        });
        assertThat(servicio.listarSolicitudes(EstadoSolicitudPermisos.APROBADO, rol, 0, 10).contenido())
                .isEmpty();
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    /** Crea un rol de liga con código único para que cada prueba tenga su propia matriz. */
    private UUID crearRol() {
        String codigo = "PRUEBA_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return jdbc.sql("""
                        INSERT INTO siglim.roles (liga_id, codigo, nombre, descripcion,
                                                  ambito_predeterminado, asignable)
                        SELECT id, :codigo, :nombre, 'Rol de prueba de H-20.1', 'LIGA', true
                          FROM siglim.ligas LIMIT 1
                        RETURNING id
                        """)
                .param("codigo", codigo)
                .param("nombre", "Rol " + codigo)
                .query(UUID.class)
                .single();
    }

    private void asignarPermiso(UUID rol, String codigo) {
        jdbc.sql("""
                        INSERT INTO siglim.rol_permisos (rol_id, permiso_id)
                        SELECT :rol, id FROM siglim.permisos WHERE codigo = :codigo
                        """)
                .param("rol", rol).param("codigo", codigo).update();
    }

    private List<String> permisosDeRol(UUID rol) {
        return jdbc.sql("""
                        SELECT p.codigo
                          FROM siglim.rol_permisos rp
                          JOIN siglim.permisos p ON p.id = rp.permiso_id
                         WHERE rp.rol_id = :rol
                         ORDER BY p.codigo
                        """)
                .param("rol", rol).query(String.class).list();
    }

    private long permisoId(String codigo) {
        return jdbc.sql("SELECT id FROM siglim.permisos WHERE codigo = :codigo")
                .param("codigo", codigo).query(Long.class).single();
    }

    private List<UUID> solicitudesDeRol(UUID rol) {
        return jdbc.sql("SELECT id FROM siglim.solicitudes_cambio_permisos WHERE rol_id = :rol")
                .param("rol", rol).query(UUID.class).list();
    }

    private FilaSolicitud solicitud(UUID id) {
        return jdbc.sql("""
                        SELECT estado, resuelto_por, permisos_agregados
                          FROM siglim.solicitudes_cambio_permisos
                         WHERE id = :id
                        """)
                .param("id", id)
                .query((fila, numero) -> new FilaSolicitud(
                        fila.getString("estado"),
                        fila.getObject("resuelto_por", UUID.class),
                        leerIds(fila.getArray("permisos_agregados"))))
                .single();
    }

    private static List<Long> leerIds(java.sql.Array arreglo) {
        if (arreglo == null) {
            return List.of();
        }
        try {
            Object[] valores = (Object[]) arreglo.getArray();
            return java.util.Arrays.stream(valores).map(valor -> ((Number) valor).longValue()).toList();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("No se pudo leer el arreglo de permisos", e);
        }
    }

    private long contarEventos(String entidad, String entidadId, String accion) {
        return jdbc.sql("""
                        SELECT count(*)
                          FROM siglim.auditoria
                         WHERE entidad = :entidad AND entidad_id = :entidadId AND accion = :accion
                        """)
                .param("entidad", entidad).param("entidadId", entidadId).param("accion", accion)
                .query(Long.class).single();
    }

    private Evento unicoEvento(String entidad, String entidadId) {
        return eventos(entidad, entidadId).getFirst();
    }

    private Evento unicoEvento(String entidad, String entidadId, String accion) {
        return eventos(entidad, entidadId).stream()
                .filter(evento -> evento.accion().equals(accion))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró el evento " + accion));
    }

    private List<Evento> eventos(String entidad, String entidadId) {
        return jdbc.sql("""
                        SELECT accion, perfil_id, motivo, detalle
                          FROM siglim.auditoria
                         WHERE entidad = :entidad AND entidad_id = :entidadId
                         ORDER BY id
                        """)
                .param("entidad", entidad).param("entidadId", entidadId)
                .query((fila, numero) -> new Evento(fila.getString("accion"),
                        fila.getObject("perfil_id", UUID.class), fila.getString("motivo"),
                        fila.getString("detalle")))
                .list();
    }

    private UUID perfilConRol(String rol) {
        return perfilConRoles(rol);
    }

    private UUID perfilConRoles(String... roles) {
        UUID perfil = jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado)"
                + " VALUES ('Usuario de prueba') RETURNING id")
                .query(UUID.class).single();
        for (String rol : roles) {
            jdbc.sql("""
                            INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                            SELECT :perfil, id, liga_id, ambito_predeterminado
                              FROM siglim.roles WHERE codigo = :rol
                            """)
                    .param("perfil", perfil).param("rol", rol).update();
        }
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

    private record FilaSolicitud(String estado, UUID resueltoPor, List<Long> permisosAgregadosIds) {
    }

    private record Evento(String accion, UUID perfilId, String motivo, String detalle) {
    }
}
