package mx.tecnm.morelia.siglim.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.RolRepository.FilaRol;
import mx.tecnm.morelia.siglim.seguridad.UsuarioRepository.FilaAsignacion;
import mx.tecnm.morelia.siglim.seguridad.UsuarioRepository.FilaPerfil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link UsuarioService}. Sin Spring ni base de datos. */
class UsuarioServiceTest {

    private static final String CONSULTAR = "seguridad.usuario:CONSULTAR";
    private static final String CREAR = "seguridad.usuario:CREAR";
    private static final String EDITAR = "seguridad.usuario:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    /** Cuenta sobre la que operan las pruebas. */
    private static final UUID PERFIL = UUID.fromString("0b7c3e1a-2d4f-4a6b-9c8d-7e6f5a4b3c2d");
    /** Cuenta de la sesión: otra cuenta, para no chocar con VAL-022 (DEC-53). */
    private static final UUID ADMIN = UUID.fromString("7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d");
    private static final UUID AUTH = UUID.fromString("6f1d2a3b-4c5d-4e6f-8a9b-0c1d2e3f4a5b");
    private static final UUID ROL_ADMIN = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ROL_CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ROL_SISTEMA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID CLUB = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID ASIGNACION = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID OTRA_ASIGNACION = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final Instant AHORA = Instant.parse("2030-03-01T12:00:00Z");
    private static final OffsetDateTime CREADO_EN = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final String MOTIVO = "Cambio de funciones en la liga";

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RolRepository roles = mock(RolRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final InvitadorAuth invitador = mock(InvitadorAuth.class);
    private final UsuarioService servicio = new UsuarioService(usuarios, roles, autorizacion, auditoria,
            invitador, new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        iniciarSesionComo(ADMIN);
    }

    private static void iniciarSesionComo(UUID perfilId) {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, perfilId));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Datos de prueba ──────────────────────────────────────────────────────────────────────

    private static FilaPerfil perfil(boolean activo) {
        return new FilaPerfil(PERFIL, "Ana López", "ana@ejemplo.mx", activo, CREADO_EN);
    }

    private static FilaAsignacion asignacion(boolean activa, OffsetDateTime revocadaEn) {
        return new FilaAsignacion(ASIGNACION, PERFIL, ROL_CLUB, "DIRECTIVA_CLUB", "Directiva de club",
                "CLUB", CLUB, "Club Halcones", activa, CREADO_EN, revocadaEn);
    }

    private static FilaRol rol(UUID id, String codigo, String nombre, String ambito, boolean asignable) {
        return new FilaRol(id, codigo, nombre, null, ambito, asignable);
    }

    private static FilaRol rolClub() {
        return rol(ROL_CLUB, "DIRECTIVA_CLUB", "Directiva de club", "CLUB", true);
    }

