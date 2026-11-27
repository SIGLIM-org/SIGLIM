package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaRepository.CanchaDeAsignacion;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaRepository.EmparejamientoPendiente;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaRepository.GrupoDeLaFecha;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaRepository.ParametrosAsignacion;
import mx.tecnm.morelia.siglim.programacion.AsignacionFechaRepository.PartidoDeLaFecha;
import mx.tecnm.morelia.siglim.programacion.FechaRepository.DatosFecha;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.CanchaDeLiga;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosJornada;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.EquipoDeLiga;
import mx.tecnm.morelia.siglim.programacion.ResultadoAsignacion.PartidoAsignado;
import mx.tecnm.morelia.siglim.programacion.SolicitudAsignacion.Ocupacion;
import mx.tecnm.morelia.siglim.programacion.ValidadorConflictos.Conflicto;
import mx.tecnm.morelia.siglim.programacion.ValidadorLocalia.Advertencia;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asignación automática de la fecha (H-33.2) con dobles de los repositorios y los validadores: el
 * orden de errores, los dos modos, la confirmación con {@link ValidadorConflictos}, la localía, la
 * trazabilidad y la auditoría. El motor (H-33.1) corre de verdad.
 *
 * <p>Refs: H-33.2, RF-041, RF-044, RN-021, VAL-006, VAL-027, DEC-181, DEC-206, DEC-207, DEC-208
 */
class AsignacionFechaServiceTest {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID PERFIL = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final UUID FECHA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID TEMPORADA = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID JORNADA = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID GRUPO = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID CANCHA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID EMPAREJAMIENTO = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID PARTIDO_NUEVO = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private static final LocalDate SABADO = LocalDate.parse("2031-03-08");
    private static final LocalDate DOMINGO = SABADO.plusDays(1);
    private static final String MOTIVO = "Se cerró la cancha 2 por lluvia";
    private static final EquipoDeLiga HALCONES = new EquipoDeLiga(UUID.randomUUID(), "Halcones", GRUPO,
            UUID.randomUUID(), "ACTIVO");
    private static final EquipoDeLiga TOROS = new EquipoDeLiga(UUID.randomUUID(), "Toros", GRUPO,
            UUID.randomUUID(), "ACTIVO");

