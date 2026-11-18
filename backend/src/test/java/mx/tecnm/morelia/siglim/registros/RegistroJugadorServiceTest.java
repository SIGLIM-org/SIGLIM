package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.registros.DocumentoRegistroRepository.RegistroDeDocumentos;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.DocumentoRegistro;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.RequisitoDocumento;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.PersonaResumenDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.EquipoDelRegistro;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.RegistroAnterior;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.RegistroEnTemporada;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorRepository.RequisitosMenor;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import mx.tecnm.morelia.siglim.temporadas.TipoCategoria;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Alta de un registro de jugador con unicidad por CURP (RF-013, RF-107, VAL-017, VAL-025) y su
 * validación por la liga (H-10.2: RF-017, RF-109, VAL-001, DEC-97 a DEC-102), sin Spring ni BD.
 */
class RegistroJugadorServiceTest {

    private static final String EDITAR = "registros.registro:EDITAR";
    private static final String VALIDAR = "registros.registro:VALIDAR";
    private static final String OBSERVACIONES = "La CURP no corresponde a la persona registrada";
    private static final UUID OTRO_PERFIL = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID CATEGORIA = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final TipoDocumentoDto IDENTIFICACION = new TipoDocumentoDto(2, "IDENTIFICACION", "Identificación");
    private static final TipoDocumentoDto FOTOGRAFIA = new TipoDocumentoDto(5, "FOTOGRAFIA", "Fotografía");
    private static final TipoDocumentoDto INE_TUTOR = new TipoDocumentoDto(6, "INE_TUTOR", "Credencial INE del tutor");
    /** Los requisitos de la semilla para LIBRE, en orden de {@code tipo_documento_id}. */
    private static final List<RequisitoDocumento> REQUISITOS_LIBRE = List.of(
            new RequisitoDocumento(IDENTIFICACION, 1, false, false),
            new RequisitoDocumento(FOTOGRAFIA, 2, false, false),
            new RequisitoDocumento(INE_TUTOR, 1, true, false));
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID PERSONA = UUID.fromString("21000000-0000-4000-8000-000000000001");
    private static final UUID EQUIPO = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final UUID OTRO_EQUIPO = UUID.fromString("60000000-0000-4000-8000-000000000002");
    private static final UUID CLUB = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID OTRO_CLUB = UUID.fromString("c0000000-0000-4000-8000-000000000002");
    private static final UUID TEMPORADA = UUID.fromString("50000000-0000-4000-8000-000000000001");
    private static final UUID REGISTRO = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID REGISTRO_ANTERIOR = UUID.fromString("80000000-0000-4000-8000-000000000002");
    private static final UUID TEMPORADA_ANTERIOR = UUID.fromString("50000000-0000-4000-8000-000000000000");
    private static final RegistroAnterior ANTERIOR_MISMO_CLUB = new RegistroAnterior(REGISTRO_ANTERIOR, CLUB,
            "Club Halcones", OTRO_EQUIPO, "Halcones 2025", TEMPORADA_ANTERIOR, "Temporada 2025",
            EstadoRegistro.ACTIVO);
    private static final RegistroAnterior ANTERIOR_OTRO_CLUB = new RegistroAnterior(REGISTRO_ANTERIOR, OTRO_CLUB,
            "Club Águilas", OTRO_EQUIPO, "Águilas 2025", TEMPORADA_ANTERIOR, "Temporada 2025",
            EstadoRegistro.ACTIVO);
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
            EstadoRegistro.PENDIENTE_VALIDACION, HOY, OffsetDateTime.parse("2026-11-11T12:00:00-06:00"),
            null, null, null, null, null);
    private static final RegistroJugadorDto VALIDADO = new RegistroJugadorDto(REGISTRO,
            new PersonaResumenDto(PERSONA, "Ana López"),
            new ReferenciaDto(EQUIPO, "Halcones"),
            new ReferenciaDto(CLUB, "Club Halcones"),
            new ReferenciaDto(CATEGORIA, "Segunda"),
            new TemporadaResumenDto(TEMPORADA, "Temporada 2026", EstadoTemporada.ACTIVA),
            EstadoRegistro.ACTIVO, HOY, OffsetDateTime.parse("2026-11-11T12:00:00-06:00"),
            new ReferenciaDto(PERFIL, "Secretario"), OffsetDateTime.parse("2026-11-11T12:00:00-06:00"), null,
            null, null);
    private static final String MOTIVO_BAJA = "El jugador se mudó de ciudad";
    private static final RegistroJugadorDto DE_BAJA = new RegistroJugadorDto(REGISTRO, VALIDADO.persona(),
            VALIDADO.equipo(), VALIDADO.club(), VALIDADO.categoria(), VALIDADO.temporada(), EstadoRegistro.BAJA, HOY,
            VALIDADO.creadoEn(), VALIDADO.validadoPor(), VALIDADO.validadoEn(), null, HOY, MOTIVO_BAJA);

    private final RegistroJugadorRepository registros = mock(RegistroJugadorRepository.class);
    private final DocumentoRegistroRepository documentos = mock(DocumentoRegistroRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivo = mock(MotivoOperacion.class);

    private RegistroJugadorService servicio = conReloj(AHORA);

    @BeforeEach
    void iniciarSesion() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
        given(autorizacion.alcance(EDITAR)).willReturn(new AlcanceAutorizacion(false, Set.of(CLUB), false));
        given(autorizacion.alcance(VALIDAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));
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

    // ── Renovación y transferencia (B-06, DEC-146) ─────────────────────────────────────────────

    @Test
    @DisplayName("RF-015 · DEC-146: renueva en el mismo club, enlaza el registro anterior y audita")
    void renuevaEnElMismoClub() {
        prepararMovimiento(false, ANTERIOR_MISMO_CLUB, true);

        assertThat(servicio.renovar(new MovimientoRegistroRequest(PERSONA, EQUIPO))).isEqualTo(CREADO);

        verify(registros).registroAnterior(PERSONA, TEMPORADA);
        verify(registros, never()).permiteCambioClub(any());
        verify(motivo).fijar("Renovación desde la temporada «Temporada 2025»");
        verify(registros).insertar(PERSONA, EQUIPO, TEMPORADA, HOY, REGISTRO_ANTERIOR, PERFIL);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("RENOVAR_REGISTRO");
        assertThat(evento.entidad()).isEqualTo("registros_jugador");
        assertThat(evento.entidadId()).isEqualTo(REGISTRO.toString());
        assertThat(evento.detalle()).containsExactly(
                Map.entry("personaId", PERSONA.toString()),
                Map.entry("equipoId", EQUIPO.toString()),
                Map.entry("temporadaId", TEMPORADA.toString()),
                Map.entry("registroAnteriorId", REGISTRO_ANTERIOR.toString()),
                Map.entry("clubAnteriorId", CLUB.toString()),
                Map.entry("equipoAnteriorId", OTRO_EQUIPO.toString()),
                Map.entry("temporadaAnteriorId", TEMPORADA_ANTERIOR.toString()),
                Map.entry("estado", "PENDIENTE_VALIDACION"));
        assertThat(evento.detalle().toString()).doesNotContain("Ana", "López", "curp");
    }

    @Test
    @DisplayName("RF-016 · RF-111: transfiere a otro club si la temporada permite el cambio y audita")
    void transfiereConCambioPermitido() {
        prepararMovimiento(false, ANTERIOR_OTRO_CLUB, true);

        assertThat(servicio.transferir(new MovimientoRegistroRequest(PERSONA, EQUIPO))).isEqualTo(CREADO);

        verify(registros).permiteCambioClub(TEMPORADA);
        verify(motivo).fijar("Transferencia desde el club «Club Águilas» de la temporada «Temporada 2025»");
        verify(registros).insertar(PERSONA, EQUIPO, TEMPORADA, HOY, REGISTRO_ANTERIOR, PERFIL);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("TRANSFERIR_REGISTRO");
        assertThat(evento.detalle()).containsEntry("clubAnteriorId", OTRO_CLUB.toString());
        assertThat(evento.detalle()).containsEntry("estado", "PENDIENTE_VALIDACION");
    }

    @Test
    @DisplayName("RF-111 · DEC-13: la temporada que no permite cambio de club responde 409 sin insertar")
    void transferenciaSinCambioDeClub() {
        prepararMovimiento(false, ANTERIOR_OTRO_CLUB, false);

        assertThatThrownBy(() -> servicio.transferir(new MovimientoRegistroRequest(PERSONA, EQUIPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("RF-111");
                    assertThat(error.getMessage()).contains("no permite cambios de club");
                });

        verify(registros, never()).insertar(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-015 · RF-016: una renovación con registro de otro club y una transferencia del mismo club se rechazan")
    void movimientoDelTipoEquivocado() {
        prepararMovimiento(false, ANTERIOR_OTRO_CLUB, true);

        assertThatThrownBy(() -> servicio.renovar(new MovimientoRegistroRequest(PERSONA, EQUIPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.codigo()).isEqualTo("RF-015");
                    assertThat(error.getMessage()).contains("transferencia");
                });

        prepararMovimiento(false, ANTERIOR_MISMO_CLUB, true);
        assertThatThrownBy(() -> servicio.transferir(new MovimientoRegistroRequest(PERSONA, EQUIPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.codigo()).isEqualTo("RF-016");
                    assertThat(error.getMessage()).contains("renovación");
                });

        verify(registros, never()).insertar(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-015 · RF-016: sin registro anterior responde 422 con el código del movimiento")
    void sinRegistroAnterior() {
        prepararMovimiento(false, null, true);

        assertThatThrownBy(() -> servicio.renovar(new MovimientoRegistroRequest(PERSONA, EQUIPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.codigo()).isEqualTo("RF-015");
                    assertThat(error.getMessage()).contains("registro anterior");
                });

        verify(registros, never()).insertar(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-026: una baja en otro club de la temporada destino se rechaza antes de buscar el anterior")
    void movimientoDentroDeLaTemporada() {
        given(registros.buscarEquipo(LIGA, EQUIPO)).willReturn(Optional.of(EQUIPO_ACTIVO));
        given(registros.clubAfiliado(CLUB, TEMPORADA)).willReturn(true);
        given(registros.esMenor(PERSONA, HOY)).willReturn(Optional.of(false));
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(List.of(
                new RegistroEnTemporada(OTRO_CLUB, "Club Águilas", OTRO_EQUIPO, "Águilas", EstadoRegistro.BAJA)));

        esperar(() -> servicio.renovar(new MovimientoRegistroRequest(PERSONA, EQUIPO)), HttpStatus.CONFLICT, "VAL-026");

        verify(registros, never()).registroAnterior(any(), any());
        verify(registros, never()).insertar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-025: un menor sin tutor no se transfiere")
    void transferenciaDeMenorSinTutor() {
        prepararMovimiento(true, ANTERIOR_OTRO_CLUB, true);
        given(registros.requisitosMenor(PERSONA)).willReturn(new RequisitosMenor(false, false));

        assertThatThrownBy(() -> servicio.transferir(new MovimientoRegistroRequest(PERSONA, EQUIPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.codigo()).isEqualTo("VAL-025");
                    assertThat(error.propiedades()).containsEntry("requisito", "TUTOR");
                });

        verify(registros, never()).insertar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("VAL-017: una persona con registro activo en la temporada destino no se renueva")
    void renovacionConRegistroActivoEnLaTemporada() {
        prepararMovimiento(false, ANTERIOR_MISMO_CLUB, true);
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(List.of(
                new RegistroEnTemporada(OTRO_CLUB, "Club Águilas", OTRO_EQUIPO, "Águilas", EstadoRegistro.ACTIVO)));

        assertThatThrownBy(() -> servicio.renovar(new MovimientoRegistroRequest(PERSONA, EQUIPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("VAL-017");
                    assertThat(error.getMessage()).contains("Club Águilas");
                });

        verify(registros, never()).registroAnterior(any(), any());
        verify(registros, never()).insertar(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-025: un menor con tutor pero sin consentimiento vigente no se renueva")
    void renovacionDeMenorSinConsentimiento() {
        prepararMovimiento(true, ANTERIOR_MISMO_CLUB, true);
        given(registros.requisitosMenor(PERSONA)).willReturn(new RequisitosMenor(true, false));

        assertThatThrownBy(() -> servicio.renovar(new MovimientoRegistroRequest(PERSONA, EQUIPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.codigo()).isEqualTo("VAL-025");
                    assertThat(error.propiedades()).containsEntry("requisito", "CONSENTIMIENTO");
                });

        verify(registros, never()).insertar(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015 · DEC-85: sin permiso o sin datos, el movimiento no lee el equipo")
    void movimientoSinPermisoOFaltanDatos() {
        given(autorizacion.alcance(EDITAR)).willThrow(Autorizacion.sinPermiso());

        esperar(() -> servicio.renovar(new MovimientoRegistroRequest(PERSONA, EQUIPO)),
                HttpStatus.FORBIDDEN, "VAL-015");
        verifyNoInteractions(registros, auditoria);

        willReturn(new AlcanceAutorizacion(false, Set.of(CLUB), false)).given(autorizacion).alcance(EDITAR);
        esperar(() -> servicio.transferir(new MovimientoRegistroRequest(null, EQUIPO)),
                HttpStatus.BAD_REQUEST, "VALIDACION");
        verifyNoInteractions(registros);
    }

    @Test
    @DisplayName("VAL-017 · DEC-90: si una carrera gana el INSERT de la renovación, responde 409 con nombres")
    void carreraDeMovimiento() {
        prepararMovimiento(false, ANTERIOR_MISMO_CLUB, true);
        given(registros.insertar(PERSONA, EQUIPO, TEMPORADA, HOY, REGISTRO_ANTERIOR, PERFIL))
                .willReturn(Optional.empty());
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(
                List.of(),
                List.of(new RegistroEnTemporada(OTRO_CLUB, "Club Águilas", OTRO_EQUIPO, "Águilas",
                        EstadoRegistro.PENDIENTE_VALIDACION)));

        assertThatThrownBy(() -> servicio.renovar(new MovimientoRegistroRequest(PERSONA, EQUIPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("VAL-017"));

        verifyNoInteractions(auditoria);
    }

    // ── Validación del registro (H-10.2, DEC-97 a DEC-102) ──────────────────────────────────────

    @Test
    @DisplayName("RF-109 · DEC-98: activa con el motivo fijo, guarda quién y cuándo, y audita sin datos personales")
    void activaSinObservaciones() {
        prepararValidacion(pendiente(), false, todosAprobados(false));

        assertThat(servicio.validar(REGISTRO, activar(null))).isEqualTo(VALIDADO);

        InOrder orden = inOrder(autorizacion, documentos, motivo, registros, auditoria);
        orden.verify(autorizacion).alcance(VALIDAR);
        orden.verify(documentos).buscarRegistro(LIGA, REGISTRO);
        orden.verify(autorizacion).exigir(VALIDAR, ObjetivoAutorizacion.deClubes(CLUB));
        orden.verify(documentos).bloquearRegistro(LIGA, REGISTRO);
        orden.verify(motivo).fijar("Registro validado por la liga");
        orden.verify(registros).validar(REGISTRO, EstadoRegistro.ACTIVO, PERFIL,
                OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC), null);
        orden.verify(auditoria).registrar(any());
        orden.verify(registros).buscar(REGISTRO);

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-04");
        assertThat(evento.accion()).isEqualTo("VALIDAR_REGISTRO_JUGADOR");
        assertThat(evento.entidad()).isEqualTo("registros_jugador");
        assertThat(evento.entidadId()).isEqualTo(REGISTRO.toString());
        assertThat(evento.motivo()).isNull();
        assertThat(evento.detalle()).containsExactly(
                Map.entry("personaId", PERSONA.toString()),
                Map.entry("equipoId", EQUIPO.toString()),
                Map.entry("temporadaId", TEMPORADA.toString()),
                Map.entry("estadoAnterior", "PENDIENTE_VALIDACION"),
                Map.entry("estado", "ACTIVO"));
        assertThat(evento.detalle().toString()).doesNotContain("Ana", "López", "curp");
    }

    @Test
    @DisplayName("DEC-98: al activar con observaciones, recortadas, son el motivo y se guardan")
    void activaConObservaciones() {
        prepararValidacion(pendiente(), false, todosAprobados(false));

        servicio.validar(REGISTRO, activar("  Documentos revisados en ventanilla  "));

        verify(motivo).fijar("Documentos revisados en ventanilla");
        verify(registros).validar(REGISTRO, EstadoRegistro.ACTIVO, PERFIL, OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC),
                "Documentos revisados en ventanilla");
        assertThat(eventoAuditado().motivo()).isEqualTo("Documentos revisados en ventanilla");
    }

    @Test
    @DisplayName("RF-109 · DEC-99: se rechaza con documentos pendientes y con el equipo de baja; las observaciones son el motivo")
    void rechazaConDocumentosPendientes() {
        prepararValidacion(registro(EstadoRegistro.PENDIENTE_VALIDACION, EstadoTemporada.ACTIVA, false, null), false,
                List.of(documento(FOTOGRAFIA, EstadoValidacion.PENDIENTE, null)));

        servicio.validar(REGISTRO, rechazar(OBSERVACIONES));

        verify(motivo).fijar(OBSERVACIONES);
        verify(registros).validar(REGISTRO, EstadoRegistro.RECHAZADO, PERFIL, OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC),
                OBSERVACIONES);
        verify(documentos, never()).vigentesConRenovaciones(any());
        verify(documentos, never()).requisitos(any(), any());
        verify(registros, never()).requisitosMenor(any());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.motivo()).isEqualTo(OBSERVACIONES);
        assertThat(evento.detalle()).containsEntry("estado", "RECHAZADO");
    }

    @Test
    @DisplayName("VAL-015: sin registros.registro:VALIDAR en ningún ámbito responde 403 sin leer el registro")
    void validarSinPermiso() {
        given(autorizacion.alcance(VALIDAR)).willThrow(Autorizacion.sinPermiso());

        esperar(() -> servicio.validar(REGISTRO, activar(null)), HttpStatus.FORBIDDEN, "VAL-015");

        verifyNoInteractions(documentos, registros, auditoria, motivo);
    }

    @Test
    @DisplayName("DEC-97: sin decision responde 400 VALIDACION antes de buscar el registro")
    void validarSinDecision() {
        esperar(() -> servicio.validar(REGISTRO, null), HttpStatus.BAD_REQUEST, "VALIDACION");
        esperar(() -> servicio.validar(REGISTRO, new ValidarRegistroRequest(null, OBSERVACIONES)),
                HttpStatus.BAD_REQUEST, "VALIDACION");

        verifyNoInteractions(documentos, registros);
    }

    @Test
    @DisplayName("DEC-97: observaciones de más de 500 caracteres responden 400 VALIDACION antes que RF-109")
    void observacionesLargas() {
        esperar(() -> servicio.validar(REGISTRO, activar("x".repeat(501))), HttpStatus.BAD_REQUEST, "VALIDACION");
        esperar(() -> servicio.validar(REGISTRO, rechazar("x".repeat(501))), HttpStatus.BAD_REQUEST, "VALIDACION");

        verifyNoInteractions(documentos, registros);
    }

    @Test
    @DisplayName("RF-109 · DEC-98: rechazar sin observaciones de al menos 10 caracteres responde 400 antes del 404")
    void rechazoSinObservaciones() {
        esperar(() -> servicio.validar(REGISTRO, rechazar(null)), HttpStatus.BAD_REQUEST, "RF-109");
        esperar(() -> servicio.validar(REGISTRO, rechazar("   ")), HttpStatus.BAD_REQUEST, "RF-109");
        esperar(() -> servicio.validar(REGISTRO, rechazar("  123456789  ")), HttpStatus.BAD_REQUEST, "RF-109");

        verifyNoInteractions(documentos, registros);
    }

    @Test
    @DisplayName("VAL-024: un registro inexistente responde 404 sin exigir el permiso sobre un club")
    void validarRegistroInexistente() {
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.validar(REGISTRO, activar(null)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(autorizacion, never()).exigir(any(), any());
        verify(documentos, never()).bloquearRegistro(any(), any());
    }

    @Test
    @DisplayName("VAL-024: un registro de un club fuera de ámbito responde 404 igual y no se bloquea")
    void validarFueraDeAmbito() {
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.of(pendiente()));
        willThrow(Autorizacion.fueraDeAmbito(VALIDAR))
                .given(autorizacion).exigir(VALIDAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.validar(REGISTRO, activar(null)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(documentos, never()).bloquearRegistro(any(), any());
        verifyNoInteractions(motivo, auditoria);
    }

    @Test
    @DisplayName("VAL-022 [PROPUESTA] · DEC-97: quien dio de alta el registro no lo valida, después del bloqueo y antes de RF-003")
    void quienDioDeAltaNoValida() {
        RegistroDeDocumentos propio = registro(EstadoRegistro.PENDIENTE_VALIDACION, EstadoTemporada.CERRADA, true, PERFIL);
        prepararValidacion(propio, false, todosAprobados(false));

        esperar(() -> servicio.validar(REGISTRO, activar(null)), HttpStatus.FORBIDDEN, "VAL-022");

        verify(documentos).bloquearRegistro(LIGA, REGISTRO);
        verifyNoInteractions(motivo, auditoria);
    }

    @Test
    @DisplayName("VAL-022 · DEC-97: un registro sin creado_por no activa la separación de funciones")
    void sinCreadoPor() {
        prepararValidacion(registro(EstadoRegistro.PENDIENTE_VALIDACION, EstadoTemporada.ACTIVA, true, null), false,
                todosAprobados(false));

        assertThat(servicio.validar(REGISTRO, activar(null))).isEqualTo(VALIDADO);
    }

    @Test
    @DisplayName("RF-003 · DEC-100: la temporada CERRADA responde 409 al activar y al rechazar")
    void validarTemporadaCerrada() {
        prepararValidacion(registro(EstadoRegistro.ACTIVO, EstadoTemporada.CERRADA, true, OTRO_PERFIL), false,
                todosAprobados(false));

        esperar(() -> servicio.validar(REGISTRO, activar(null)), HttpStatus.CONFLICT, "RF-003");
        esperar(() -> servicio.validar(REGISTRO, rechazar(OBSERVACIONES)), HttpStatus.CONFLICT, "RF-003");

        verifyNoInteractions(motivo);
    }

    @Test
    @DisplayName("RF-017 · DEC-100: un registro ya ACTIVO o RECHAZADO responde 409 (acción repetida)")
    void registroYaValidado() {
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.of(pendiente()));
        given(documentos.bloquearRegistro(LIGA, REGISTRO)).willReturn(
                Optional.of(registro(EstadoRegistro.ACTIVO, EstadoTemporada.ACTIVA, true, OTRO_PERFIL)),
                Optional.of(registro(EstadoRegistro.RECHAZADO, EstadoTemporada.ACTIVA, true, OTRO_PERFIL)));

        assertThatThrownBy(() -> servicio.validar(REGISTRO, activar(null))).isInstanceOfSatisfying(
                ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("RF-017");
                    assertThat(error.getMessage()).contains("activo");
                });
        esperar(() -> servicio.validar(REGISTRO, rechazar(OBSERVACIONES)), HttpStatus.CONFLICT, "RF-017");

        verifyNoInteractions(motivo, auditoria);
    }

    @Test
    @DisplayName("RF-013 · DEC-100: con el equipo en BAJA no se activa (422) y no se revisan documentos")
    void activarConEquipoDeBaja() {
        prepararValidacion(registro(EstadoRegistro.PENDIENTE_VALIDACION, EstadoTemporada.ACTIVA, false, OTRO_PERFIL),
                false, todosAprobados(false));

        esperar(() -> servicio.validar(REGISTRO, activar(null)), HttpStatus.UNPROCESSABLE_CONTENT, "RF-013");

        verify(documentos, never()).vigentesConRenovaciones(any());
        verifyNoInteractions(motivo);
    }

    @Test
    @DisplayName("VAL-001 · DEC-99: 422 con faltantes en orden: uno sin cargar y otro cargado pero pendiente")
    void faltanDocumentos() {
        prepararValidacion(pendiente(), false, List.of(
                documento(FOTOGRAFIA, EstadoValidacion.PENDIENTE, null),
                documento(FOTOGRAFIA, EstadoValidacion.APROBADO, null)));

        assertThatThrownBy(() -> servicio.validar(REGISTRO, activar(null))).isInstanceOfSatisfying(
                ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.codigo()).isEqualTo("VAL-001");
                    assertThat(error.getMessage()).contains("«Identificación»", "«Fotografía»")
                            .doesNotContain("IDENTIFICACION", "FOTOGRAFIA");
                    assertThat(error.propiedades()).containsOnlyKeys("faltantes");
                    assertThat(error.propiedades().get("faltantes")).asInstanceOf(InstanceOfAssertFactories.LIST)
                            .containsExactly(
                                    new RegistroJugadorService.DocumentoFaltante(IDENTIFICACION, 1, 0, 0),
                                    new RegistroJugadorService.DocumentoFaltante(FOTOGRAFIA, 2, 2, 1));
                });

        verify(registros, never()).requisitosMenor(any());
        verify(registros, never()).validar(any(), any(), any(), any(), any());
        verifyNoInteractions(motivo, auditoria);
    }

    @Test
    @DisplayName("VAL-001 · DEC-99: un documento aprobado pero reemplazado no cuenta")
    void aprobadoReemplazadoNoCuenta() {
        List<DocumentoRegistro> vigentes = new ArrayList<>(todosAprobados(false));
        vigentes.set(0, documento(IDENTIFICACION, EstadoValidacion.APROBADO, UUID.randomUUID()));
        prepararValidacion(pendiente(), false, vigentes);

        esperar(() -> servicio.validar(REGISTRO, activar(null)), HttpStatus.UNPROCESSABLE_CONTENT, "VAL-001");
    }

    @Test
    @DisplayName("VAL-001 · DEC-99: a un menor le falta el INE del tutor aprobado → VAL-001 antes que VAL-025")
    void menorSinIneDelTutor() {
        prepararValidacion(pendiente(), true, todosAprobados(false));

        assertThatThrownBy(() -> servicio.validar(REGISTRO, activar(null))).isInstanceOfSatisfying(
                ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-001");
                    assertThat(error.propiedades().get("faltantes")).asInstanceOf(InstanceOfAssertFactories.LIST)
                            .containsExactly(new RegistroJugadorService.DocumentoFaltante(INE_TUTOR, 1, 0, 0));
                });

        verify(registros, never()).requisitosMenor(any());
    }

    @Test
    @DisplayName("VAL-025 · DEC-99: un menor con documentos aprobados y sin tutela vigente responde 422 TUTOR")
    void activarMenorSinTutor() {
        prepararValidacion(pendiente(), true, todosAprobados(true));
        given(registros.requisitosMenor(PERSONA)).willReturn(new RequisitosMenor(false, false));

        assertThatThrownBy(() -> servicio.validar(REGISTRO, activar(null))).isInstanceOfSatisfying(
                ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(error.codigo()).isEqualTo("VAL-025");
                    assertThat(error.propiedades()).containsEntry("requisito", "TUTOR");
                });

        verify(documentos).esMenor(PERSONA, HOY);
        verifyNoInteractions(motivo);
    }

    @Test
    @DisplayName("VAL-025 · DEC-99: un menor con tutor pero sin consentimiento responde 422 CONSENTIMIENTO")
    void activarMenorSinConsentimiento() {
        prepararValidacion(pendiente(), true, todosAprobados(true));
        given(registros.requisitosMenor(PERSONA)).willReturn(new RequisitosMenor(true, false));

        assertThatThrownBy(() -> servicio.validar(REGISTRO, activar(null))).isInstanceOfSatisfying(
                ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VAL-025");
                    assertThat(error.propiedades()).containsEntry("requisito", "CONSENTIMIENTO");
                });
    }

    @Test
    @DisplayName("VAL-025: un menor con documentos, tutor y consentimiento se activa")
    void activarMenorCompleto() {
        prepararValidacion(pendiente(), true, todosAprobados(true));
        given(registros.requisitosMenor(PERSONA)).willReturn(new RequisitosMenor(true, true));

        assertThat(servicio.validar(REGISTRO, activar(null))).isEqualTo(VALIDADO);
    }

    @Test
    @DisplayName("RF-017 · DEC-102: si el UPDATE condicional no devuelve fila responde 409 y no audita")
    void otraPeticionValidoAntes() {
        prepararValidacion(pendiente(), false, todosAprobados(false));
        given(registros.validar(any(), any(), any(), any(), any())).willReturn(false);

        esperar(() -> servicio.validar(REGISTRO, activar(null)), HttpStatus.CONFLICT, "RF-017");

        verifyNoInteractions(auditoria);
        verify(registros, never()).buscar(any());
    }

    // ── Baja del registro (H-23.1, DEC-118 a DEC-124) ───────────────────────────────────────────

    @Test
    @DisplayName("RF-014 · RN-021: da de baja un registro ACTIVO con el motivo antes del UPDATE, la fecha de México y auditoría")
    void daDeBaja() {
        // 15 nov 2026 a las 00:30 UTC: en Morelia todavía es 14 de noviembre (DEC-121).
        servicio = conReloj(Instant.parse("2026-11-15T00:30:00Z"));
        prepararBaja(registro(EstadoRegistro.ACTIVO, EstadoTemporada.ACTIVA, true, PERFIL));

        assertThat(servicio.darDeBaja(REGISTRO, new BajaRegistroRequest("  " + MOTIVO_BAJA + "  "))).isEqualTo(DE_BAJA);

        LocalDate catorce = LocalDate.parse("2026-11-14");
        InOrder orden = inOrder(autorizacion, documentos, motivo, registros, auditoria);
        orden.verify(autorizacion).alcance(EDITAR);
        orden.verify(documentos).buscarRegistro(LIGA, REGISTRO);
        orden.verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));
        orden.verify(documentos).bloquearRegistro(LIGA, REGISTRO);
        orden.verify(motivo).fijar(MOTIVO_BAJA);
        orden.verify(registros).darDeBaja(REGISTRO, catorce);
        orden.verify(auditoria).registrar(any());
        orden.verify(registros).buscar(REGISTRO);
        verify(registros, never()).validar(any(), any(), any(), any(), any());

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.ligaId()).isEqualTo(LIGA);
        assertThat(evento.modulo()).isEqualTo("M-04");
        assertThat(evento.accion()).isEqualTo("DAR_BAJA_REGISTRO_JUGADOR");
        assertThat(evento.entidad()).isEqualTo("registros_jugador");
        assertThat(evento.entidadId()).isEqualTo(REGISTRO.toString());
        assertThat(evento.motivo()).isEqualTo(MOTIVO_BAJA);
        assertThat(evento.detalle()).containsExactly(
                Map.entry("personaId", PERSONA.toString()),
                Map.entry("equipoId", EQUIPO.toString()),
                Map.entry("temporadaId", TEMPORADA.toString()),
                Map.entry("estadoAnterior", "ACTIVO"),
                Map.entry("estado", "BAJA"),
                Map.entry("bajaEn", "2026-11-14"));
        assertThat(evento.detalle().toString()).doesNotContain("Ana", "López", "curp");
    }

    @Test
    @DisplayName("DEC-120: con el equipo en BAJA, un registro ACTIVO sí se da de baja")
    void daDeBajaConEquipoDeBaja() {
        prepararBaja(registro(EstadoRegistro.ACTIVO, EstadoTemporada.ACTIVA, false, OTRO_PERFIL));

        assertThat(servicio.darDeBaja(REGISTRO, new BajaRegistroRequest(MOTIVO_BAJA))).isEqualTo(DE_BAJA);

        verify(registros).darDeBaja(REGISTRO, HOY);
    }

    @Test
    @DisplayName("DEC-120: se acepta la temporada en CONFIGURACION")
    void daDeBajaEnConfiguracion() {
        prepararBaja(registro(EstadoRegistro.ACTIVO, EstadoTemporada.CONFIGURACION, true, OTRO_PERFIL));

        assertThat(servicio.darDeBaja(REGISTRO, new BajaRegistroRequest(MOTIVO_BAJA))).isEqualTo(DE_BAJA);
    }

    @Test
    @DisplayName("VAL-015 · DEC-119: sin registros.registro:EDITAR en ningún ámbito responde 403 sin leer el registro")
    void bajaSinPermiso() {
        given(autorizacion.alcance(EDITAR)).willThrow(Autorizacion.sinPermiso());

        esperar(() -> servicio.darDeBaja(REGISTRO, new BajaRegistroRequest(MOTIVO_BAJA)), HttpStatus.FORBIDDEN,
                "VAL-015");

        verifyNoInteractions(documentos, registros, auditoria, motivo);
    }

    @Test
    @DisplayName("RN-021 · DEC-121: sin motivo, en blanco o de 9 caracteres responde 400 RN-021 antes de buscar el registro")
    void bajaSinMotivo() {
        esperar(() -> servicio.darDeBaja(REGISTRO, null), HttpStatus.BAD_REQUEST, "RN-021");
        esperar(() -> servicio.darDeBaja(REGISTRO, new BajaRegistroRequest(null)), HttpStatus.BAD_REQUEST, "RN-021");
        esperar(() -> servicio.darDeBaja(REGISTRO, new BajaRegistroRequest("   ")), HttpStatus.BAD_REQUEST, "RN-021");
        assertThatThrownBy(() -> servicio.darDeBaja(REGISTRO, new BajaRegistroRequest("  123456789  ")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.codigo()).isEqualTo("RN-021");
                    assertThat(error.getMessage())
                            .isEqualTo("Escribe un motivo de al menos 10 caracteres para dar de baja el registro.");
                });

        verifyNoInteractions(documentos, registros, auditoria, motivo);
    }

    @Test
    @DisplayName("DEC-121: un motivo de 501 caracteres responde 400 VALIDACION; de 500, recortado, se acepta")
    void bajaMotivoLargo() {
        esperar(() -> servicio.darDeBaja(REGISTRO, new BajaRegistroRequest("x".repeat(501))), HttpStatus.BAD_REQUEST,
                "VALIDACION");
        verifyNoInteractions(documentos, registros);

        prepararBaja(registro(EstadoRegistro.ACTIVO, EstadoTemporada.ACTIVA, true, OTRO_PERFIL));
        servicio.darDeBaja(REGISTRO, new BajaRegistroRequest(" " + "x".repeat(500) + " "));
        verify(motivo).fijar("x".repeat(500));
    }

    @Test
    @DisplayName("DEC-121 · DEC-122: el 400 del motivo gana al 404 de un registro inexistente")
    void motivoAntesQueNoEncontrado() {
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.empty());

        esperar(() -> servicio.darDeBaja(REGISTRO, new BajaRegistroRequest("corto")), HttpStatus.BAD_REQUEST, "RN-021");

        verifyNoInteractions(documentos);
    }

    @Test
    @DisplayName("VAL-024 · DEC-119: un registro inexistente responde 404 sin exigir el permiso sobre un club")
    void bajaRegistroInexistente() {
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.darDeBaja(REGISTRO, new BajaRegistroRequest(MOTIVO_BAJA)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(autorizacion, never()).exigir(any(), any());
        verify(documentos, never()).bloquearRegistro(any(), any());
        verifyNoInteractions(motivo, auditoria);
    }

    @Test
    @DisplayName("VAL-024 · DEC-119: un registro de un club fuera de ámbito responde 404 igual y no se bloquea")
    void bajaFueraDeAmbito() {
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(
                Optional.of(registro(EstadoRegistro.ACTIVO, EstadoTemporada.ACTIVA, true, OTRO_PERFIL)));
        willThrow(Autorizacion.fueraDeAmbito(EDITAR))
                .given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.darDeBaja(REGISTRO, new BajaRegistroRequest(MOTIVO_BAJA)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(documentos, never()).bloquearRegistro(any(), any());
        verifyNoInteractions(motivo, auditoria);
    }

    @Test
    @DisplayName("RF-003 · DEC-120: la temporada CERRADA responde 409 antes de revisar el estado")
    void bajaTemporadaCerrada() {
        prepararBaja(registro(EstadoRegistro.PENDIENTE_VALIDACION, EstadoTemporada.CERRADA, true, OTRO_PERFIL));

        assertThatThrownBy(() -> servicio.darDeBaja(REGISTRO, new BajaRegistroRequest(MOTIVO_BAJA)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("RF-003");
                    assertThat(error.getMessage())
                            .isEqualTo("La temporada del registro está cerrada: sus registros ya no cambian.");
                });

        verify(documentos).bloquearRegistro(LIGA, REGISTRO);
        verifyNoInteractions(motivo, auditoria);
        verify(registros, never()).darDeBaja(any(), any());
    }

    @Test
    @DisplayName("RF-017 · DEC-118: un registro PENDIENTE_VALIDACION responde 409 y remite al rechazo de la liga")
    void bajaDePendiente() {
        esperarSinBaja(EstadoRegistro.PENDIENTE_VALIDACION,
                "El registro aún no está validado: si no procede, pide a la liga que lo rechace.");
    }

    @Test
    @DisplayName("RF-017 · DEC-118: un registro SUSPENDIDO responde 409 (la baja por sanción es de M-09)")
    void bajaDeSuspendido() {
        esperarSinBaja(EstadoRegistro.SUSPENDIDO,
                "El registro tiene una sanción vigente: no se puede dar de baja mientras esté suspendido.");
    }

    @Test
    @DisplayName("RF-017 · DEC-118: un registro ya en BAJA responde 409 (acción repetida)")
    void bajaDeBaja() {
        esperarSinBaja(EstadoRegistro.BAJA, "El registro ya está dado de baja.");
    }

    @Test
    @DisplayName("RF-017 · DEC-118: un registro RECHAZADO responde 409")
    void bajaDeRechazado() {
        esperarSinBaja(EstadoRegistro.RECHAZADO, "El registro fue rechazado: no está activo.");
    }

    @Test
    @DisplayName("RF-017 · DEC-122: si el UPDATE condicional no devuelve fila responde 409 y no audita")
    void otraPeticionCambioElEstado() {
        prepararBaja(registro(EstadoRegistro.ACTIVO, EstadoTemporada.ACTIVA, true, OTRO_PERFIL));
        given(registros.darDeBaja(any(), any())).willReturn(false);

        assertThatThrownBy(() -> servicio.darDeBaja(REGISTRO, new BajaRegistroRequest(MOTIVO_BAJA)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("RF-017");
                    assertThat(error.getMessage()).isEqualTo("Otra persona cambió el estado de este registro al "
                            + "mismo tiempo. Actualiza la página para ver su estado.");
                });

        verifyNoInteractions(auditoria);
        verify(registros, never()).buscar(any());
    }

    // ── Apoyo ──────────────────────────────────────────────────────────────────────────────────

    /** Registro encontrado y, con el bloqueo, {@code bloqueado}; un {@code UPDATE} que sí actualiza. */
    private void prepararBaja(RegistroDeDocumentos bloqueado) {
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.of(bloqueado));
        given(documentos.bloquearRegistro(LIGA, REGISTRO)).willReturn(Optional.of(bloqueado));
        given(registros.darDeBaja(any(), any())).willReturn(true);
        given(registros.buscar(REGISTRO)).willReturn(Optional.of(DE_BAJA));
    }

    /** {@code 409 RF-017} con el {@code detail} del estado, después del bloqueo y sin tocar nada (DEC-118). */
    private void esperarSinBaja(EstadoRegistro estado, String mensaje) {
        prepararBaja(registro(estado, EstadoTemporada.ACTIVA, true, OTRO_PERFIL));

        assertThatThrownBy(() -> servicio.darDeBaja(REGISTRO, new BajaRegistroRequest(MOTIVO_BAJA)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo("RF-017");
                    assertThat(error.getMessage()).isEqualTo(mensaje);
                });

        verify(documentos).bloquearRegistro(LIGA, REGISTRO);
        verify(registros, never()).darDeBaja(any(), any());
        verifyNoInteractions(motivo, auditoria);
    }

    private RegistroJugadorService conReloj(Instant instante) {
        return new RegistroJugadorService(registros, documentos, autorizacion, auditoria, motivo,
                Clock.fixed(instante, ZoneOffset.UTC), new SiglimPropiedades(null, null, null, LIGA));
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

    /** Equipo destino, persona existente sin registros en la temporada y alta exitosa del movimiento. */
    private void prepararMovimiento(boolean menor, RegistroAnterior anterior, boolean permiteCambioClub) {
        given(registros.buscarEquipo(LIGA, EQUIPO)).willReturn(Optional.of(EQUIPO_ACTIVO));
        given(registros.clubAfiliado(CLUB, TEMPORADA)).willReturn(true);
        given(registros.esMenor(PERSONA, HOY)).willReturn(Optional.of(menor));
        given(registros.registrosEnTemporada(PERSONA, TEMPORADA)).willReturn(List.of());
        given(registros.registroAnterior(PERSONA, TEMPORADA)).willReturn(Optional.ofNullable(anterior));
        given(registros.permiteCambioClub(TEMPORADA)).willReturn(permiteCambioClub);
        given(registros.insertar(PERSONA, EQUIPO, TEMPORADA, HOY, anterior == null ? null : anterior.id(), PERFIL))
                .willReturn(Optional.of(REGISTRO));
        given(registros.buscar(REGISTRO)).willReturn(Optional.of(CREADO));
    }

    /**
     * Registro {@code buscarRegistro} pendiente y, con el bloqueo, {@code bloqueado}; documentos de la
     * categoría LIBRE, la persona menor o no en la fecha de alta, y un {@code UPDATE} que sí actualiza.
     */
    private void prepararValidacion(RegistroDeDocumentos bloqueado, boolean menor, List<DocumentoRegistro> vigentes) {
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.of(pendiente()));
        given(documentos.bloquearRegistro(LIGA, REGISTRO)).willReturn(Optional.of(bloqueado));
        given(documentos.esMenor(PERSONA, HOY)).willReturn(Optional.of(menor));
        given(documentos.requisitos(LIGA, TipoCategoria.LIBRE)).willReturn(REQUISITOS_LIBRE);
        given(documentos.vigentesConRenovaciones(REGISTRO)).willReturn(vigentes);
        given(registros.validar(any(), any(), any(), any(), any())).willReturn(true);
        given(registros.buscar(REGISTRO)).willReturn(Optional.of(VALIDADO));
    }

    private static RegistroDeDocumentos pendiente() {
        return registro(EstadoRegistro.PENDIENTE_VALIDACION, EstadoTemporada.ACTIVA, true, OTRO_PERFIL);
    }

    private static RegistroDeDocumentos registro(EstadoRegistro estado, EstadoTemporada temporada, boolean equipoActivo,
            UUID creadoPor) {
        return new RegistroDeDocumentos(REGISTRO, PERSONA, CLUB, HOY, estado, temporada, CATEGORIA, "Segunda",
                TipoCategoria.LIBRE, EQUIPO, equipoActivo, TEMPORADA, creadoPor);
    }

    /** Identificación y dos fotografías aprobadas y, si se pide, el INE del tutor aprobado. */
    private static List<DocumentoRegistro> todosAprobados(boolean conIneDelTutor) {
        List<DocumentoRegistro> aprobados = new ArrayList<>(List.of(
                documento(IDENTIFICACION, EstadoValidacion.APROBADO, null),
                documento(FOTOGRAFIA, EstadoValidacion.APROBADO, null),
                documento(FOTOGRAFIA, EstadoValidacion.APROBADO, null)));
        if (conIneDelTutor) {
            aprobados.add(documento(INE_TUTOR, EstadoValidacion.APROBADO, null));
        }
        return aprobados;
    }

    private static DocumentoRegistro documento(TipoDocumentoDto tipo, EstadoValidacion estado, UUID reemplazadoPor) {
        DocumentoDto dto = new DocumentoDto(UUID.randomUUID(), REGISTRO, tipo, "image/jpeg", 1024, estado, null, null,
                null, null, OffsetDateTime.parse("2026-11-11T12:00:00-06:00"));
        return new DocumentoRegistro(dto, OTRO_PERFIL, reemplazadoPor, "siglim-documentos-prueba", "clave");
    }

    private static ValidarRegistroRequest activar(String observaciones) {
        return new ValidarRegistroRequest(DecisionRegistro.ACTIVO, observaciones);
    }

    private static ValidarRegistroRequest rechazar(String observaciones) {
        return new ValidarRegistroRequest(DecisionRegistro.RECHAZADO, observaciones);
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
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
