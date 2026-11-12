package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
            null, null, null);
    private static final RegistroJugadorDto VALIDADO = new RegistroJugadorDto(REGISTRO,
            new PersonaResumenDto(PERSONA, "Ana López"),
            new ReferenciaDto(EQUIPO, "Halcones"),
            new ReferenciaDto(CLUB, "Club Halcones"),
            new ReferenciaDto(CATEGORIA, "Segunda"),
            new TemporadaResumenDto(TEMPORADA, "Temporada 2026", EstadoTemporada.ACTIVA),
            EstadoRegistro.ACTIVO, HOY, OffsetDateTime.parse("2026-11-11T12:00:00-06:00"),
            new ReferenciaDto(PERFIL, "Secretario"), OffsetDateTime.parse("2026-11-11T12:00:00-06:00"), null);

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
        verify(documentos, never()).vigentes(any());
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

        verify(documentos, never()).vigentes(any());
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

    // ── Apoyo ──────────────────────────────────────────────────────────────────────────────────

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

    /**
     * Registro {@code buscarRegistro} pendiente y, con el bloqueo, {@code bloqueado}; documentos de la
     * categoría LIBRE, la persona menor o no en la fecha de alta, y un {@code UPDATE} que sí actualiza.
     */
    private void prepararValidacion(RegistroDeDocumentos bloqueado, boolean menor, List<DocumentoRegistro> vigentes) {
        given(documentos.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.of(pendiente()));
        given(documentos.bloquearRegistro(LIGA, REGISTRO)).willReturn(Optional.of(bloqueado));
        given(documentos.esMenor(PERSONA, HOY)).willReturn(Optional.of(menor));
        given(documentos.requisitos(LIGA, TipoCategoria.LIBRE)).willReturn(REQUISITOS_LIBRE);
        given(documentos.vigentes(REGISTRO)).willReturn(vigentes);
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
