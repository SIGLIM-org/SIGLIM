package mx.tecnm.morelia.siglim.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * H-21.1 contra PostgreSQL real, con {@link ConsultaAuditoriaService} completo: autorización, SQL y
 * RLS. Como el contenedor se comparte, cada prueba acota el resultado con acciones o perfiles propios.
 * La inmutabilidad en la base (UPDATE, DELETE y TRUNCATE) ya la cubre {@link AuditoriaServiceIT}.
 */
class ConsultaAuditoriaIT extends BaseDatosPrueba {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final String POR_DEFECTO = "ocurridoEn,desc";

    @Autowired
    private ConsultaAuditoriaService servicio;

    @Autowired
    private AuditoriaService auditoria;

    @Autowired
    private PlatformTransactionManager transacciones;

    @Autowired
    private JsonMapper json;

    // ── Filtros ───────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-134: filtra por perfil")
    void filtraPorPerfil() {
        UUID ana = crearPerfil("Ana " + UUID.randomUUID(), null);
        UUID beto = crearPerfil("Beto " + UUID.randomUUID(), null);
        String accion = accionNueva();
        iniciarSesion(ana);
        registrar("M-03", accion);
        iniciarSesion(beto);
        registrar("M-03", accion);

        iniciarSesionComoAdministrador();
        var pagina = servicio.listar(new FiltroAuditoria(ana, List.of(), List.of(accion), null, null, 0, 50, POR_DEFECTO));

        assertThat(pagina.contenido()).extracting(fila -> fila.perfil().id()).containsExactly(ana);
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-134: filtra por uno o varios módulos")
    void filtraPorModulo() {
        iniciarSesionComoAdministrador();
        String en03 = accionNueva();
        String en06 = accionNueva();
        String en08 = accionNueva();
        registrar("M-03", en03);
        registrar("M-06", en06);
        registrar("M-08", en08);
        List<String> acciones = List.of(en03, en06, en08);

        var uno = servicio.listar(new FiltroAuditoria(null, List.of("M-06"), acciones, null, null, 0, 50, POR_DEFECTO));
        var varios = servicio.listar(new FiltroAuditoria(null, List.of("M-06", "M-08"), acciones, null, null, 0, 50,
                POR_DEFECTO));

        assertThat(uno.contenido()).extracting(RegistroAuditoriaDto::accion).containsExactly(en06);
        assertThat(varios.contenido()).extracting(RegistroAuditoriaDto::accion).containsExactly(en08, en06);
        assertThat(varios.totalElementos()).isEqualTo(2);
    }

    @Test
    @DisplayName("RF-134: filtra por acción, y una acción bien formada que no existe devuelve página vacía")
    void filtraPorAccion() {
        iniciarSesionComoAdministrador();
        String buscada = accionNueva();
        String otra = accionNueva();
        registrar("M-03", buscada);
        registrar("M-03", otra);

        var pagina = servicio.listar(porAccion(buscada));
        var inexistente = servicio.listar(porAccion(accionNueva()));

        assertThat(pagina.contenido()).extracting(RegistroAuditoriaDto::accion).containsExactly(buscada);
        assertThat(inexistente.contenido()).isEmpty();
        assertThat(inexistente.totalElementos()).isZero();
    }

    @Test
    @DisplayName("RF-134: el rango incluye desde y excluye hasta, en el borde exacto")
    void filtraPorRango() {
        iniciarSesionComoAdministrador();
        String accion = accionNueva();
        registrar("M-03", accion);
        registrar("M-03", accion);
        registrar("M-03", accion);
        List<RegistroAuditoriaDto> filas = servicio.listar(new FiltroAuditoria(null, List.of(), List.of(accion), null,
                null, 0, 50, "ocurridoEn,asc")).contenido();
        assertThat(filas).hasSize(3);
        OffsetDateTime primera = filas.get(0).ocurridoEn();
        OffsetDateTime segunda = filas.get(1).ocurridoEn();
        OffsetDateTime tercera = filas.get(2).ocurridoEn();

        var desdeLaSegunda = servicio.listar(new FiltroAuditoria(null, List.of(), List.of(accion), segunda, null, 0, 50,
                "ocurridoEn,asc"));
        var hastaLaSegunda = servicio.listar(new FiltroAuditoria(null, List.of(), List.of(accion), null, segunda, 0, 50,
                "ocurridoEn,asc"));
        var entreBordes = servicio.listar(new FiltroAuditoria(null, List.of(), List.of(accion), primera, tercera, 0, 50,
                "ocurridoEn,asc"));

        assertThat(desdeLaSegunda.contenido()).extracting(RegistroAuditoriaDto::id)
                .containsExactly(filas.get(1).id(), filas.get(2).id());
        assertThat(hastaLaSegunda.contenido()).extracting(RegistroAuditoriaDto::id).containsExactly(filas.get(0).id());
        assertThat(entreBordes.contenido()).extracting(RegistroAuditoriaDto::id)
                .containsExactly(filas.get(0).id(), filas.get(1).id());
    }

    // ── Página y orden ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-134: 120 registros dan 3 páginas de 50, del más reciente al más antiguo y sin repetir filas")
    void paginaYOrden() {
        iniciarSesionComoAdministrador();
        String accion = accionNueva();
        for (int i = 0; i < 120; i++) {
            registrar("M-03", accion);
        }

        List<RegistroAuditoriaDto> todas = new ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            var resultado = servicio.listar(new FiltroAuditoria(null, List.of(), List.of(accion), null, null, pagina, 50,
                    POR_DEFECTO));
            assertThat(resultado.totalElementos()).isEqualTo(120);
            assertThat(resultado.totalPaginas()).isEqualTo(3);
            assertThat(resultado.contenido()).hasSize(pagina < 2 ? 50 : 20);
            todas.addAll(resultado.contenido());
        }

        assertThat(todas).extracting(RegistroAuditoriaDto::id).doesNotHaveDuplicates()
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(todas).extracting(fila -> fila.ocurridoEn().toInstant())
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("RF-134: con la misma fecha el id desempata en la dirección pedida")
    void desempatePorId() {
        iniciarSesionComoAdministrador();
        String accion = accionNueva();
        enTransaccion(() -> {
            for (int i = 0; i < 3; i++) {
                auditoria.registrar(evento("M-03", accion, null, null));
            }
        });

        var descendente = servicio.listar(porAccion(accion)).contenido();
        var ascendente = servicio.listar(new FiltroAuditoria(null, List.of(), List.of(accion), null, null, 0, 50,
                "ocurridoEn,asc")).contenido();

        assertThat(descendente).extracting(RegistroAuditoriaDto::ocurridoEn).as("misma transacción, misma fecha")
                .containsOnly(descendente.getFirst().ocurridoEn());
        assertThat(descendente).extracting(RegistroAuditoriaDto::id).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(ascendente).extracting(RegistroAuditoriaDto::id).isSorted();
    }

    // ── Contenido de la fila ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-134: el detalle jsonb y el motivo completo llegan íntegros")
    void detalleYMotivoIntegros() {
        iniciarSesionComoAdministrador();
        String accion = accionNueva();
        String motivo = "Motivo largo ".repeat(100).strip();
        registrar(evento("M-08", accion, motivo,
                Map.of("estadoNuevo", "APROBADA", "goles", 3, "anidado", Map.of("lista", List.of(1, 2)))));

        RegistroAuditoriaDto fila = servicio.listar(porAccion(accion)).contenido().getFirst();

        assertThat(fila.motivo()).isEqualTo(motivo);
        assertThat(fila.detalle()).isEqualTo(json.readTree(
                "{\"estadoNuevo\":\"APROBADA\",\"goles\":3,\"anidado\":{\"lista\":[1,2]}}"));
        assertThat(fila.modulo()).isEqualTo("M-08");
        assertThat(fila.entidad()).isEqualTo("pruebas");
        assertThat(fila.ocurridoEn().getOffset().getTotalSeconds())
                .as("en la zona de negocio, America/Mexico_City").isEqualTo(-6 * 3600);
    }

    @Test
    @DisplayName("RF-134: un registro sin perfil y sin detalle los devuelve como null")
    void sinPerfilNiDetalle() {
        String accion = accionNueva();
        jdbc.sql("""
                        INSERT INTO siglim.auditoria (liga_id, modulo, accion, entidad)
                        VALUES (:liga, 'M-01', :accion, 'pruebas')
                        """)
                .param("liga", LIGA).param("accion", accion).update();
        iniciarSesionComoAdministrador();

        RegistroAuditoriaDto fila = servicio.listar(porAccion(accion)).contenido().getFirst();

        assertThat(fila.perfil()).isNull();
        assertThat(fila.detalle()).isNull();
    }

    @Test
    @DisplayName("RNF-012: la respuesta no contiene el correo ni la persona del perfil")
    void sinDatosPersonales() {
        String correo = UUID.randomUUID() + "@prueba.mx";
        UUID perfil = crearPerfil("Usuario " + UUID.randomUUID(), correo);
        iniciarSesion(perfil);
        String accion = accionNueva();
        registrar("M-03", accion);

        iniciarSesionComoAdministrador();
        String listado = json.writeValueAsString(servicio.listar(porAccion(accion)));
        String opciones = json.writeValueAsString(servicio.opcionesFiltro());

        assertThat(listado).contains(perfil.toString()).doesNotContain(correo, "correo", "personaId");
        assertThat(opciones).contains(perfil.toString()).doesNotContain(correo, "correo", "personaId");
    }

    // ── Permisos y ámbito ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-015: un rol sin auditoria.registro:CONSULTAR recibe 403 en el listado y en las opciones")
    void rolSinPermiso() {
        iniciarSesion(perfilConRol("PROGRAMADOR"));

        assertThatThrownBy(() -> servicio.listar(porAccion(accionNueva())))
                .isInstanceOfSatisfying(ReglaNegocioException.class, ConsultaAuditoriaIT::esSinPermiso);
        assertThatThrownBy(servicio::opcionesFiltro)
                .isInstanceOfSatisfying(ReglaNegocioException.class, ConsultaAuditoriaIT::esSinPermiso);
    }

    @Test
    @DisplayName("RF-134: el Presidente, con ámbito LIGA, consulta la auditoría sin el permiso de usuarios")
    void presidenteConsulta() {
        iniciarSesionComoAdministrador();
        String accion = accionNueva();
        registrar("M-03", accion);

        iniciarSesion(perfilConRol("PRESIDENTE"));
        var pagina = servicio.listar(porAccion(accion));

        assertThat(pagina.contenido()).extracting(RegistroAuditoriaDto::accion).containsExactly(accion);
        assertThat(pagina.contenido().getFirst().perfil().nombreMostrado()).isNotBlank();
        assertThat(servicio.opcionesFiltro().acciones()).contains(accion);
    }

    @Test
    @DisplayName("VAL-024: con el permiso en ámbito CLUB el listado, el total y las opciones salen vacíos")
    void ambitoDeClub() {
        iniciarSesionComoAdministrador();
        String accion = accionNueva();
        registrar("M-03", accion);
        assertThat(servicio.listar(porAccion(accion)).totalElementos()).as("la fila existe").isEqualTo(1);

        iniciarSesion(perfilConRolDeClub());
        var pagina = servicio.listar(porAccion(accion));
        OpcionesFiltroAuditoriaDto opciones = servicio.opcionesFiltro();

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
        assertThat(pagina.totalPaginas()).isZero();
        assertThat(opciones.perfiles()).isEmpty();
        assertThat(opciones.modulos()).isEmpty();
        assertThat(opciones.acciones()).isEmpty();
    }

    // ── Opciones de los filtros ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-134: las opciones traen perfiles, módulos y acciones distintos y ordenados, también perfiles inactivos")
    void opcionesDistintasYOrdenadas() {
        String token = UUID.randomUUID().toString();
        UUID inactivo = crearPerfil("Opción " + token + " A", null);
        UUID activo = crearPerfil("Opción " + token + " B", null);
        String accionA = "OPCION_A_" + sufijo();
        String accionB = "OPCION_B_" + sufijo();
        iniciarSesion(activo);
        registrar("M-08", accionB);
        registrar("M-08", accionB);
        iniciarSesion(inactivo);
        registrar("M-03", accionA);
        jdbc.sql("UPDATE siglim.perfiles SET activo = false WHERE id = :id").param("id", inactivo).update();

        iniciarSesionComoAdministrador();
        OpcionesFiltroAuditoriaDto opciones = servicio.opcionesFiltro();

        assertThat(opciones.perfiles()).extracting(PerfilResumenDto::id).doesNotHaveDuplicates()
                .containsSubsequence(inactivo, activo);
        assertThat(opciones.modulos()).doesNotHaveDuplicates().isSorted().contains("M-03", "M-08");
        assertThat(opciones.acciones()).doesNotHaveDuplicates().containsSubsequence(accionA, accionB);
    }

    // ── Apoyo ─────────────────────────────────────────────────────────────────────────────────

    private static void esSinPermiso(ReglaNegocioException error) {
        assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(error.codigo()).isEqualTo("VAL-015");
    }

    private static FiltroAuditoria porAccion(String accion) {
        return new FiltroAuditoria(null, List.of(), List.of(accion), null, null, 0, 50, POR_DEFECTO);
    }

    private static String sufijo() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    /** Acción propia de la prueba: acota el resultado aunque el contenedor sea compartido. */
    private static String accionNueva() {
        return "PRUEBA_" + sufijo();
    }

    private static EventoAuditoria evento(String modulo, String accion, String motivo, Map<String, Object> detalle) {
        return new EventoAuditoria(LIGA, modulo, accion, "pruebas", UUID.randomUUID().toString(), motivo, detalle);
    }

    private void registrar(String modulo, String accion) {
        registrar(evento(modulo, accion, null, null));
    }

    /** Cada registro en su propia transacción: {@code now()} le da una fecha distinta. */
    private void registrar(EventoAuditoria evento) {
        enTransaccion(() -> auditoria.registrar(evento));
    }

    private void enTransaccion(Runnable operacion) {
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> operacion.run());
    }

