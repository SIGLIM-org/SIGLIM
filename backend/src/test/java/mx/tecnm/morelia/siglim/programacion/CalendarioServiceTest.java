package mx.tecnm.morelia.siglim.programacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
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
import mx.tecnm.morelia.siglim.programacion.CalendarioRepository.FilaCalendario;
import mx.tecnm.morelia.siglim.programacion.CalendarioRepository.GrupoCalendario;
import mx.tecnm.morelia.siglim.programacion.CalendarioRepository.JornadaManual;
import mx.tecnm.morelia.siglim.programacion.CalendarioRepository.ParametrosCalendario;
import mx.tecnm.morelia.siglim.programacion.CalendarioRepository.TemporadaCalendario;
import mx.tecnm.morelia.siglim.programacion.FechaRepository.FechaDeTemporada;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reglas de {@link CalendarioService}: orden de validación, omisión, reemplazo, fechas y auditoría. Sin Spring. */
class CalendarioServiceTest {

    private static final String CONSULTAR = "programacion.jornada:CONSULTAR";
    private static final String EDITAR = "programacion.jornada:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID TEMPORADA = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID GRUPO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID GRUPO_CHICO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID CATEGORIA = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PERFIL = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final List<UUID> CUATRO = List.of(
            UUID.fromString("00000000-0000-4000-8000-0000000000a1"), UUID.fromString("00000000-0000-4000-8000-0000000000a2"),
            UUID.fromString("00000000-0000-4000-8000-0000000000a3"), UUID.fromString("00000000-0000-4000-8000-0000000000a4"));
    /** Miércoles: la fecha 1 empieza el sábado 8 (DEC-186). */
    private static final LocalDate INICIO = LocalDate.parse("2031-03-05");
    private static final String MOTIVO = "Se agregó un equipo al grupo";

    private final CalendarioRepository calendario = mock(CalendarioRepository.class);
    private final FechaRepository fechas = mock(FechaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final CalendarioService servicio = new CalendarioService(calendario, fechas, autorizacion, auditoria,
            motivoOperacion, new SiglimPropiedades(null, null, null, LIGA));

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

    // ── Datos de prueba ──────────────────────────────────────────────────────────────────────

    private void temporada(String estado, LocalDate fin) {
        given(calendario.bloquearTemporada(LIGA, TEMPORADA))
                .willReturn(Optional.of(new TemporadaCalendario(TEMPORADA, "Temporada 2031", estado, INICIO, fin)));
        given(calendario.buscarTemporada(LIGA, TEMPORADA))
                .willReturn(Optional.of(new TemporadaCalendario(TEMPORADA, "Temporada 2031", estado, INICIO, fin)));
    }

    private static GrupoCalendario grupo(UUID id, String nombre, boolean conCalendario) {
        return new GrupoCalendario(id, nombre, CATEGORIA, "Libre", conCalendario);
    }

    /** Temporada en configuración, 2 vueltas, un grupo de 4 y otro de 1; sin fechas ni jornadas. */
    private void escenario(boolean conCalendario) {
        temporada("CONFIGURACION", null);
        given(calendario.parametrosVigentes(TEMPORADA)).willReturn(Optional.of(new ParametrosCalendario(3, 2)));
        given(calendario.listarGrupos(TEMPORADA)).willReturn(List.of(
                grupo(GRUPO, "Grupo 1", conCalendario), grupo(GRUPO_CHICO, "Grupo 2", false)));
        given(calendario.equiposActivos(GRUPO)).willReturn(CUATRO);
        given(calendario.equiposActivos(GRUPO_CHICO)).willReturn(List.of(UUID.randomUUID()));
        given(calendario.gruposConProgramacion(anyList())).willReturn(List.of());
        given(calendario.ultimaFechaVigente(TEMPORADA)).willReturn(6);
        given(fechas.listarDeTemporada(TEMPORADA)).willReturn(List.of());
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private void rechaza(GenerarCalendarioRequest peticion, HttpStatus estado, String codigo) {
        assertThatThrownBy(() -> servicio.generar(TEMPORADA, peticion))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(estado, codigo));
        sinEscrituras();
    }

    private void sinEscrituras() {
        verify(calendario, never()).insertar(any(), any(), any());
        verify(calendario, never()).reemplazar(any(), any(), any());
        verify(fechas, never()).insertarFechas(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @SuppressWarnings("unchecked")
    private SortedMap<Integer, List<LocalDate>> fechasInsertadas() {
        ArgumentCaptor<SortedMap<Integer, List<LocalDate>>> nuevas = ArgumentCaptor.forClass(SortedMap.class);
        verify(fechas).insertarFechas(eq(TEMPORADA), nuevas.capture(), eq(PERFIL));
        return nuevas.getValue();
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static List<LocalDate> fin(String sabado) {
        LocalDate dia = LocalDate.parse(sabado);
        return List.of(dia, dia.plusDays(1));
    }

    // ── Generación ───────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-034, DEC-187: genera el grupo con 2+ equipos activos, omite el de 1, crea las fechas y audita")
    @SuppressWarnings("unchecked")
    void generar() {
        escenario(false);

        CalendarioGeneradoDto resultado = servicio.generar(TEMPORADA, new GenerarCalendarioRequest(null, null, null));

        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        ArgumentCaptor<List<Emparejamiento>> calendarioGenerado = ArgumentCaptor.forClass(List.class);
        verify(calendario).insertar(eq(GRUPO), calendarioGenerado.capture(), eq(PERFIL));
        assertThat(calendarioGenerado.getValue()).hasSize(12)
                .isEqualTo(GeneradorCalendario.generar(CUATRO, 2));
        verify(calendario, never()).insertar(eq(GRUPO_CHICO), any(), any());
        verify(calendario, never()).reemplazar(any(), any(), any());

        assertThat(resultado.versionParametros()).isEqualTo(3);
        assertThat(resultado.vueltas()).isEqualTo(2);
        assertThat(resultado.totalFechas()).isEqualTo(6);
        assertThat(resultado.fechasCreadas()).isEqualTo(6);
        assertThat(resultado.grupos()).singleElement().satisfies(grupo -> {
            assertThat(grupo.grupoId()).isEqualTo(GRUPO);
            assertThat(grupo.equipos()).isEqualTo(4);
            assertThat(grupo.fechas()).isEqualTo(6);
            assertThat(grupo.reemplazado()).isFalse();
        });
        assertThat(resultado.gruposOmitidos()).singleElement().satisfies(omitido -> {
            assertThat(omitido.grupoId()).isEqualTo(GRUPO_CHICO);
            assertThat(omitido.equiposActivos()).isEqualTo(1);
            assertThat(omitido.causa()).contains("al menos 2");
        });
        assertThat(resultado.advertencias()).isEmpty();

        SortedMap<Integer, List<LocalDate>> nuevas = fechasInsertadas();
        assertThat(nuevas.keySet()).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(nuevas.get(1)).as("primer sábado desde el inicio de la temporada (DEC-186)")
                .isEqualTo(fin("2031-03-08"));
        assertThat(nuevas.get(6)).isEqualTo(fin("2031-04-12"));

        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-07");
        assertThat(evento.accion()).isEqualTo("GENERAR_CALENDARIO");
        assertThat(evento.entidad()).isEqualTo("temporadas");
        assertThat(evento.entidadId()).isEqualTo(TEMPORADA.toString());
        assertThat(evento.motivo()).isNull();
        assertThat(evento.detalle())
                .containsEntry("versionParametros", 3)
                .containsEntry("vueltas", 2)
                .containsEntry("grupos", List.of(GRUPO.toString()))
                .containsEntry("omitidos", List.of(GRUPO_CHICO.toString()))
                .containsEntry("reemplazados", List.of())
                .containsEntry("emparejamientos", 12)
                .containsEntry("totalFechas", 6)
                .containsEntry("fechasCreadas", 6)
                .containsEntry("fechaInicio", INICIO.toString());
        verifyNoInteractions(motivoOperacion);
    }

    @Test
    @DisplayName("RF-034: sin cuerpo equivale a {} y genera todos los grupos")
    void generarSinCuerpo() {
        escenario(false);

        CalendarioGeneradoDto resultado = servicio.generar(TEMPORADA, null);

        assertThat(resultado.grupos()).extracting(CalendarioGeneradoDto.GrupoGenerado::grupoId).containsExactly(GRUPO);
    }

    @Test
    @DisplayName("VAL-015: sin programacion.jornada:EDITAR no lee ni escribe")
    void generarSinPermiso() {
        willThrow(ReglaNegocioException.prohibida("VAL-015", "Sin permiso"))
                .given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.generar(TEMPORADA, null))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(calendario, fechas, auditoria);
    }

    @Test
    @DisplayName("RF-034: grupoIds vacío o con un elemento nulo responde 400 VALIDACION antes de leer la temporada")
    void cuerpoInvalido() {
        rechaza(new GenerarCalendarioRequest(null, List.of(), null), HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION);
        rechaza(new GenerarCalendarioRequest(null, Arrays.asList(GRUPO, null), null), HttpStatus.BAD_REQUEST,
                ErroresTecnicos.VALIDACION);
        verifyNoInteractions(calendario);
    }

    @Test
    @DisplayName("RF-034: una temporada que no existe en la liga responde 404")
    void temporadaInexistente() {
        given(calendario.bloquearTemporada(LIGA, TEMPORADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.generar(TEMPORADA, null)).isInstanceOf(RecursoNoEncontradoException.class);
        sinEscrituras();
    }

    @Test
    @DisplayName("RF-003: una temporada cerrada responde 409 RF-003")
    void temporadaCerrada() {
        escenario(false);
        temporada("CERRADA", null);

        rechaza(null, HttpStatus.CONFLICT, "RF-003");
        verify(calendario, never()).parametrosVigentes(any());
    }

    @Test
    @DisplayName("RF-006, DEC-190: sin parámetros vigentes responde 409 RF-006")
    void sinParametros() {
        escenario(false);
        given(calendario.parametrosVigentes(TEMPORADA)).willReturn(Optional.empty());

        rechaza(null, HttpStatus.CONFLICT, "RF-006");
        verify(calendario, never()).listarGrupos(any());
    }

    @Test
    @DisplayName("RF-034: un grupo de otra temporada responde 422 REFERENCIA_INVALIDA")
    void grupoAjeno() {
        escenario(false);

        rechaza(new GenerarCalendarioRequest(null, List.of(GRUPO, UUID.randomUUID()), null),
                HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA);
    }

    @Test
    @DisplayName("RN-021, DEC-189: reemplazar un calendario sin motivo de 10 caracteres responde 400 RN-021")
    void reemplazoSinMotivo() {
        escenario(true);

        rechaza(null, HttpStatus.BAD_REQUEST, "RN-021");
        rechaza(new GenerarCalendarioRequest(null, null, "  corto  "), HttpStatus.BAD_REQUEST, "RN-021");
        verify(calendario, never()).gruposConProgramacion(anyList());
    }

    @Test
    @DisplayName("RN-021: si solo se generan grupos sin calendario, el motivo no es obligatorio")
    void sinReemplazoNoExigeMotivo() {
        escenario(false);
        given(calendario.listarGrupos(TEMPORADA)).willReturn(List.of(
                grupo(GRUPO, "Grupo 1", false), grupo(GRUPO_CHICO, "Grupo 2", true)));

        CalendarioGeneradoDto resultado = servicio.generar(TEMPORADA, new GenerarCalendarioRequest(null, List.of(GRUPO), null));

        assertThat(resultado.grupos()).hasSize(1);
        assertThat(resultado.gruposOmitidos()).as("solo los grupos pedidos").isEmpty();
    }

    @Test
    @DisplayName("RN-020: un grupo con partidos o jornadas de fecha responde 409 RN-020 con la lista y no escribe")
    void regenerarConPartidos() {
        escenario(true);
        given(calendario.gruposConProgramacion(anyList())).willReturn(List.of(GRUPO));

        assertThatThrownBy(() -> servicio.generar(TEMPORADA, new GenerarCalendarioRequest(null, null, MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    assertThat(excepcion.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(excepcion.codigo()).isEqualTo("RN-020");
                    assertThat(excepcion.getMessage()).contains("«Libre – Grupo 1»");
                    assertThat(excepcion.propiedades().get("grupos"))
                            .isEqualTo(List.of(Map.of("grupoId", GRUPO.toString(), "nombre", "Grupo 1")));
                });
        sinEscrituras();
        verify(calendario, never()).equiposActivos(any());
    }

    @Test
    @DisplayName("DEC-187: si ningún grupo tiene 2 equipos activos responde 422 VALIDACION")
    void ningunGrupoGenerable() {
        escenario(false);
        given(calendario.equiposActivos(GRUPO)).willReturn(List.of());

        rechaza(null, HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("DEC-193: fechaInicio anterior al inicio de la temporada responde 422")
    void fechaInicioAnterior() {
        escenario(false);

        rechaza(new GenerarCalendarioRequest(INICIO.minusDays(1), null, null), HttpStatus.UNPROCESSABLE_CONTENT,
                ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("DEC-193: fechaInicio con fechas que ya tienen jornadas responde 422")
    void fechaInicioConJornadas() {
        escenario(false);
        given(fechas.algunaFechaConJornadas(TEMPORADA)).willReturn(true);

        rechaza(new GenerarCalendarioRequest(INICIO.plusDays(7), null, null), HttpStatus.UNPROCESSABLE_CONTENT,
                ErroresTecnicos.VALIDACION);
    }

    @Test
    @DisplayName("DEC-189: regenerar con motivo reemplaza el calendario vigente, fija el motivo y lo audita")
    void regenerarConMotivo() {
        escenario(true);

        CalendarioGeneradoDto resultado = servicio.generar(TEMPORADA, new GenerarCalendarioRequest(null, null, MOTIVO));

        verify(motivoOperacion).fijar(MOTIVO);
        verify(calendario).reemplazar(GRUPO, PERFIL, MOTIVO);
        verify(calendario).insertar(eq(GRUPO), anyList(), eq(PERFIL));
        assertThat(resultado.grupos()).singleElement()
                .satisfies(grupo -> assertThat(grupo.reemplazado()).isTrue());
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
        assertThat(evento.detalle()).containsEntry("reemplazados", List.of(GRUPO.toString()));
    }

    @Test
    @DisplayName("DEC-193: con jornadas en fechas, las nuevas siguen semana a semana desde la última y no se recalculan")
    void fechasNuevasSiguenALaUltima() {
        escenario(false);
        given(fechas.algunaFechaConJornadas(TEMPORADA)).willReturn(true);
        given(fechas.listarDeTemporada(TEMPORADA)).willReturn(List.of(
                new FechaDeTemporada(UUID.randomUUID(), 1, fin("2031-03-08")),
                new FechaDeTemporada(UUID.randomUUID(), 2, fin("2031-03-15")),
                new FechaDeTemporada(UUID.randomUUID(), 3, List.of(LocalDate.parse("2031-03-21"),
                        LocalDate.parse("2031-03-23")))));

        CalendarioGeneradoDto resultado = servicio.generar(TEMPORADA, null);

        assertThat(resultado.fechasCreadas()).isEqualTo(3);
        SortedMap<Integer, List<LocalDate>> nuevas = fechasInsertadas();
        assertThat(nuevas.keySet()).containsExactly(4, 5, 6);
        assertThat(nuevas.get(4)).as("el sábado después del último día de la fecha 3").isEqualTo(fin("2031-03-29"));
        assertThat(nuevas.get(6)).isEqualTo(fin("2031-04-12"));
        verify(fechas, never()).actualizarDias(any(), any());
        assertThat(eventoAuditado().detalle()).doesNotContainKey("fechaInicio");
    }

    @Test
    @DisplayName("DEC-193: sin jornadas, fechaInicio recalcula los días de todas las fechas, también las mayores que F")
    void fechaInicioRecalculaTodas() {
        escenario(false);
        UUID fecha1 = UUID.randomUUID();
        UUID fecha8 = UUID.randomUUID();
        given(fechas.listarDeTemporada(TEMPORADA)).willReturn(List.of(
                new FechaDeTemporada(fecha1, 1, fin("2031-03-08")),
                new FechaDeTemporada(fecha8, 8, fin("2031-04-26"))));
        LocalDate nuevoInicio = LocalDate.parse("2031-03-14");

        CalendarioGeneradoDto resultado = servicio.generar(TEMPORADA,
                new GenerarCalendarioRequest(nuevoInicio, null, null));

        verify(fechas).actualizarDias(fecha1, fin("2031-03-15"));
        verify(fechas).actualizarDias(fecha8, fin("2031-05-03"));
        assertThat(fechasInsertadas().keySet()).containsExactly(2, 3, 4, 5, 6);
        assertThat(resultado.fechasCreadas()).isEqualTo(5);
        assertThat(eventoAuditado().detalle()).containsEntry("fechasRecalculadas", 2)
                .containsEntry("fechaInicio", nuevoInicio.toString());
    }

    @Test
    @DisplayName("DEC-193: advierte si las fechas pasan del fin de la temporada y si hay jornadas manuales con el mismo número")
    void advertencias() {
        escenario(false);
        temporada("ACTIVA", LocalDate.parse("2031-03-31"));
        given(fechas.listarDeTemporada(TEMPORADA)).willReturn(List.of(), List.of(
                new FechaDeTemporada(UUID.randomUUID(), 1, fin("2031-03-08")),
                new FechaDeTemporada(UUID.randomUUID(), 6, fin("2031-04-12")),
                new FechaDeTemporada(UUID.randomUUID(), 9, fin("2031-05-03"))));
        given(calendario.jornadasManuales(any())).willReturn(List.of(
                new JornadaManual(GRUPO, 2), new JornadaManual(GRUPO, 5), new JornadaManual(GRUPO, 7)));

        CalendarioGeneradoDto resultado = servicio.generar(TEMPORADA, null);

        assertThat(resultado.advertencias()).hasSize(2);
        assertThat(resultado.advertencias().get(0)).satisfies(aviso -> {
            assertThat(aviso.tipo()).isEqualTo(CalendarioService.FECHAS_DESPUES_DEL_FIN);
            assertThat(aviso.grupoId()).isNull();
            assertThat(aviso.mensaje()).as("solo fechas ≤ F").contains("2031-04-13").doesNotContain("2031-05-04");
        });
        assertThat(resultado.advertencias().get(1)).satisfies(aviso -> {
            assertThat(aviso.tipo()).isEqualTo(CalendarioService.JORNADAS_MANUALES);
            assertThat(aviso.grupoId()).isEqualTo(GRUPO);
            assertThat(aviso.mensaje()).contains("2, 5").doesNotContain("7");
        });
    }

    // ── Consulta ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-034, RF-037: el calendario lista todos los grupos con sus fechas, partidos y descanso")
    void consultar() {
        temporada("ACTIVA", null);
        given(calendario.listarGrupos(TEMPORADA)).willReturn(List.of(
                grupo(GRUPO, "Grupo 1", true), grupo(GRUPO_CHICO, "Grupo 2", false)));
        CalendarioDto.EquipoDto a = new CalendarioDto.EquipoDto(CUATRO.get(0), "Halcones", "ACTIVO");
        CalendarioDto.EquipoDto b = new CalendarioDto.EquipoDto(CUATRO.get(1), "Toros", "ACTIVO");
        CalendarioDto.EquipoDto c = new CalendarioDto.EquipoDto(CUATRO.get(2), "Águilas", "BAJA");
        UUID partido = UUID.randomUUID();
        given(calendario.listarVigentes(TEMPORADA)).willReturn(List.of(
                new FilaCalendario(UUID.randomUUID(), GRUPO, 1, 1, partido, a, b),
                new FilaCalendario(UUID.randomUUID(), GRUPO, 1, 1, null, c, null),
                new FilaCalendario(UUID.randomUUID(), GRUPO, 1, 2, null, b, c)));

        CalendarioDto resultado = servicio.consultar(TEMPORADA);

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deLiga());
        assertThat(resultado.grupos()).hasSize(2);
        CalendarioDto.GrupoDto primero = resultado.grupos().getFirst();
        assertThat(primero.generado()).isTrue();
        assertThat(primero.categoria().nombre()).isEqualTo("Libre");
        assertThat(primero.fechas()).extracting(CalendarioDto.FechaDto::numero).containsExactly(1, 2);
        assertThat(primero.fechas().getFirst().partidos()).singleElement().satisfies(fila -> {
            assertThat(fila.local()).isEqualTo(a);
            assertThat(fila.partidoId()).isEqualTo(partido);
        });
        assertThat(primero.fechas().getFirst().descansa()).isEqualTo(c);
        assertThat(primero.fechas().get(1).descansa()).isNull();
        assertThat(resultado.grupos().get(1).generado()).isFalse();
        assertThat(resultado.grupos().get(1).fechas()).isEmpty();
    }

    @Test
    @DisplayName("RF-034: consultar una temporada que no existe en la liga responde 404")
    void consultarInexistente() {
        given(calendario.buscarTemporada(LIGA, TEMPORADA)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(TEMPORADA)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