    // ── Listado y detalle ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-094: el listado filtra por ámbito y arma los chips con las asignaciones activas")
    void listarConAlcanceDeLiga() {
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(true, Set.of(), false);
        FiltroUsuarios filtro = new FiltroUsuarios("ana", null, null, null, true, 0, 20);
        given(autorizacion.alcance(CONSULTAR)).willReturn(alcance);
        given(usuarios.listar(filtro, alcance)).willReturn(List.of(perfil(true)));
        given(usuarios.listarAsignaciones(List.of(PERFIL), true)).willReturn(List.of(asignacion(true, null)));
        given(usuarios.contar(filtro, alcance)).willReturn(1L);

        var pagina = servicio.listar(filtro);

        assertThat(pagina.contenido()).singleElement()
                .satisfies(usuario -> assertThat(usuario.asignaciones()).singleElement()
                        .satisfies(chip -> {
                            assertThat(chip.rol().codigo()).isEqualTo("DIRECTIVA_CLUB");
                            assertThat(chip.ambito()).isEqualTo("CLUB");
                            assertThat(chip.club().nombre()).isEqualTo("Club Halcones");
                        }));
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    @DisplayName("ADR-0007: sin ámbito de liga el listado responde vacío y no consulta las cuentas")
    void listarSinAmbitoDeLiga() {
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(false, Set.of(CLUB), false);
        given(autorizacion.alcance(CONSULTAR)).willReturn(alcance);

        var pagina = servicio.listar(new FiltroUsuarios(null, null, null, null, null, 0, 20));

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
        verifyNoInteractions(usuarios);
    }

    @Test
    @DisplayName("RF-094: la consulta exige el permiso de liga y devuelve también las revocadas")
    void consultarConRevocadas() {
        given(usuarios.buscarPerfil(PERFIL)).willReturn(Optional.of(perfil(true)));
        given(usuarios.listarAsignaciones(PERFIL, false))
                .willReturn(List.of(asignacion(false, CREADO_EN.plusDays(1))));

        UsuarioDto usuario = servicio.consultar(PERFIL);

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        assertThat(usuario.asignaciones()).singleElement().satisfies(asignacion -> {
            assertThat(asignacion.activa()).isFalse();
            assertThat(asignacion.revocadaEn()).isEqualTo(CREADO_EN.plusDays(1));
        });
    }

    @Test
    @DisplayName("VAL-024: una cuenta inexistente responde 404 después de autorizar")
    void consultarInexistente() {
        given(usuarios.buscarPerfil(PERFIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(PERFIL))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Invitación ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("H-03: invita, crea el perfil con su asignación, audita y devuelve el detalle")
    void invitarConAsignacionDeClub() {
        given(usuarios.existeCorreo("ana@ejemplo.mx")).willReturn(false);
        given(roles.buscar(LIGA, ROL_CLUB)).willReturn(Optional.of(rolClub()));
        given(usuarios.existeClub(LIGA, CLUB)).willReturn(true);
        given(invitador.invitar("ana@ejemplo.mx")).willReturn(AUTH);
        given(usuarios.insertarPerfil(AUTH, "Ana López", "ana@ejemplo.mx")).willReturn(PERFIL);
        given(usuarios.insertarAsignacion(PERFIL, ROL_CLUB, LIGA, "CLUB", CLUB, ADMIN)).willReturn(ASIGNACION);
        given(usuarios.buscarPerfil(PERFIL)).willReturn(Optional.of(perfil(true)));
        given(usuarios.listarAsignaciones(PERFIL, false)).willReturn(List.of(asignacion(true, null)));

        UsuarioDto creado = servicio.invitar(new CrearUsuarioRequest("  Ana López  ", " ANA@Ejemplo.mx ",
                List.of(new AsignacionRequest(ROL_CLUB, Ambito.CLUB, CLUB))));

        verify(autorizacion).exigir(CREAR, ObjetivoAutorizacion.deLiga());
        verify(usuarios).insertarPerfil(AUTH, "Ana López", "ana@ejemplo.mx");
        verify(usuarios).insertarAsignacion(PERFIL, ROL_CLUB, LIGA, "CLUB", CLUB, ADMIN);
        assertThat(creado.id()).isEqualTo(PERFIL);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("INVITAR_USUARIO");
        assertThat(evento.getValue().entidad()).isEqualTo("perfiles");
        assertThat(evento.getValue().detalle().toString()).contains("DIRECTIVA_CLUB", "CLUB");
    }

    @Test
    @DisplayName("RF-094: si se omite el ámbito, usa el predeterminado del rol")
    void invitarSinAmbitoUsaElPredeterminado() {
        given(usuarios.existeCorreo("ana@ejemplo.mx")).willReturn(false);
        given(roles.buscar(LIGA, ROL_CLUB)).willReturn(Optional.of(rolClub()));
        given(usuarios.existeClub(LIGA, CLUB)).willReturn(true);
        given(invitador.invitar("ana@ejemplo.mx")).willReturn(AUTH);
        given(usuarios.insertarPerfil(AUTH, "Ana", "ana@ejemplo.mx")).willReturn(PERFIL);
        given(usuarios.insertarAsignacion(PERFIL, ROL_CLUB, LIGA, "CLUB", CLUB, ADMIN)).willReturn(ASIGNACION);
        given(usuarios.buscarPerfil(PERFIL)).willReturn(Optional.of(perfil(true)));
        given(usuarios.listarAsignaciones(PERFIL, false)).willReturn(List.of(asignacion(true, null)));

        servicio.invitar(new CrearUsuarioRequest("Ana", "ana@ejemplo.mx",
                List.of(new AsignacionRequest(ROL_CLUB, null, CLUB))));

        verify(usuarios).insertarAsignacion(PERFIL, ROL_CLUB, LIGA, "CLUB", CLUB, ADMIN);
    }

    @Test
    @DisplayName("H-03 criterio 2: una asignación CLUB sin club responde 400 y no invita")
    void invitarClubSinClub() {
        given(usuarios.existeCorreo("ana@ejemplo.mx")).willReturn(false);
        given(roles.buscar(LIGA, ROL_CLUB)).willReturn(Optional.of(rolClub()));
        given(usuarios.existeClub(LIGA, CLUB)).willReturn(true);

        assertThatThrownBy(() -> servicio.invitar(new CrearUsuarioRequest("Ana", "ana@ejemplo.mx",
                List.of(new AsignacionRequest(ROL_CLUB, Ambito.CLUB, null)))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.codigo()).isEqualTo(ErroresTecnicos.VALIDACION);
                });
        verifyNoInteractions(invitador);
        verify(usuarios, never()).insertarPerfil(any(), any(), any());
    }

    @Test
    @DisplayName("RF-094: un correo con cuenta responde 409 y no llama a Supabase Auth")
    void invitarCorreoDuplicado() {
        given(roles.buscar(LIGA, ROL_CLUB)).willReturn(Optional.of(rolClub()));
        given(usuarios.existeClub(LIGA, CLUB)).willReturn(true);
        given(usuarios.existeCorreo("ana@ejemplo.mx")).willReturn(true);

        assertThatThrownBy(() -> servicio.invitar(new CrearUsuarioRequest("Ana", "ana@ejemplo.mx",
                List.of(new AsignacionRequest(ROL_CLUB, Ambito.CLUB, CLUB)))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo(ErroresTecnicos.DUPLICADO);
                });
        verifyNoInteractions(invitador);
    }

    @Test
    @DisplayName("RF-094: un rol no asignable responde 400")
    void invitarRolNoAsignable() {
        given(usuarios.existeCorreo("ana@ejemplo.mx")).willReturn(false);
        given(roles.buscar(LIGA, ROL_SISTEMA))
                .willReturn(Optional.of(rol(ROL_SISTEMA, "SISTEMA", "Sistema", "LIGA", false)));

        assertThatThrownBy(() -> servicio.invitar(new CrearUsuarioRequest("Ana", "ana@ejemplo.mx",
                List.of(new AsignacionRequest(ROL_SISTEMA, Ambito.LIGA, null)))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error ->
                        assertThat(error.codigo()).isEqualTo(ErroresTecnicos.VALIDACION));
        verifyNoInteractions(invitador);
    }

    @Test
    @DisplayName("ADR-0003: si falla el alta en la base, elimina la cuenta invitada de Auth")
    void invitacionCompensaLaCuentaDeAuth() {
        given(usuarios.existeCorreo("ana@ejemplo.mx")).willReturn(false);
        given(roles.buscar(LIGA, ROL_CLUB)).willReturn(Optional.of(rolClub()));
        given(usuarios.existeClub(LIGA, CLUB)).willReturn(true);
        given(invitador.invitar("ana@ejemplo.mx")).willReturn(AUTH);
        given(usuarios.insertarPerfil(AUTH, "Ana", "ana@ejemplo.mx"))
                .willThrow(new IllegalStateException("fallo de la base"));

        assertThatThrownBy(() -> servicio.invitar(new CrearUsuarioRequest("Ana", "ana@ejemplo.mx",
                List.of(new AsignacionRequest(ROL_CLUB, Ambito.CLUB, CLUB)))))
                .isInstanceOf(IllegalStateException.class);

        verify(invitador).eliminar(AUTH);
        verifyNoInteractions(auditoria);
    }

    // ── Activación y desactivación ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("H-03 criterio 3: desactivar sin motivo responde 400 RN-021 y no cambia la cuenta")
    void desactivarSinMotivo() {
        given(usuarios.buscarPerfil(PERFIL)).willReturn(Optional.of(perfil(true)));

        assertThatThrownBy(() -> servicio.actualizar(PERFIL, new ActualizarUsuarioRequest(false, "corto")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.codigo()).isEqualTo("RN-021");
                });
        verify(usuarios, never()).actualizarActivo(any(), anyBoolean());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("H-03 criterio 3: desactivar con motivo deja la cuenta inactiva y auditoría")
    void desactivarConMotivo() {
        given(usuarios.buscarPerfil(PERFIL)).willReturn(Optional.of(perfil(true)), Optional.of(perfil(false)));
        given(usuarios.listarAsignaciones(PERFIL, false)).willReturn(List.of(asignacion(true, null)));

        UsuarioDto actualizado = servicio.actualizar(PERFIL, new ActualizarUsuarioRequest(false, MOTIVO));

        assertThat(actualizado.activo()).isFalse();
        verify(usuarios).actualizarActivo(PERFIL, false);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("DESACTIVAR_CUENTA");
        assertThat(evento.getValue().motivo()).isEqualTo(MOTIVO);
        assertThat(evento.getValue().detalle()).containsEntry("activo", false);
    }

    @Test
    @DisplayName("RF-094: activar no exige motivo")
    void activarSinMotivo() {
        given(usuarios.buscarPerfil(PERFIL)).willReturn(Optional.of(perfil(false)), Optional.of(perfil(true)));
        given(usuarios.listarAsignaciones(PERFIL, false)).willReturn(List.of());

        UsuarioDto actualizado = servicio.actualizar(PERFIL, new ActualizarUsuarioRequest(true, null));

        assertThat(actualizado.activo()).isTrue();
        verify(usuarios).actualizarActivo(PERFIL, true);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("ACTIVAR_CUENTA");
    }

    // ── Asignaciones ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-094: una asignación activa repetida responde 409")
    void asignarDuplicada() {
        given(usuarios.buscarPerfil(PERFIL)).willReturn(Optional.of(perfil(true)));
        given(roles.buscar(LIGA, ROL_CLUB)).willReturn(Optional.of(rolClub()));
        given(usuarios.existeClub(LIGA, CLUB)).willReturn(true);
        given(usuarios.existeAsignacionActiva(PERFIL, ROL_CLUB, CLUB)).willReturn(true);

        assertThatThrownBy(() -> servicio.asignar(PERFIL,
                new AsignacionRequest(ROL_CLUB, Ambito.CLUB, CLUB)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo(ErroresTecnicos.DUPLICADO);
                });
        verify(usuarios, never()).insertarAsignacion(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-094: revocar deja la asignación inactiva con su motivo y la audita")
    void revocarAsignacion() {
        OffsetDateTime revocadaEn = CREADO_EN.plusDays(2);
        given(usuarios.buscarAsignacion(ASIGNACION))
                .willReturn(Optional.of(asignacion(true, null)), Optional.of(asignacion(false, revocadaEn)));

        UsuarioDto.AsignacionDto revocada = servicio.revocar(ASIGNACION,
                new RevocarAsignacionRequest(MOTIVO));

        assertThat(revocada.activa()).isFalse();
        assertThat(revocada.revocadaEn()).isEqualTo(revocadaEn);
        verify(usuarios).revocarAsignacion(ASIGNACION);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().accion()).isEqualTo("REVOCAR_ASIGNACION");
        assertThat(evento.getValue().motivo()).isEqualTo(MOTIVO);
        assertThat(evento.getValue().entidad()).isEqualTo("asignaciones_rol");
    }

    @Test
    @DisplayName("RF-094: revocar una asignación ya inactiva responde 409 sin volver a escribir")
    void revocarYaRevocada() {
        given(usuarios.buscarAsignacion(ASIGNACION))
                .willReturn(Optional.of(asignacion(false, CREADO_EN)));

        assertThatThrownBy(() -> servicio.revocar(ASIGNACION, new RevocarAsignacionRequest(MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error ->
                        assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT));
        verify(usuarios, never()).revocarAsignacion(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin el permiso de edición no asigna ni escribe")
    void asignarSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion)
                .exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.asignar(PERFIL,
                new AsignacionRequest(ROL_CLUB, Ambito.CLUB, CLUB)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error ->
                        assertThat(error.codigo()).isEqualTo("VAL-015"));
        verifyNoInteractions(usuarios, roles, auditoria);
    }

    // ── Separación de funciones sobre la propia cuenta (VAL-022, DEC-53) ──────────────────────

    private static void assertProhibidaVal022(Throwable error) {
        assertThat(error).isInstanceOfSatisfying(ReglaNegocioException.class, regla -> {
            assertThat(regla.estado()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(regla.codigo()).isEqualTo("VAL-022");
        });
    }

    private void verificarSinEscrituraNiAuditoria() {
        verify(usuarios, never()).insertarAsignacion(any(), any(), any(), any(), any(), any());
        verify(usuarios, never()).revocarAsignacion(any());
        verify(usuarios, never()).actualizarActivo(any(), anyBoolean());
        verify(auditoria, never()).registrar(any());
    }

    @Test
    @DisplayName("H-03 criterio 4: agregarse una asignación responde 403 VAL-022 sin escribir ni auditar")
    void asignarseAsiMismo() {
        iniciarSesionComo(PERFIL);
        given(usuarios.buscarPerfil(PERFIL)).willReturn(Optional.of(perfil(true)));
        given(roles.buscar(LIGA, ROL_CLUB)).willReturn(Optional.of(rolClub()));
        given(usuarios.existeClub(LIGA, CLUB)).willReturn(true);

        assertThatThrownBy(() -> servicio.asignar(PERFIL, new AsignacionRequest(ROL_CLUB, Ambito.CLUB, CLUB)))
                .satisfies(UsuarioServiceTest::assertProhibidaVal022);
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        verifyNoInteractions(roles);
        verificarSinEscrituraNiAuditoria();
    }

    @Test
    @DisplayName("H-03 criterio 4: revocar una asignación propia responde 403 VAL-022 sin escribir ni auditar")
    void revocarAsignacionPropia() {
        iniciarSesionComo(PERFIL);
        given(usuarios.buscarAsignacion(ASIGNACION)).willReturn(Optional.of(asignacion(true, null)));

        assertThatThrownBy(() -> servicio.revocar(ASIGNACION, new RevocarAsignacionRequest(MOTIVO)))
                .satisfies(UsuarioServiceTest::assertProhibidaVal022);
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        verificarSinEscrituraNiAuditoria();
    }

    @Test
    @DisplayName("H-03 criterio 4: desactivar la propia cuenta responde 403 VAL-022 aunque falte el motivo")
    void desactivarCuentaPropiaSinMotivo() {
        iniciarSesionComo(PERFIL);
        given(usuarios.buscarPerfil(PERFIL)).willReturn(Optional.of(perfil(true)));

        // Sin motivo otra cuenta recibiría 400 RN-021; VAL-022 se evalúa antes.
        assertThatThrownBy(() -> servicio.actualizar(PERFIL, new ActualizarUsuarioRequest(false, null)))
                .satisfies(UsuarioServiceTest::assertProhibidaVal022);
        verificarSinEscrituraNiAuditoria();
    }

    @Test
    @DisplayName("H-03 criterio 4: activar la propia cuenta ya activa también responde 403 VAL-022")
    void activarCuentaPropiaYaActiva() {
        iniciarSesionComo(PERFIL);
        given(usuarios.buscarPerfil(PERFIL)).willReturn(Optional.of(perfil(true)));

        assertThatThrownBy(() -> servicio.actualizar(PERFIL, new ActualizarUsuarioRequest(true, null)))
                .satisfies(UsuarioServiceTest::assertProhibidaVal022);
        verificarSinEscrituraNiAuditoria();
    }

    @Test
    @DisplayName("H-03 criterio 4: revocar una asignación propia ya revocada responde 403, no 409")
    void revocarAsignacionPropiaYaRevocada() {
        iniciarSesionComo(PERFIL);
        given(usuarios.buscarAsignacion(ASIGNACION)).willReturn(Optional.of(asignacion(false, CREADO_EN)));

        assertThatThrownBy(() -> servicio.revocar(ASIGNACION, new RevocarAsignacionRequest(MOTIVO)))
                .satisfies(UsuarioServiceTest::assertProhibidaVal022);
        verificarSinEscrituraNiAuditoria();
    }
}
