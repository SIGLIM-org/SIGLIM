package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mx.tecnm.morelia.siglim.BaseDatosPrueba;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * B-01 contra PostgreSQL con RLS: el Administrador general registra una solicitud ARCO, la sigue
 * hasta cerrarla y cada paso queda en la auditoría sin la descripción ni la respuesta.
 */
class SolicitudesArcoIT extends BaseDatosPrueba {

    @Autowired
    private SolicitudArcoService servicio;

    @Test
    @DisplayName("RF-098: registrar, poner en atención y atender una solicitud, con su auditoría")
    void seguimientoCompleto() {
        UUID admin = iniciarSesionComoAdministrador();
        UUID persona = insertarPersona();

        SolicitudArcoDto registrada = servicio.registrar(new RegistrarSolicitudArcoRequest(persona, "CANCELACION",
                "Solicita cancelar su teléfono de contacto"));
        assertThat(registrada.estado()).isEqualTo(EstadoSolicitudArco.RECIBIDA);
        assertThat(registrada.personaNombre()).isEqualTo("Titular Arco Prueba");
        assertThat(registrada.recibidaEn()).isNotNull();

        SolicitudArcoDto enAtencion = servicio.actualizar(registrada.id(),
                new ActualizarSolicitudArcoRequest("EN_ATENCION", null));
        assertThat(enAtencion.estado()).isEqualTo(EstadoSolicitudArco.EN_ATENCION);
        assertThat(enAtencion.atendidaEn()).isNull();

        SolicitudArcoDto atendida = servicio.actualizar(registrada.id(),
                new ActualizarSolicitudArcoRequest("ATENDIDA", "Se bloqueó el teléfono (DEC-67)"));
        assertThat(atendida.estado()).isEqualTo(EstadoSolicitudArco.ATENDIDA);
        assertThat(atendida.respuesta()).isEqualTo("Se bloqueó el teléfono (DEC-67)");
        assertThat(atendida.atendidaPor()).isEqualTo(admin);
        assertThat(atendida.atendidaPorNombre()).isNotBlank();
        assertThat(atendida.atendidaEn()).isNotNull();

        assertThatThrownBy(() -> servicio.actualizar(registrada.id(),
                new ActualizarSolicitudArcoRequest("RECHAZADA", "Otra respuesta")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("RF-098"));

        assertThat(eventos(registrada.id())).containsExactly("REGISTRAR_SOLICITUD_ARCO",
                "CAMBIAR_ESTADO_SOLICITUD_ARCO", "CAMBIAR_ESTADO_SOLICITUD_ARCO");
        assertThat(detallesAuditados(registrada.id()))
                .noneMatch(detalle -> detalle.contains("teléfono") || detalle.contains("Titular"));
    }

    @Test
    @DisplayName("RF-098: el listado filtra por titular y estado, con el total de las filas visibles")
    void listadoFiltrado() {
        iniciarSesionComoAdministrador();
        UUID persona = insertarPersona();
        UUID acceso = servicio.registrar(new RegistrarSolicitudArcoRequest(persona, "ACCESO", "Copia de datos")).id();
        UUID oposicion = servicio.registrar(new RegistrarSolicitudArcoRequest(persona, "OPOSICION", "No difundir")).id();
        servicio.actualizar(oposicion, new ActualizarSolicitudArcoRequest("RECHAZADA", "Ya no se difunde"));

        Pagina<SolicitudArcoDto> todas = servicio.listar(new FiltroSolicitudesArco(null, null, persona, 0, 20));
        Pagina<SolicitudArcoDto> recibidas = servicio.listar(new FiltroSolicitudesArco(EstadoSolicitudArco.RECIBIDA,
                null, persona, 0, 20));

        assertThat(todas.totalElementos()).isEqualTo(2);
        assertThat(todas.contenido()).extracting(SolicitudArcoDto::id).containsExactlyInAnyOrder(acceso, oposicion);
        assertThat(recibidas.totalElementos()).isEqualTo(1);
        assertThat(recibidas.contenido()).extracting(SolicitudArcoDto::id).containsExactly(acceso);
    }

    @Test
    @DisplayName("VAL-015: una cuenta sin seguridad.arco:GESTIONAR no lista ni registra")
    void sinPermiso() {
        iniciarSesionComoAdministrador();
        UUID persona = insertarPersona();
        UUID perfil = jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado) VALUES ('Sin permisos') RETURNING id")
                .query(UUID.class).single();
        iniciarSesion(perfil);

        assertThatThrownBy(() -> servicio.listar(new FiltroSolicitudesArco(null, null, null, 0, 20)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("VAL-015"));
        assertThatThrownBy(() -> servicio.registrar(new RegistrarSolicitudArcoRequest(persona, "ACCESO", "Ver")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("VAL-015"));
    }

    @Test
    @DisplayName("RF-098: una persona inexistente responde 422 REFERENCIA_INVALIDA")
    void personaInexistente() {
        iniciarSesionComoAdministrador();

        assertThatThrownBy(() -> servicio.registrar(new RegistrarSolicitudArcoRequest(UUID.randomUUID(), "ACCESO",
                "Ver mis datos")))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("REFERENCIA_INVALIDA"));
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────────

    private UUID insertarPersona() {
        String curp = "PETU" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000))
                + "HMNRRS0" + ThreadLocalRandom.current().nextInt(10);
        return jdbc.sql("""
                        INSERT INTO siglim.personas (curp, nombre, apellido_paterno, apellido_materno, fecha_nacimiento)
                        VALUES (:curp, 'Titular', 'Arco', 'Prueba', DATE '1990-01-01') RETURNING id
                        """)
                .param("curp", curp).query(UUID.class).single();
    }

    private List<String> eventos(UUID solicitud) {
        return jdbc.sql("""
                        SELECT accion FROM siglim.auditoria
                         WHERE entidad = 'solicitudes_arco' AND entidad_id = :id ORDER BY id
                        """)
                .param("id", solicitud.toString()).query(String.class).list();
    }

    private List<String> detallesAuditados(UUID solicitud) {
        return jdbc.sql("""
                        SELECT coalesce(detalle::text, '') || coalesce(motivo, '') FROM siglim.auditoria
                         WHERE entidad = 'solicitudes_arco' AND entidad_id = :id
                        """)
                .param("id", solicitud.toString()).query(String.class).list();
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}
