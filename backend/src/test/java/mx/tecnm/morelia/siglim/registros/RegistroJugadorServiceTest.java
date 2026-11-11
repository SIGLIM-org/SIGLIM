package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.PersonaResumenDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.EquipoDelRegistro;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.RegistroEnTemporada;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.RequisitosMenor;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Alta de un registro de jugador con unicidad por CURP (RF-013, RF-107, VAL-017, VAL-025), sin Spring ni BD. */
class RegistroJugadorServiceTest {

    private static final String EDITAR = "registros.registro:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID PERSONA = UUID.fromString("21000000-0000-4000-8000-000000000001");
    private static final UUID EQUIPO = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final UUID OTRO_EQUIPO = UUID.fromString("60000000-0000-4000-8000-000000000002");
    private static final UUID CLUB = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID OTRO_CLUB = UUID.fromString("c0000000-0000-4000-8000-000000000002");
    private static final UUID TEMPORADA = UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID REGISTRO = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID PERFIL = UUID.fromString("55555555-5555-4555-8555-555555555555");
    /** 11 nov 2026 a las 12:00 en Morelia. */
    private static final Instant AHORA = Instant.parse("2026-11-11T18:00:00Z");
    private static final LocalDate HOY = LocalDate.parse("2026-11-11");
    private static final CrearRegistroJugadorRequest PETICION = new CrearRegistroJugadorRequest(PERSONA, EQUIPO);
    private static final EquipoDelRegistro EQUIPO_ACTIVO = equipo(true, EstadoTemporada.ACTIVA);
    private static final RegistroJugadorDto CREADO = new RegistroJugadorDto(REGISTRO,
            new PersonaResumenDto(PERSONA, "Ana López"),
            new ReferenciaDto(EQUIPO, "Halcones"),
            new ReferenciaDto(CLUB, "Club Halcones"),
            new ReferenciaDto(UUID.randomUUID(), "Libre"),
            new TemporadaResumenDto(TEMPORADA, "Temporada 2026", EstadoTemporada.ACTIVA),
            EstadoRegistro.PENDIENTE_VALIDACION, HOY, OffsetDateTime.parse("2026-11-11T12:00:00-06:00"));

    private final RegistroJugadorRepository registros = mock(RegistroJugadorRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);

    private RegistroJugadorService servicio = conReloj(AHORA);

