package mx.tecnm.morelia.siglim.publicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
 * B-27.1 contra PostgreSQL real: el flujo editorial atraviesa autorización por permiso y ámbito,
 * RLS, los triggers de publicación e historial y la auditoría.
 *
 * <p>Cada prueba crea sus propios perfiles con los roles sembrados {@code SECRETARIO} y
 * {@code PRESIDENTE}, porque el Administrador general no tiene permisos de M-11. Los comunicados se
 * identifican por su UUID, así que no dependen de lo que hayan dejado otras pruebas.
 */
class ComunicadosIT extends BaseDatosPrueba {

    private static final String TITULO = "Ajuste extraordinario al calendario";
    private static final String CUERPO = "Se reprograman las semifinales de vuelta.";
    private static final String MOTIVO_DEVOLUCION = "Falta la fecha definitiva del calendario";

    @Autowired
    private ComunicadoService servicio;

    @Test
    @DisplayName("RF-090, CU-027: redactar, editar, enviar a revisión, aprobar y publicar con historial")
    void flujoCompletoHastaPublicar() {
        UUID secretario = perfilConRol("SECRETARIO");
        iniciarSesion(secretario);

        ComunicadoDto creado = servicio.crear(new CrearComunicadoRequest(TITULO, CUERPO));
        assertThat(creado.estadoPublicacion()).isEqualTo(EstadoPublicacion.BORRADOR);
        assertThat(creado.redactadoPor()).isEqualTo(secretario);
        assertThat(creado.publicadoEn()).isNull();

        servicio.actualizar(creado.id(), new ActualizarComunicadoRequest(TITULO + " (corregido)", null));
        assertThat(servicio.enviarARevision(creado.id()).estadoPublicacion())
                .isEqualTo(EstadoPublicacion.REVISION);

        Fila enRevision = fila(creado.id());
        assertThat(enRevision.estado()).isEqualTo("REVISION");
        assertThat(enRevision.aprobadoPor()).isNull();
        assertThat(enRevision.publicadoEn()).isNull();
        assertThat(historial(creado.id())).singleElement().satisfies(paso -> {
            assertThat(paso.estadoAnterior()).isEqualTo("BORRADOR");
            assertThat(paso.estadoNuevo()).isEqualTo("REVISION");
            assertThat(paso.cambiadoPor()).isEqualTo(secretario);
        });

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);

        // La Presidencia solo tiene APROBAR, no REDACTAR; la consulta acepta cualquiera de los dos.
        assertThat(servicio.consultar(creado.id()).titulo()).isEqualTo(TITULO + " (corregido)");

        ComunicadoDto publicado = servicio.resolver(creado.id(),
                new ResolverComunicadoRequest(DecisionComunicado.PUBLICADO, "Aprobado por unanimidad"));

        assertThat(publicado.estadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
        assertThat(publicado.aprobadoPor()).isEqualTo(presidente);
        assertThat(publicado.aprobadoEn()).isNotNull();
        assertThat(publicado.publicadoEn()).isNotNull();

        Fila fila = fila(creado.id());
        assertThat(fila.estado()).isEqualTo("PUBLICADO");
        assertThat(fila.aprobadoPor()).isEqualTo(presidente);
        assertThat(fila.publicadoEn()).isNotNull();
        assertThat(fila.observaciones()).isEqualTo("Aprobado por unanimidad");

        List<Historial> historial = historial(creado.id());
        assertThat(historial).hasSize(2);
        assertThat(historial.getLast().estadoAnterior()).isEqualTo("REVISION");
        assertThat(historial.getLast().estadoNuevo()).isEqualTo("PUBLICADO");
        assertThat(historial.getLast().cambiadoPor()).isEqualTo(presidente);

        assertThat(auditoria(creado.id())).extracting(Auditoria::accion)
                .containsExactly("REDACTAR_COMUNICADO", "EDITAR_COMUNICADO",
                        "ENVIAR_COMUNICADO_A_REVISION", "APROBAR_COMUNICADO");
        assertThat(auditoria(creado.id())).filteredOn(evento -> evento.accion().equals("APROBAR_COMUNICADO"))
                .extracting(Auditoria::perfilId).containsExactly(presidente);
    }