    private UUID crearPerfil(String nombre, String correo) {
        return jdbc.sql("INSERT INTO siglim.perfiles (nombre_mostrado, correo) VALUES (:nombre, :correo) RETURNING id")
                .param("nombre", nombre).param("correo", correo).query(UUID.class).single();
    }

    /** Perfil con un rol de la matriz de la migración, con su ámbito predeterminado. */
    private UUID perfilConRol(String rol) {
        UUID perfil = crearPerfil("Usuario de prueba", null);
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito)
                        SELECT :perfil, id, liga_id, ambito_predeterminado FROM siglim.roles WHERE codigo = :rol
                        """)
                .param("perfil", perfil).param("rol", rol).update();
        return perfil;
    }

    /** Perfil con un rol propio que da {@code auditoria.registro:CONSULTAR} en ámbito de club. */
    private UUID perfilConRolDeClub() {
        UUID club = jdbc.sql("INSERT INTO siglim.clubes (liga_id, nombre) VALUES (:liga, :nombre) RETURNING id")
                .param("liga", LIGA).param("nombre", "Club " + UUID.randomUUID()).query(UUID.class).single();
        UUID rol = jdbc.sql("""
                        INSERT INTO siglim.roles (liga_id, codigo, nombre, ambito_predeterminado)
                        VALUES (:liga, :codigo, 'Rol de prueba', 'CLUB') RETURNING id
                        """)
                .param("liga", LIGA)
                .param("codigo", "PRUEBA_" + UUID.randomUUID().toString().substring(0, 8))
                .query(UUID.class).single();
        jdbc.sql("""
                        INSERT INTO siglim.rol_permisos (rol_id, permiso_id)
                        SELECT :rol, id FROM siglim.permisos WHERE codigo = 'auditoria.registro:CONSULTAR'
                        """)
                .param("rol", rol).update();
        UUID perfil = crearPerfil("Usuario de club", null);
        jdbc.sql("""
                        INSERT INTO siglim.asignaciones_rol (perfil_id, rol_id, liga_id, ambito, club_id)
                        VALUES (:perfil, :rol, :liga, 'CLUB', :club)
                        """)
                .param("perfil", perfil).param("rol", rol).param("liga", LIGA).param("club", club).update();
        return perfil;
    }

    private static void iniciarSesion(UUID perfil) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfil));
    }
}