    @BeforeEach
    void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
        given(autorizacion.alcance(EDITAR)).willReturn(new AlcanceAutorizacion(false, Set.of(CLUB), false));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Camino feliz ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-013: registra a un adulto PENDIENTE_VALIDACION, sobre el club del equipo y con auditoría")
    void registraAdulto() {
        prepararAlta(false);

        assertThat(servicio.registrar(PETICION)).isEqualTo(CREADO);

        verify(autorizacion).alcance(EDITAR);
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(registros).insertar(PERSONA, EQUIPO, TEMPORADA, HOY, PERFIL);
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().modulo()).isEqualTo("M-04");
        assertThat(evento.getValue().accion()).isEqualTo("REGISTRAR_REGISTRO_JUGADOR");
        assertThat(evento.getValue().entidad()).isEqualTo("registros_jugador");
        assertThat(evento.getValue().entidadId()).isEqualTo(REGISTRO.toString());
        assertThat(evento.getValue().detalle()).isEqualTo(Map.of(
                "personaId", PERSONA.toString(),
                "equipoId", EQUIPO.toString(),
                "temporadaId", TEMPORADA.toString(),
                "estado", "PENDIENTE_VALIDACION"));
    }

    @Test
    @DisplayName("DEC-76 · VAL-025: a un adulto no se le exigen tutor ni consentimiento")
    void adultoSinConsentimiento() {
        prepararAlta(false);

        servicio.registrar(PETICION);

        verify(registros, never()).requisitosMenor(any());
    }

    @Test
    @DisplayName("VAL-025: un menor con tutor vigente y su consentimiento sí se registra")
    void menorConRequisitos() {
        prepararAlta(true);
        given(registros.requisitosMenor(PERSONA)).willReturn(new RequisitosMenor(true, true));

        assertThat(servicio.registrar(PETICION)).isEqualTo(CREADO);
    }

    @Test
    @DisplayName("RN-039 · RF-013: con una BAJA en el mismo equipo se registra de nuevo; RECHAZADO no cuenta")
    void bajaEnElMismoEquipo() {
        prepararAlta(false);
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(List.of(
                previo(CLUB, EQUIPO, EstadoRegistro.BAJA)));

        assertThat(servicio.registrar(PETICION)).isEqualTo(CREADO);
    }

    @Test
    @DisplayName("DEC-89: alta_en y la minoría de edad se calculan con la fecha de Morelia, no la de UTC")
    void fechaDeMexico() {
        // 12 nov 01:00 UTC = 11 nov 19:00 en Morelia: quien cumple 18 el 12 nov todavía es menor.
        servicio = conReloj(Instant.parse("2026-11-12T01:00:00Z"));
        prepararAlta(false);
        given(registros.esMenor(PERSONA, HOY)).willReturn(Optional.of(true));
        given(registros.esMenor(PERSONA, HOY.plusDays(1))).willReturn(Optional.of(false));
        given(registros.requisitosMenor(PERSONA)).willReturn(new RequisitosMenor(false, false));

        assertThatThrownBy(() -> servicio.registrar(PETICION)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> assertThat(error.codigo()).isEqualTo("VAL-025"));

        verify(registros).esMenor(PERSONA, HOY);
        verify(registros, never()).esMenor(PERSONA, HOY.plusDays(1));
    }

    @Test
    @DisplayName("DEC-89: el INSERT lleva alta_en del día de Morelia después de las 18:00")
    void altaEnConFechaDeMexico() {
        servicio = conReloj(Instant.parse("2026-11-12T01:00:00Z"));
        prepararAlta(false);
        given(registros.esMenor(PERSONA, HOY)).willReturn(Optional.of(false));

        servicio.registrar(PETICION);

        verify(registros).insertar(PERSONA, EQUIPO, TEMPORADA, HOY, PERFIL);
    }

    // ── Orden de errores (DEC-85 a DEC-89) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-015: sin el permiso en ningún ámbito responde 403 sin leer el equipo")
    void sinPermiso() {
        given(autorizacion.alcance(EDITAR)).willThrow(Autorizacion.sinPermiso());

        esperar(() -> servicio.registrar(PETICION), HttpStatus.FORBIDDEN, "VAL-015");

        verifyNoInteractions(registros, auditoria);
    }

    @Test
    @DisplayName("DEC-85: sin personaId o sin equipoId responde 400 VALIDACION después del permiso")
    void faltanDatos() {
        esperar(() -> servicio.registrar(new CrearRegistroJugadorRequest(null, EQUIPO)),
                HttpStatus.BAD_REQUEST, "VALIDACION");
        esperar(() -> servicio.registrar(new CrearRegistroJugadorRequest(PERSONA, null)),
                HttpStatus.BAD_REQUEST, "VALIDACION");
        esperar(() -> servicio.registrar(null), HttpStatus.BAD_REQUEST, "VALIDACION");

        verifyNoInteractions(registros);
    }

    @Test
    @DisplayName("VAL-024: un equipo inexistente responde 404 sin exigir el permiso sobre un club")
    void equipoInexistente() {
        given(registros.buscarEquipo(LIGA, EQUIPO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(PETICION)).isInstanceOf(RecursoNoEncontradoException.class);

        verify(autorizacion, never()).exigir(any(), any());
    }

    @Test
    @DisplayName("VAL-024: un equipo de otro club responde 404 igual que uno inexistente")
    void equipoFueraDeAmbito() {
        given(registros.buscarEquipo(LIGA, EQUIPO)).willReturn(Optional.of(EQUIPO_ACTIVO));
        willThrow(Autorizacion.fueraDeAmbito(EDITAR))
                .given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.registrar(PETICION)).isInstanceOf(RecursoNoEncontradoException.class);

        verify(registros, never()).clubAfiliado(any(), any());
        verify(registros, never()).insertar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("RF-003: la temporada CERRADA responde 409 aunque el equipo también esté de baja")
    void temporadaCerrada() {
        given(registros.buscarEquipo(LIGA, EQUIPO)).willReturn(Optional.of(equipo(false, EstadoTemporada.CERRADA)));

        esperar(() -> servicio.registrar(PETICION), HttpStatus.CONFLICT, "RF-003");
    }

    @Test
    @DisplayName("RF-003: una temporada en CONFIGURACION sí admite registros")
    void temporadaEnConfiguracion() {
        prepararAlta(false);
        given(registros.buscarEquipo(LIGA, EQUIPO)).willReturn(Optional.of(equipo(true, EstadoTemporada.CONFIGURACION)));

        assertThat(servicio.registrar(PETICION)).isEqualTo(CREADO);
    }

    @Test
    @DisplayName("RF-013: un equipo en BAJA responde 422 antes de revisar la afiliación")
    void equipoDeBaja() {
        given(registros.buscarEquipo(LIGA, EQUIPO)).willReturn(Optional.of(equipo(false, EstadoTemporada.ACTIVA)));

        esperar(() -> servicio.registrar(PETICION), HttpStatus.UNPROCESSABLE_CONTENT, "RF-013");

        verify(registros, never()).clubAfiliado(any(), any());
    }

    @Test
    @DisplayName("RF-101 · DEC-87: un club sin tarjetón aprobado en la temporada responde 409")
    void clubSinAfiliacion() {
        given(registros.buscarEquipo(LIGA, EQUIPO)).willReturn(Optional.of(EQUIPO_ACTIVO));
        given(registros.clubAfiliado(CLUB, TEMPORADA)).willReturn(false);

        assertThatThrownBy(() -> servicio.registrar(PETICION)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("RF-101");
                    assertThat(error.getMessage()).contains("tarjetón");
                });

        verify(registros, never()).esMenor(any(), any());
    }

    @Test
    @DisplayName("DEC-88: una persona inexistente responde 422 REFERENCIA_INVALIDA")
    void personaInexistente() {
        prepararAlta(false);
        given(registros.esMenor(PERSONA, HOY)).willReturn(Optional.empty());

        esperar(() -> servicio.registrar(PETICION), HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA");

        verify(registros, never()).registrosEnTemporada(any(), any());
    }

    @Test
    @DisplayName("VAL-017: registro activo en otro club → 409 con nombres de club y equipo, sin estado ni ids")
    void registroActivoEnOtroClub() {
        prepararAlta(false);
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(List.of(
                new RegistroEnTemporada(OTRO_CLUB, "Club Águilas", OTRO_EQUIPO, "Águilas", EstadoRegistro.SUSPENDIDO)));

        assertThatThrownBy(() -> servicio.registrar(PETICION)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("VAL-017");
                    assertThat(error.propiedades()).containsExactly(
                            Map.entry("club", "Club Águilas"), Map.entry("equipo", "Águilas"));
                    assertThat(error.getMessage()).contains("Club Águilas", "Águilas")
                            .doesNotContain("SUSPENDIDO", "suspend", OTRO_CLUB.toString(), OTRO_EQUIPO.toString());
                });

        verify(registros, never()).insertar(any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-017: en el mismo club el mensaje dice «tu club» y el equipo")
    void registroActivoEnElMismoClub() {
        prepararAlta(false);
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(List.of(
                previo(CLUB, OTRO_EQUIPO, EstadoRegistro.ACTIVO)));

        assertThatThrownBy(() -> servicio.registrar(PETICION)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-017");
                    assertThat(error.getMessage()).contains("tu club", "Otro equipo");
                });
    }

    @Test
    @DisplayName("VAL-017: en el mismo equipo el mensaje lo dice")
    void registroActivoEnElMismoEquipo() {
        prepararAlta(false);
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(List.of(
                previo(CLUB, EQUIPO, EstadoRegistro.PENDIENTE_VALIDACION)));

        assertThatThrownBy(() -> servicio.registrar(PETICION)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-017");
                    assertThat(error.getMessage()).contains("este equipo");
                });
    }

    @Test
    @DisplayName("VAL-017 va antes que VAL-026: activo en un club y de baja en otro")
    void activoGanaABaja() {
        prepararAlta(false);
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(List.of(
                previo(OTRO_CLUB, OTRO_EQUIPO, EstadoRegistro.BAJA),
                previo(CLUB, OTRO_EQUIPO, EstadoRegistro.ACTIVO)));

        esperar(() -> servicio.registrar(PETICION), HttpStatus.CONFLICT, "VAL-017");
    }

    @Test
    @DisplayName("VAL-026: una BAJA en otro club en la temporada responde 409 sin nombrar el club")
    void bajaEnOtroClub() {
        prepararAlta(false);
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(List.of(
                new RegistroEnTemporada(OTRO_CLUB, "Club Águilas", OTRO_EQUIPO, "Águilas", EstadoRegistro.BAJA)));

        assertThatThrownBy(() -> servicio.registrar(PETICION)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("VAL-026");
                    assertThat(error.getMessage()).doesNotContain("Águilas");
                    assertThat(error.propiedades()).isEmpty();
                });
    }

    @Test
    @DisplayName("RN-039 · RF-016: una BAJA en otro equipo del mismo club responde 409")
    void bajaEnOtroEquipoDelClub() {
        prepararAlta(false);
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(List.of(
                previo(CLUB, OTRO_EQUIPO, EstadoRegistro.BAJA)));

        esperar(() -> servicio.registrar(PETICION), HttpStatus.CONFLICT, "RN-039");
    }

    @Test
    @DisplayName("VAL-025: un menor sin tutela vigente responde 422 con requisito TUTOR")
    void menorSinTutor() {
        prepararAlta(true);
        given(registros.requisitosMenor(PERSONA)).willReturn(new RequisitosMenor(false, false));

        assertThatThrownBy(() -> servicio.registrar(PETICION)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.codigo()).isEqualTo("VAL-025");
                    assertThat(error.propiedades()).containsEntry("requisito", "TUTOR");
                    assertThat(error.getMessage()).contains("tutor");
                });

        verify(registros, never()).insertar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-025: un menor con tutor pero sin consentimiento responde 422 con requisito CONSENTIMIENTO")
    void menorSinConsentimiento() {
        prepararAlta(true);
        given(registros.requisitosMenor(PERSONA)).willReturn(new RequisitosMenor(true, false));

        assertThatThrownBy(() -> servicio.registrar(PETICION)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-025");
                    assertThat(error.propiedades()).containsEntry("requisito", "CONSENTIMIENTO");
                    assertThat(error.getMessage()).contains("consentimiento");
                });
    }

    @Test
    @DisplayName("VAL-017 · DEC-90: si una carrera gana el INSERT, se vuelve a consultar y responde 409 con nombres")
    void carrera() {
        prepararAlta(false);
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(
                List.of(),
                List.of(new RegistroEnTemporada(OTRO_CLUB, "Club Águilas", OTRO_EQUIPO, "Águilas",
                        EstadoRegistro.PENDIENTE_VALIDACION)));
        given(registros.insertar(PERSONA, EQUIPO, TEMPORADA, HOY, PERFIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.registrar(PETICION)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-017");
                    assertThat(error.propiedades()).containsEntry("club", "Club Águilas");
                });

        verifyNoInteractions(auditoria);
    }

    // ── Apoyo ──────────────────────────────────────────────────────────────────────────────────

    private RegistroJugadorService conReloj(Instant instante) {
        return new RegistroJugadorService(registros, autorizacion, auditoria, Clock.fixed(instante, ZoneOffset.UTC),
                new SiglimPropiedades(null, null, null, LIGA));
    }

    /** Equipo activo, club afiliado, persona existente sin registros y alta exitosa. */
    private void prepararAlta(boolean menor) {
        given(registros.buscarEquipo(LIGA, EQUIPO)).willReturn(Optional.of(EQUIPO_ACTIVO));
        given(registros.clubAfiliado(CLUB, TEMPORADA)).willReturn(true);
        given(registros.esMenor(PERSONA, HOY)).willReturn(Optional.of(menor));
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(List.of());
        given(registros.insertar(PERSONA, EQUIPO, TEMPORADA, HOY, PERFIL)).willReturn(Optional.of(REGISTRO));
        given(registros.buscar(REGISTRO)).willReturn(Optional.of(CREADO));
    }

    private static EquipoDelRegistro equipo(boolean activo, EstadoTemporada estadoTemporada) {
        return new EquipoDelRegistro(EQUIPO, "Halcones", activo, CLUB, "Club Halcones", TEMPORADA,
                "Temporada 2026", estadoTemporada);
    }

    private static RegistroEnTemporada previo(UUID club, UUID equipo, EstadoRegistro estado) {
        return new RegistroEnTemporada(club, club.equals(CLUB) ? "Club Halcones" : "Club Águilas", equipo,
                equipo.equals(EQUIPO) ? "Halcones" : "Otro equipo", estado);
    }

    private static void esperar(ThrowingCallable accion, HttpStatus estado,
            String codigo) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.estado()).isEqualTo(estado);
            assertThat(error.codigo()).isEqualTo(codigo);
        });
    }
}
