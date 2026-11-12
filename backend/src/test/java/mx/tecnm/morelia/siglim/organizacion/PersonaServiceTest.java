package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Personas por CURP, alta, edición y tutela (RF-010, RF-011, RF-106), sin Spring ni base de datos. */
class PersonaServiceTest {

    private static final String CONSULTAR = "organizacion.persona:CONSULTAR";
    private static final String EDITAR = "organizacion.persona:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTRA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TUTOR = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PERFIL = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID TUTELA = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final Instant AHORA = Instant.parse("2026-11-11T18:00:00Z");
    private static final LocalDate HOY = LocalDate.parse("2026-11-11");
    private static final String CURP = "GODE840101HDFRNS09";
    private static final String CURP_NUEVA = "LOMA900202MMNRRN01";
    private static final PersonaDto PERSONA = new PersonaDto(ID, CURP, "Ana", "López", null);
    private static final PersonaDetalleDto DETALLE = new PersonaDetalleDto(ID, CURP, "Ana", "López", null,
            LocalDate.parse("1984-01-01"), "ana@correo.mx", null);
    private static final AlcanceAutorizacion LIGA_COMPLETA = new AlcanceAutorizacion(true, Set.of(), false);
    private static final AlcanceAutorizacion SOLO_CLUB = new AlcanceAutorizacion(false, Set.of(OTRA), false);

    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final PersonaService servicio = new PersonaService(personas, autorizacion, auditoria,
            Clock.fixed(AHORA, ZoneOffset.UTC), new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Consulta por CURP (H-22.2) ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-010: consulta por CURP con el permiso en cualquier ámbito")
    void buscaPorCurp() {
        given(personas.buscarPorCurp(CURP)).willReturn(Optional.of(PERSONA));
        given(personas.esMenor(ID, HOY)).willReturn(Optional.of(false));

        assertThat(servicio.buscarPorCurp("  " + CURP.toLowerCase() + " ")).isEqualTo(PERSONA.conMinoria(false));

        verify(autorizacion).alcance(CONSULTAR);
        verify(personas).buscarPorCurp(CURP);
    }

    @Test
    @DisplayName("RF-010: una CURP sin persona responde NO_ENCONTRADO (VAL-024)")
    void noExiste() {
        given(personas.buscarPorCurp(CURP)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.buscarPorCurp(CURP))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("DEC-10: acepta una CURP con X en el sexo, igual que ck_personas_curp")
    void aceptaCurpConSexoX() {
        String curpX = "GODE840101XDFRNS09";
        PersonaDto persona = new PersonaDto(ID, curpX, "Ana", "López", null);
        given(personas.buscarPorCurp(curpX)).willReturn(Optional.of(persona));
        given(personas.esMenor(ID, HOY)).willReturn(Optional.of(false));

        assertThat(servicio.buscarPorCurp(curpX)).isEqualTo(persona.conMinoria(false));
        verify(personas).buscarPorCurp(curpX);
    }

    @Test
    @DisplayName("DEC-104: la consulta por CURP dice si es menor con la fecha de Morelia")
    void consultaPorCurpCalculaMinoriaConFechaDeMorelia() {
        // 03:00 UTC del 12-nov son las 21:00 del 11-nov en Morelia: quien nació el 12-nov-2008 cumple
        // 18 ese día en UTC, pero en la zona de negocio todavía es menor (DEC-89).
        PersonaService casiMedianoche = new PersonaService(personas, autorizacion, auditoria,
                Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneOffset.UTC),
                new SiglimPropiedades(null, null, null, LIGA));
        LocalDate hoyEnMorelia = LocalDate.parse("2026-11-11");
        given(personas.buscarPorCurp(CURP)).willReturn(Optional.of(PERSONA));
        given(personas.esMenor(ID, hoyEnMorelia)).willReturn(Optional.of(true));

        PersonaDto encontrada = casiMedianoche.buscarPorCurp(CURP);

        assertThat(encontrada.esMenor()).isTrue();
        assertThat(encontrada.id()).isEqualTo(ID);
        verify(personas).esMenor(ID, hoyEnMorelia);
        verify(personas, never()).esMenor(ID, LocalDate.parse("2026-11-12"));
    }

    @Test
    @DisplayName("DEC-10: una CURP mal formada responde 400 VALIDACION sin consultar la tabla")
    void formatoInvalido() {
        assertThatThrownBy(() -> servicio.buscarPorCurp("GODE"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VALIDACION");
                    assertThat(error.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verify(autorizacion).alcance(CONSULTAR);
        verifyNoInteractions(personas);
    }

    // ── Alta (DEC-84) ───────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-84: registra la persona normalizada, con creado_por y auditoría sin datos personales")
    void registra() {
        given(personas.buscarPorCurp(CURP)).willReturn(Optional.empty());
        given(personas.insertar(any(), any())).willReturn(Optional.of(ID));
        given(personas.buscarDetalle(ID)).willReturn(Optional.of(DETALLE));

        PersonaDetalleDto creada = servicio.registrar(new CrearPersonaRequest(" " + CURP.toLowerCase(), " Ana ",
                "López", "  ", LocalDate.parse("1984-01-01"), "ana@correo.mx", ""));

        assertThat(creada).isEqualTo(DETALLE);
        ArgumentCaptor<PersonaDetalleDto> datos = ArgumentCaptor.forClass(PersonaDetalleDto.class);
        verify(personas).insertar(datos.capture(), org.mockito.ArgumentMatchers.eq(PERFIL));
        assertThat(datos.getValue().curp()).isEqualTo(CURP);
        assertThat(datos.getValue().nombre()).isEqualTo("Ana");
        assertThat(datos.getValue().apellidoMaterno()).isNull();
        assertThat(datos.getValue().telefono()).isNull();
        EventoAuditoria evento = evento();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_PERSONA");
        assertThat(evento.modulo()).isEqualTo("M-03");
        assertThat(evento.entidad()).isEqualTo("personas");
        assertThat(evento.detalle()).isNull();
    }

    @Test
    @DisplayName("VAL-015: sin permiso responde 403 antes de validar los datos")
    void registrarSinPermiso() {
        given(autorizacion.alcance(EDITAR)).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(() -> servicio.registrar(new CrearPersonaRequest(null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("VAL-015"));
        verifyNoInteractions(personas);
    }

    @Test
    @DisplayName("DEC-62: faltan obligatorios, correo mal formado o fecha futura responden 400 VALIDACION")
    void registrarDatosInvalidos() {
        LocalDate nacimiento = LocalDate.parse("1984-01-01");
        List<CrearPersonaRequest> invalidas = List.of(
                new CrearPersonaRequest("GODE", "Ana", "López", null, nacimiento, null, null),
                new CrearPersonaRequest(CURP, " ", "López", null, nacimiento, null, null),
                new CrearPersonaRequest(CURP, "Ana", null, null, nacimiento, null, null),
                new CrearPersonaRequest(CURP, "Ana", "López", null, null, null, null),
                new CrearPersonaRequest(CURP, "Ana", "López", null, HOY.plusDays(1), null, null),
                new CrearPersonaRequest(CURP, "Ana", "López", null, nacimiento, "ana@correo", null),
                new CrearPersonaRequest(CURP, "Ana", "López", null, nacimiento, null, "1".repeat(21)));

        for (CrearPersonaRequest peticion : invalidas) {
            assertThatThrownBy(() -> servicio.registrar(peticion))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                        assertThat(error.codigo()).isEqualTo("VALIDACION");
                        assertThat(error.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
        }
        verifyNoInteractions(personas, auditoria);
    }

    @Test
    @DisplayName("RF-010: una CURP ya registrada en otro club responde 409 con personaId y no inserta")
    void registrarCurpExistente() {
        given(personas.buscarPorCurp(CURP)).willReturn(Optional.of(PERSONA));

        assertThatThrownBy(() -> servicio.registrar(peticionValida()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("RF-010");
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.propiedades()).containsEntry("personaId", ID);
                });
        verify(personas, never()).insertar(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-010: si una carrera inserta la CURP antes, responde 409 con el id de la ganadora")
    void registrarCarrera() {
        given(personas.buscarPorCurp(CURP)).willReturn(Optional.empty(), Optional.of(PERSONA));
        given(personas.insertar(any(), any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(peticionValida()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("RF-010");
                    assertThat(error.propiedades()).containsEntry("personaId", ID);
                });
        verifyNoInteractions(auditoria);
    }

    // ── Edición (DEC-83) ────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-83: la Directiva edita correo y teléfono; la auditoría lleva solo los nombres de los campos")
    void editaContacto() {
        given(autorizacion.alcance(EDITAR)).willReturn(SOLO_CLUB);
        given(personas.buscarDetalleParaActualizar(ID)).willReturn(Optional.of(DETALLE));
        given(personas.buscarDetalle(ID)).willReturn(Optional.of(DETALLE));

        servicio.actualizar(ID, new ActualizarPersonaRequest(null, "Ana", null, null, null, "nuevo@correo.mx", "4431234567"));

        ArgumentCaptor<PersonaDetalleDto> datos = ArgumentCaptor.forClass(PersonaDetalleDto.class);
        verify(personas).actualizar(datos.capture());
        assertThat(datos.getValue().correo()).isEqualTo("nuevo@correo.mx");
        assertThat(datos.getValue().telefono()).isEqualTo("4431234567");
        assertThat(datos.getValue().nombre()).isEqualTo("Ana");
        EventoAuditoria evento = evento();
        assertThat(evento.accion()).isEqualTo("EDITAR_PERSONA");
        assertThat(evento.detalle()).isEqualTo(Map.of("campos", List.of("correo", "telefono")));
    }

    @Test
    @DisplayName("DEC-83: un cuerpo sin campos responde 400 antes de buscar la persona")
    void editarSinCampos() {
        given(autorizacion.alcance(EDITAR)).willReturn(LIGA_COMPLETA);

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarPersonaRequest(null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("VALIDACION"));
        verifyNoInteractions(personas);
    }

    @Test
    @DisplayName("VAL-024: una persona inexistente o no visible por RLS responde 404")
    void editarNoVisible() {
        given(autorizacion.alcance(EDITAR)).willReturn(LIGA_COMPLETA);
        given(personas.buscarDetalleParaActualizar(ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarPersonaRequest(null, null, null, null, null, "a@b.mx", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("DEC-77: sin el permiso en toda la liga, cambiar el nombre responde 403 VAL-015")
    void directivaNoCambiaIdentidad() {
        given(autorizacion.alcance(EDITAR)).willReturn(SOLO_CLUB);
        given(personas.buscarDetalleParaActualizar(ID)).willReturn(Optional.of(DETALLE));

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarPersonaRequest(null, "Anabel", null, null, null, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-015");
                    assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getMessage()).contains("Secretario de la liga");
                });
        verify(personas, never()).actualizar(any());
    }

    @Test
    @DisplayName("DEC-83: con el permiso en toda la liga, una CURP de otra persona responde 409 DUPLICADO")
    void curpDeOtraPersona() {
        given(autorizacion.alcance(EDITAR)).willReturn(LIGA_COMPLETA);
        given(personas.buscarDetalleParaActualizar(ID)).willReturn(Optional.of(DETALLE));
        given(personas.buscarPorCurp(CURP_NUEVA)).willReturn(Optional.of(new PersonaDto(OTRA, CURP_NUEVA, "Otra", "Persona", null)));

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarPersonaRequest(CURP_NUEVA, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("DUPLICADO");
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                });
        verify(personas, never()).actualizar(any());
    }

    @Test
    @DisplayName("DEC-83: una fecha de nacimiento futura responde 400 aunque sea el Secretario")
    void fechaFuturaEnEdicion() {
        given(autorizacion.alcance(EDITAR)).willReturn(LIGA_COMPLETA);
        given(personas.buscarDetalleParaActualizar(ID)).willReturn(Optional.of(DETALLE));

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarPersonaRequest(null, null, null, null, HOY.plusDays(1), null, null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("VALIDACION"));
    }

    @Test
    @DisplayName("DEC-83: sin cambios reales responde la persona sin escribir ni auditar")
    void editarSinCambios() {
        given(autorizacion.alcance(EDITAR)).willReturn(SOLO_CLUB);
        given(personas.buscarDetalleParaActualizar(ID)).willReturn(Optional.of(DETALLE));

        assertThat(servicio.actualizar(ID, new ActualizarPersonaRequest(null, null, null, null, null, "ana@correo.mx", null)))
                .isEqualTo(DETALLE);
        verify(personas, never()).actualizar(any());
        verifyNoInteractions(auditoria);
    }

    // ── Tutela (DEC-82) ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-106: registra la tutela de un menor con un tutor adulto y la audita")
    void registraTutela() {
        tutelaValidaHasta(true);
        given(personas.insertarTutela(ID, TUTOR, "Madre", PERFIL)).willReturn(Optional.of(
                new TutelaDto(TUTELA, ID, TUTOR, "Madre", true, OffsetDateTime.parse("2026-11-11T12:00:00-06:00"))));

        TutelaDto tutela = servicio.registrarTutela(ID, new RegistrarTutelaRequest(TUTOR, "  Madre "));

        assertThat(tutela.id()).isEqualTo(TUTELA);
        EventoAuditoria evento = evento();
        assertThat(evento.accion()).isEqualTo("REGISTRAR_TUTELA");
        assertThat(evento.entidad()).isEqualTo("tutelas");
        assertThat(evento.detalle()).isEqualTo(Map.of("menorId", ID.toString(), "tutorId", TUTOR.toString()));
    }

    @Test
    @DisplayName("DEC-82: parentesco vacío o de más de 40 caracteres responde 400 antes de buscar al menor")
    void tutelaDatosInvalidos() {
        for (RegistrarTutelaRequest peticion : List.of(new RegistrarTutelaRequest(null, "Madre"),
                new RegistrarTutelaRequest(TUTOR, " "), new RegistrarTutelaRequest(TUTOR, "x".repeat(41)))) {
            assertThatThrownBy(() -> servicio.registrarTutela(ID, peticion))
                    .isInstanceOfSatisfying(ReglaNegocioException.class,
                            error -> assertThat(error.codigo()).isEqualTo("VALIDACION"));
        }
        verifyNoInteractions(personas);
    }

    @Test
    @DisplayName("DEC-82: un menor inexistente responde 404 antes de revisar al tutor")
    void tutelaMenorInexistente() {
        given(personas.esMenor(ID, HOY)).willReturn(Optional.empty());
        given(personas.personaDelUsuario()).willReturn(Optional.of(TUTOR));

        assertThatThrownBy(() -> servicio.registrarTutela(ID, new RegistrarTutelaRequest(TUTOR, "Madre")))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("DEC-79: registrarse como tutor desde la propia cuenta responde 403 VAL-022 antes que RF-106")
    void tutelaPropiaCuenta() {
        given(personas.esMenor(ID, HOY)).willReturn(Optional.of(false));
        given(personas.personaDelUsuario()).willReturn(Optional.of(TUTOR));

        assertThatThrownBy(() -> servicio.registrarTutela(ID, new RegistrarTutelaRequest(TUTOR, "Madre")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-022");
                    assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    @DisplayName("RF-106: un adulto como menor responde 422 RF-106")
    void tutelaDeAdulto() {
        given(personas.esMenor(ID, HOY)).willReturn(Optional.of(false));
        given(personas.personaDelUsuario()).willReturn(Optional.empty());

        esperarError(() -> servicio.registrarTutela(ID, new RegistrarTutelaRequest(TUTOR, "Madre")),
                "RF-106", HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("DEC-82: un tutor inexistente responde 422 REFERENCIA_INVALIDA")
    void tutorInexistente() {
        given(personas.esMenor(ID, HOY)).willReturn(Optional.of(true));
        given(personas.personaDelUsuario()).willReturn(Optional.empty());
        given(personas.esMenor(TUTOR, HOY)).willReturn(Optional.empty());

        esperarError(() -> servicio.registrarTutela(ID, new RegistrarTutelaRequest(TUTOR, "Madre")),
                "REFERENCIA_INVALIDA", HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("RF-106: un menor como tutor responde 422 RF-106")
    void tutorMenor() {
        given(personas.esMenor(ID, HOY)).willReturn(Optional.of(true));
        given(personas.personaDelUsuario()).willReturn(Optional.empty());
        given(personas.esMenor(TUTOR, HOY)).willReturn(Optional.of(true));

        esperarError(() -> servicio.registrarTutela(ID, new RegistrarTutelaRequest(TUTOR, "Hermano")),
                "RF-106", HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("DEC-82: una tutela vigente igual responde 409 DUPLICADO sin insertar")
    void tutelaDuplicada() {
        tutelaValidaHasta(true);
        given(personas.esTutorVigente(ID, TUTOR)).willReturn(true);

        esperarError(() -> servicio.registrarTutela(ID, new RegistrarTutelaRequest(TUTOR, "Madre")),
                "DUPLICADO", HttpStatus.CONFLICT);
        verify(personas, never()).insertarTutela(any(), any(), any(), any());
    }

    // ── Apoyo ──────────────────────────────────────────────────────────────────────────────────

    private void tutelaValidaHasta(boolean menor) {
        given(personas.esMenor(ID, HOY)).willReturn(Optional.of(menor));
        given(personas.personaDelUsuario()).willReturn(Optional.empty());
        given(personas.esMenor(TUTOR, HOY)).willReturn(Optional.of(false));
    }

    private static CrearPersonaRequest peticionValida() {
        return new CrearPersonaRequest(CURP, "Ana", "López", null, LocalDate.parse("1984-01-01"), null, null);
    }

    private EventoAuditoria evento() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().ligaId()).isEqualTo(LIGA);
        return evento.getValue();
    }

    private static void esperarError(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String codigo,
            HttpStatus estado) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.codigo()).isEqualTo(codigo);
            assertThat(error.estado()).isEqualTo(estado);
        });
    }
}