    @Test
    @DisplayName("RF-090: la devolución exige observaciones, vuelve a borrador y deja el motivo")
    void devolucionConObservaciones() {
        UUID secretario = perfilConRol("SECRETARIO");
        iniciarSesion(secretario);
        ComunicadoDto creado = servicio.crear(new CrearComunicadoRequest(TITULO, CUERPO));
        servicio.enviarARevision(creado.id());

        UUID presidente = perfilConRol("PRESIDENTE");
        iniciarSesion(presidente);

        assertThatThrownBy(() -> servicio.resolver(creado.id(),
                new ResolverComunicadoRequest(DecisionComunicado.BORRADOR, "Corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(excepcion.codigo()).isEqualTo("RF-090");
                });
        assertThat(fila(creado.id()).estado()).as("la devolución inválida no cambia el estado")
                .isEqualTo("REVISION");

        ComunicadoDto devuelto = servicio.resolver(creado.id(),
                new ResolverComunicadoRequest(DecisionComunicado.BORRADOR, MOTIVO_DEVOLUCION));

        assertThat(devuelto.estadoPublicacion()).isEqualTo(EstadoPublicacion.BORRADOR);
        assertThat(devuelto.observaciones()).isEqualTo(MOTIVO_DEVOLUCION);
        assertThat(fila(creado.id()).estado()).isEqualTo("BORRADOR");

        List<Historial> historial = historial(creado.id());
        assertThat(historial).hasSize(2);
        assertThat(historial.getLast().estadoAnterior()).isEqualTo("REVISION");
        assertThat(historial.getLast().estadoNuevo()).isEqualTo("BORRADOR");
        assertThat(historial.getLast().motivo()).isEqualTo(MOTIVO_DEVOLUCION);
        assertThat(historial.getLast().cambiadoPor()).isEqualTo(presidente);

        assertThat(auditoria(creado.id()))
                .filteredOn(evento -> evento.accion().equals("DEVOLVER_COMUNICADO"))
                .extracting(Auditoria::motivo).containsExactly(MOTIVO_DEVOLUCION);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    /** Crea una cuenta con el rol sembrado, liga y ámbito de liga. */
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
                        INSERT INTO perfiles (usuario_auth_id, nombre_mostrado, correo)
                        VALUES (:usuario, :nombre, :correo)
                        RETURNING id
                        """)
                .param("usuario", usuarioAuth)
                .param("nombre", codigoRol + " " + usuarioAuth)
                .param("correo", usuarioAuth + "@prueba.mx")
                .query(UUID.class)
                .single();

        jdbc.sql("""
                        INSERT INTO asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, r.id, r.liga_id, 'LIGA'
                          FROM roles r
                         WHERE r.codigo = :rol
                        """)
                .param("perfil", perfil)
                .param("rol", codigoRol)
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

    private Fila fila(UUID id) {
        return jdbc.sql("""
                        SELECT estado_publicacion, aprobado_por, publicado_en, observaciones
                          FROM comunicados
                         WHERE id = :id
                        """)
                .param("id", id)
                .query((resultado, numero) -> new Fila(
                        resultado.getString("estado_publicacion"),
                        resultado.getObject("aprobado_por", UUID.class),
                        resultado.getObject("publicado_en", OffsetDateTime.class),
                        resultado.getString("observaciones")))
                .single();
    }

    private List<Historial> historial(UUID id) {
        return jdbc.sql("""
                        SELECT estado_anterior, estado_nuevo, motivo, cambiado_por
                          FROM historial_publicacion
                         WHERE entidad = 'COMUNICADO' AND entidad_id = :id
                         ORDER BY cambiado_en, id
                        """)
                .param("id", id)
                .query((resultado, numero) -> new Historial(
                        resultado.getString("estado_anterior"),
                        resultado.getString("estado_nuevo"),
                        resultado.getString("motivo"),
                        resultado.getObject("cambiado_por", UUID.class)))
                .list();
    }

    private List<Auditoria> auditoria(UUID id) {
        return jdbc.sql("""
                        SELECT accion, perfil_id, motivo
                          FROM auditoria
                         WHERE entidad = 'comunicados' AND entidad_id = :id
                         ORDER BY id
                        """)
                .param("id", id.toString())
                .query((resultado, numero) -> new Auditoria(
                        resultado.getString("accion"),
                        resultado.getObject("perfil_id", UUID.class),
                        resultado.getString("motivo")))
                .list();
    }

    private record Fila(String estado, UUID aprobadoPor, OffsetDateTime publicadoEn, String observaciones) {
    }

    private record Historial(String estadoAnterior, String estadoNuevo, String motivo, UUID cambiadoPor) {
    }

    private record Auditoria(String accion, UUID perfilId, String motivo) {
    }
}