    private final AsignacionFechaRepository asignacion = mock(AsignacionFechaRepository.class);
    private final FechaRepository fechas = mock(FechaRepository.class);
    private final JornadaRepository jornadas = mock(JornadaRepository.class);
    private final ConflictoRepository conflictos = mock(ConflictoRepository.class);
    private final LocaliaRepository localiaRepository = mock(LocaliaRepository.class);
    private final ValidadorConflictos validador = mock(ValidadorConflictos.class);
    private final ValidadorLocalia localia = mock(ValidadorLocalia.class);
    private final FechaService fechaService = mock(FechaService.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final AsignacionFechaService servicio = new AsignacionFechaService(asignacion, fechas, jornadas,
            conflictos, localiaRepository, validador, localia, fechaService, autorizacion, auditoria,
            motivoOperacion, JsonMapper.builder().build(), new SiglimPropiedades(null, null, null, LIGA));

    @BeforeEach
    void iniciarSesion() {
        Instant ahora = Instant.parse("2031-03-01T12:00:00Z");
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(ahora).expiresAt(ahora.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Escenario ───────────────────────────────────────────────────────────────────────────────

    private static OffsetDateTime a(LocalDate dia, int hora) {
        return dia.atTime(hora, 0).atZone(ZONA).toOffsetDateTime();
    }

    private static DatosJornada jornada(EstadoJornada estado) {
        OffsetDateTime creada = a(SABADO.minusDays(7), 8);
        return new DatosJornada(JORNADA, GRUPO, null, 3, estado, null, null, null, null, null, null, null,
                creada, creada);
    }

    private static EmparejamientoPendiente pendiente(EquipoDeLiga local, EquipoDeLiga visitante) {
        return new EmparejamientoPendiente(EMPAREJAMIENTO, GRUPO, UUID.randomUUID(), "Libre", 1,
                Set.of(DayOfWeek.SUNDAY), 120, local, visitante);
    }

    /** Fecha 3 en borrador, con parámetros, un grupo con su jornada, una cancha y un pendiente. */
    private void escenario(EstadoJornada estado, EmparejamientoPendiente pendiente) {
        given(fechas.bloquear(LIGA, FECHA)).willReturn(true);
        given(jornadas.bloquearJornadasDeFecha(FECHA)).willReturn(List.of(jornada(estado)));
        given(fechas.buscar(LIGA, FECHA)).willReturn(Optional.of(new DatosFecha(FECHA, TEMPORADA, 3,
                List.of(SABADO, DOMINGO), null, null, null, 1, 1, 0, 0, 1, 0)));
        given(asignacion.parametrosVigentes(TEMPORADA)).willReturn(Optional.of(
                new ParametrosAsignacion(2, LocalTime.of(8, 0), LocalTime.of(18, 0), null, null)));
        given(asignacion.gruposDeLaFecha(TEMPORADA, FECHA, 3))
                .willReturn(List.of(new GrupoDeLaFecha(GRUPO, "Grupo 1", JORNADA, false)));
        given(asignacion.pendientes(TEMPORADA, 3)).willReturn(List.of(pendiente));
        given(asignacion.canchas(LIGA)).willReturn(List.of(new CanchaDeAsignacion(CANCHA, "Cancha 1", true, true)));
        given(jornadas.bloquearCancha(LIGA, CANCHA)).willReturn(Optional.of(new CanchaDeLiga(CANCHA, "Cancha 1", true)));
        given(jornadas.insertarPartido(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .willReturn(PARTIDO_NUEVO);
    }

    private void escenario() {
        escenario(EstadoJornada.BORRADOR, pendiente(HALCONES, TOROS));
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private void rechaza(AsignarFechaRequest peticion, HttpStatus estado, String codigo) {
        assertThatThrownBy(() -> servicio.asignar(FECHA, peticion))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(estado, codigo));
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(jornadas, never()).insertarJornadaDeFecha(any(), any(), any(Integer.class), any());
        verify(asignacion, never()).cancelarPartido(any());
        verifyNoInteractions(auditoria);
    }

    private static AsignarFechaRequest pendientes() {
        return new AsignarFechaRequest(ModoAsignacion.PENDIENTES, null);
    }

    private static AsignarFechaRequest regenerar(String motivo) {
        return new AsignarFechaRequest(ModoAsignacion.REGENERAR, motivo);
    }

    // ── Orden de errores (DEC-206) ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VAL-015: sin programacion.jornada:EDITAR no lee ni escribe")
    void sinPermiso() {
        willThrow(ReglaNegocioException.prohibida("VAL-015", "Sin permiso"))
                .given(autorizacion).exigir(eq("programacion.jornada:EDITAR"), any());

        rechaza(pendientes(), HttpStatus.FORBIDDEN, "VAL-015");
        verifyNoInteractions(fechas, asignacion);
    }

    @Test
    @DisplayName("DEC-206: sin modo responde 400 VALIDACION antes de bloquear la fecha")
    void sinModo() {
        rechaza(new AsignarFechaRequest(null, MOTIVO), HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION);
        rechaza(null, HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION);
        verifyNoInteractions(fechas);
    }

    @Test
    @DisplayName("RN-021: regenerar sin motivo o con menos de 10 caracteres responde 400 antes de bloquear")
    void regenerarSinMotivo() {
        rechaza(regenerar(null), HttpStatus.BAD_REQUEST, "RN-021");
        rechaza(regenerar("  corto  "), HttpStatus.BAD_REQUEST, "RN-021");
        verifyNoInteractions(fechas);
    }

    @Test
    @DisplayName("DEC-206: una fecha que no es de la liga responde 404")
    void fechaInexistente() {
        given(fechas.bloquear(LIGA, FECHA)).willReturn(false);

        assertThatThrownBy(() -> servicio.asignar(FECHA, pendientes())).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(asignacion, auditoria);
    }

    @Test
    @DisplayName("RF-003: con la temporada cerrada responde 409")
    void temporadaCerrada() {
        escenario();
        given(fechas.temporadaCerrada(TEMPORADA)).willReturn(true);

        rechaza(pendientes(), HttpStatus.CONFLICT, "RF-003");
    }

    @Test
    @DisplayName("RF-090: con una jornada en revisión responde 409 en los dos modos")
    void jornadaEnRevision() {
        escenario(EstadoJornada.REVISION, pendiente(HALCONES, TOROS));

        rechaza(pendientes(), HttpStatus.CONFLICT, "RF-090");
        rechaza(regenerar(MOTIVO), HttpStatus.CONFLICT, "RF-090");
    }

    @Test
    @DisplayName("RF-006: sin parámetros vigentes responde 409, como el calendario (DEC-190)")
    void sinParametros() {
        escenario();
        given(asignacion.parametrosVigentes(TEMPORADA)).willReturn(Optional.empty());

        rechaza(pendientes(), HttpStatus.CONFLICT, "RF-006");
    }

    @Test
    @DisplayName("DEC-206: una fecha sin emparejamientos vigentes responde 422")
    void sinCalendario() {
        escenario();
        given(asignacion.gruposDeLaFecha(TEMPORADA, FECHA, 3)).willReturn(List.of());

        rechaza(pendientes(), HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("DEC-208: una jornada manual con el número de la fecha responde 409 DUPLICADO")
    void jornadaManualDuplicada() {
        escenario();
        given(asignacion.gruposDeLaFecha(TEMPORADA, FECHA, 3))
                .willReturn(List.of(new GrupoDeLaFecha(GRUPO, "Grupo 1", null, true)));

        rechaza(pendientes(), HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO);
    }

    @Test
    @DisplayName("DEC-208: regenerar con un partido EN_CURSO responde 409 RF-046 y no cancela nada")
    void regenerarConPartidoNoCancelable() {
        escenario();
        given(asignacion.partidosDeLaFecha(FECHA)).willReturn(List.of(
                new PartidoDeLaFecha(UUID.randomUUID(), EstadoPartido.PROGRAMADO, "Pumas", "Leones"),
                new PartidoDeLaFecha(UUID.randomUUID(), EstadoPartido.EN_CURSO, "Lobos", "Osos")));

        rechaza(regenerar(MOTIVO), HttpStatus.CONFLICT, "RF-046");
        verify(motivoOperacion, never()).fijar(anyString());
    }

    // ── Modos (DEC-181) ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DEC-181: PENDIENTES no toca lo ya asignado, que entra como ocupación, y coloca el pendiente después")
    void pendientesRespetaLoAsignado() {
        escenario();
        given(asignacion.ocupacion(eq(LIGA), any(), any())).willReturn(List.of(new Ocupacion(CANCHA,
                UUID.randomUUID(), UUID.randomUUID(), a(DOMINGO, 8), a(DOMINGO, 10))));

        AsignacionFechaDto resultado = servicio.asignar(FECHA, pendientes());

        assertThat(resultado.totales()).isEqualTo(new AsignacionFechaDto.Totales(1, 0, 0));
        verify(jornadas).insertarPartido(JORNADA, HALCONES.id(), TOROS.id(), CANCHA, a(DOMINGO, 10), null, PERFIL, true);
        verify(jornadas).ligarEmparejamiento(EMPAREJAMIENTO, PARTIDO_NUEVO);
        verify(asignacion, never()).partidosDeLaFecha(any());
        verify(asignacion, never()).cancelarPartido(any());
        verify(asignacion, never()).soltarEmparejamientos(any(), any(Integer.class));
        verify(motivoOperacion, never()).fijar(anyString());
    }

    @Test
    @DisplayName("DEC-181, RF-044: REGENERAR cancela con motivo, quita la vigencia de las designaciones y suelta los emparejamientos")
    void regenerarCancelaSinBorrar() {
        escenario();
        UUID uno = UUID.randomUUID();
        UUID dos = UUID.randomUUID();
        given(asignacion.partidosDeLaFecha(FECHA)).willReturn(List.of(
                new PartidoDeLaFecha(uno, EstadoPartido.PROGRAMADO, "Pumas", "Leones"),
                new PartidoDeLaFecha(dos, EstadoPartido.SUSPENDIDO, "Lobos", "Osos")));

        AsignacionFechaDto resultado = servicio.asignar(FECHA, regenerar("  " + MOTIVO + " "));

        assertThat(resultado.totales()).isEqualTo(new AsignacionFechaDto.Totales(1, 0, 2));
        verify(motivoOperacion).fijar(MOTIVO);
        verify(asignacion).cancelarPartido(uno);
        verify(asignacion).cancelarPartido(dos);
        verify(asignacion).retirarDesignaciones(List.of(uno, dos), MOTIVO);
        verify(asignacion).soltarEmparejamientos(TEMPORADA, 3);
    }

    @Test
    @DisplayName("DEC-175: crea la jornada del grupo en la fecha si todavía no existe")
    void creaLaJornadaDeLaFecha() {
        escenario();
        UUID nueva = UUID.randomUUID();
        given(asignacion.gruposDeLaFecha(TEMPORADA, FECHA, 3))
                .willReturn(List.of(new GrupoDeLaFecha(GRUPO, "Grupo 1", null, false)));
        given(jornadas.insertarJornadaDeFecha(GRUPO, FECHA, 3, PERFIL)).willReturn(nueva);

        servicio.asignar(FECHA, pendientes());

        verify(jornadas).insertarPartido(eq(nueva), any(), any(), any(), any(), any(), eq(PERFIL), eq(true));
    }

    // ── Confirmación y causas (DEC-207, DEC-180) ───────────────────────────────────────────────

    @Test
    @DisplayName("DEC-207: si ValidadorConflictos rechaza al candidato, no se inserta y queda sin asignar con su código")
    void validadorRechazaAlCandidato() {
        escenario();
        given(validador.evaluar(eq(GRUPO), eq(null), eq(HALCONES), eq(TOROS), any(), any()))
                .willReturn(List.of(new Conflicto("VAL-004", "La cancha «Cancha 1» ya está ocupada.")));

        AsignacionFechaDto resultado = servicio.asignar(FECHA, pendientes());

        assertThat(resultado.totales()).isEqualTo(new AsignacionFechaDto.Totales(0, 1, 0));
        assertThat(resultado.noAsignados()).singleElement()
                .isEqualTo(new AsignacionFechaDto.NoAsignado(EMPAREJAMIENTO, "VAL-004",
                        "La cancha «Cancha 1» ya está ocupada."));
        verify(asignacion).marcarNoAsignado(EMPAREJAMIENTO, "VAL-004", "La cancha «Cancha 1» ya está ocupada.");
        verify(jornadas, never()).insertarPartido(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("VAL-006: un equipo dado de baja deja el partido sin asignar con la causa del validador")
    void equipoDeBaja() {
        EquipoDeLiga deBaja = new EquipoDeLiga(TOROS.id(), "Toros", GRUPO, TOROS.clubId(), "BAJA");
        escenario(EstadoJornada.BORRADOR, pendiente(HALCONES, deBaja));

        AsignacionFechaDto resultado = servicio.asignar(FECHA, pendientes());

        String causa = "No se asigna «Halcones vs. Toros»: El equipo «Toros» está dado de baja y no se puede programar.";
        assertThat(resultado.noAsignados()).singleElement()
                .isEqualTo(new AsignacionFechaDto.NoAsignado(EMPAREJAMIENTO, "VAL-006", causa));
        verify(asignacion).marcarNoAsignado(EMPAREJAMIENTO, "VAL-006", causa);
    }

    @Test
    @DisplayName("VAL-027: con advertencia de localía se guarda el motivo automático; sin advertencia, ninguno")
    void motivoDeLocalia() {
        escenario();
        given(localia.evaluar(GRUPO, JORNADA, HALCONES, CANCHA)).willReturn(List.of(new Advertencia(
                ValidadorLocalia.Regla.CANCHA_DEL_LOCAL, "El equipo local «Halcones» tiene cancha.")));

        servicio.asignar(FECHA, pendientes());

        verify(jornadas).insertarPartido(JORNADA, HALCONES.id(), TOROS.id(), CANCHA, a(DOMINGO, 8),
                "Asignación automática: El equipo local «Halcones» tiene cancha.", PERFIL, true);
    }

    @Test
    @DisplayName("DEC-207: el motivo del motor manda si el validador advierte; sin advertencia no se guarda")
    void motivoDelMotorSiHayAdvertencia() {
        PartidoAsignado conMotivo = new PartidoAsignado(EMPAREJAMIENTO, CANCHA, a(DOMINGO, 8), a(DOMINGO, 10),
                "Asignación automática: no hubo lugar en la cancha del equipo local «Halcones».");
        Advertencia advertencia = new Advertencia(ValidadorLocalia.Regla.CANCHA_DEL_LOCAL, "Otra explicación.");

        assertThat(AsignacionFechaService.motivoLocalia(conMotivo, List.of(advertencia)))
                .isEqualTo(conMotivo.motivoLocalia());
        assertThat(AsignacionFechaService.motivoLocalia(conMotivo, List.of())).isNull();
    }

    // ── Trazabilidad (RF-041, RF-093) ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-041, RF-093: guarda los parámetros de generación y audita GENERAR_FECHA con modo, motivo y totales")
    void trazabilidadYAuditoria() {
        escenario();
        given(asignacion.partidosDeLaFecha(FECHA))
                .willReturn(List.of(new PartidoDeLaFecha(UUID.randomUUID(), EstadoPartido.PROGRAMADO, "Pumas", "Leones")));

        servicio.asignar(FECHA, regenerar(MOTIVO));

        ArgumentCaptor<String> parametros = ArgumentCaptor.forClass(String.class);
        verify(asignacion).registrarGeneracion(eq(FECHA), eq(PERFIL), parametros.capture());
        assertThat(parametros.getValue()).isEqualTo("{\"versionParametros\":2,\"modo\":\"REGENERAR\",\"motivo\":\""
                + MOTIVO + "\",\"asignados\":1,\"noAsignados\":0,\"cancelados\":1}");

        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().modulo()).isEqualTo("M-07");
        assertThat(evento.getValue().accion()).isEqualTo("GENERAR_FECHA");
        assertThat(evento.getValue().entidad()).isEqualTo("fechas_programacion");
        assertThat(evento.getValue().entidadId()).isEqualTo(FECHA.toString());
        assertThat(evento.getValue().motivo()).isEqualTo(MOTIVO);
        assertThat(evento.getValue().detalle())
                .containsEntry("modo", "REGENERAR")
                .containsEntry("numero", 3)
                .containsEntry("versionParametros", 2)
                .containsEntry("asignados", 1)
                .containsEntry("noAsignados", 0)
                .containsEntry("cancelados", 1)
                .containsEntry("jornadasCreadas", List.of());
        verify(fechaService).detalle(FECHA);
    }
}
