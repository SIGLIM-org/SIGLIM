package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.TrasladoAsignacionesClub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

/** Reglas de {@link ClubService}. Sin Spring ni base de datos. */
class ClubServiceTest {

    private static final String CONSULTAR = "organizacion.club:CONSULTAR";
    private static final String EDITAR = "organizacion.club:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final ClubDto CLUB = new ClubDto(ID, "Club Halcones", EstadoClub.ACTIVO,
            OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), null, null, null);

    private static final UUID NUEVO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String MOTIVO = "La asamblea aprobó el nuevo nombre";
    private static final ClubDto CERRADO = new ClubDto(ID, "Club Halcones", EstadoClub.CERRADO,
            OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), LocalDate.parse("2030-06-01"), "Otro cambio", null);
    private static final ClubDto CLUB_NUEVO = new ClubDto(NUEVO, "Club Águilas", EstadoClub.ACTIVO,
            OffsetDateTime.parse("2030-07-01T06:00:00-06:00"), null, null,
            new ClubDto.ClubAnteriorDto(ID, "Club Halcones"));
    /** 2 de julio de 2030 a las 05:00 UTC: en México todavía es 1 de julio. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2030-07-02T05:00:00Z"), ZoneOffset.UTC);

    private final ClubRepository clubes = mock(ClubRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ConsultaAdeudosClub adeudos = mock(ConsultaAdeudosClub.class);
    private final TrasladoAsignacionesClub traslado = mock(TrasladoAsignacionesClub.class);
    private final ClubService servicio = new ClubService(clubes, autorizacion, auditoria, adeudos, traslado,
            RELOJ, new SiglimPropiedades(null, null, null, LIGA));

    @Test
    @DisplayName("ADR-0007: el listado usa el alcance del permiso en la consulta y en el total")
    void listarUsaAlcance() {
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(false, Set.of(ID), false);
        FiltroClubes filtro = new FiltroClubes("halc", EstadoClub.ACTIVO, 0, 20);
        given(autorizacion.alcance(CONSULTAR)).willReturn(alcance);
        given(clubes.listar(LIGA, filtro, alcance)).willReturn(List.of(CLUB));
        given(clubes.contar(LIGA, filtro, alcance)).willReturn(1L);

        var pagina = servicio.listar(filtro);

        assertThat(pagina.contenido()).containsExactly(CLUB);
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-008: una página o un tamaño fuera de rango responde 400 VALIDACION")
    void listarRechazaPaginaInvalida() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));

        assertThatThrownBy(() -> servicio.listar(new FiltroClubes(null, null, 0, 101)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error ->
                        assertThat(error.codigo()).isEqualTo(ErroresTecnicos.VALIDACION));
        assertThatThrownBy(() -> servicio.listar(new FiltroClubes(null, null, -1, 20)))
                .isInstanceOf(ReglaNegocioException.class);
        verify(clubes, never()).listar(any(), any(), any());
    }

    @Test
    @DisplayName("VAL-024: la consulta exige el permiso sobre el propio club")
    void consultarExigeAmbitoDelClub() {
        given(clubes.buscar(LIGA, ID)).willReturn(Optional.of(CLUB));

        assertThat(servicio.consultar(ID)).isEqualTo(CLUB);
        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deClubes(ID));
    }

    @Test
    @DisplayName("VAL-024: un club inexistente responde 404 antes de autorizar")
    void consultarInexistente() {
        given(clubes.buscar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }

    @Test
    @DisplayName("RF-008: el alta es de la liga, recorta el nombre, nace ACTIVO y se audita REGISTRAR_CLUB")
    void crearRegistraYAudita() {
        given(clubes.existeNombre(LIGA, "Club Halcones")).willReturn(false);
        given(clubes.insertar(LIGA, "Club Halcones")).willReturn(ID);
        given(clubes.buscar(LIGA, ID)).willReturn(Optional.of(CLUB));

        ClubDto club = servicio.crear(new CrearClubRequest("  Club Halcones  "));

        assertThat(club.estado()).isEqualTo(EstadoClub.ACTIVO);
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        assertThat(evento.getValue().modulo()).isEqualTo("M-03");
        assertThat(evento.getValue().accion()).isEqualTo("REGISTRAR_CLUB");
        assertThat(evento.getValue().entidad()).isEqualTo("clubes");
        assertThat(evento.getValue().entidadId()).isEqualTo(ID.toString());
        assertThat(evento.getValue().detalle()).isEqualTo(Map.of("estado", "ACTIVO"));
    }

    @Test
    @DisplayName("RF-008: un nombre repetido en la liga responde 409 DUPLICADO y nombra el club")
    void crearDuplicado() {
        given(clubes.existeNombre(LIGA, "Club Halcones")).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(new CrearClubRequest("Club Halcones")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.codigo()).isEqualTo(ErroresTecnicos.DUPLICADO);
                    assertThat(error.getMessage()).contains("Club Halcones");
                });
        verify(clubes, never()).insertar(any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-008: un nombre en blanco responde 400 VALIDACION")
    void crearSinNombre() {
        assertThatThrownBy(() -> servicio.crear(new CrearClubRequest("   ")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error ->
                        assertThat(error.codigo()).isEqualTo(ErroresTecnicos.VALIDACION));
    }

    @Test
    @DisplayName("RF-008: una carrera con el índice único se traduce a 409 DUPLICADO con el nombre del club")
    void crearCarreraDuplicada() {
        given(clubes.existeNombre(LIGA, "Club Halcones")).willReturn(false);
        given(clubes.insertar(LIGA, "Club Halcones")).willThrow(new DuplicateKeyException("ux"));

        assertThatThrownBy(() -> servicio.crear(new CrearClubRequest("Club Halcones")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo(ErroresTecnicos.DUPLICADO);
                    assertThat(error.getMessage()).contains("Club Halcones");
                });
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("VAL-015: sin permiso no consulta ni escribe")
    void crearSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(new CrearClubRequest("Club Halcones")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error ->
                        assertThat(error.codigo()).isEqualTo("VAL-015"));
        verifyNoInteractions(clubes, auditoria);
    }

    // ── Cambio de nombre (B-04, RF-105, RN-041, DEC-49, DEC-61) ──────────────────────────────

    /** Club activo, sin equipos inscritos, sin adeudos y con el nombre nuevo libre. */
    private void clubListoParaCambio() {
        given(clubes.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(CLUB));
        given(clubes.contarEquiposInscritos(LIGA, ID)).willReturn(0L);
        given(adeudos.tieneCobrosPendientesOVencidos(ID)).willReturn(false);
        given(clubes.existeNombre(LIGA, "Club Águilas")).willReturn(false);
    }

    private ClubDto cambiar(String nombre, String motivo) {
        return servicio.cambiarNombre(ID, new CambioNombreClubRequest(nombre, motivo));
    }

    /** En un error no se inserta, no se cierra, no se trasladan asignaciones ni se audita. */
    private void sinEscrituras() {
        verify(clubes, never()).insertarPorCambioNombre(any(), any(), any());
        verify(clubes, never()).cerrar(any(), any(), any());
        verifyNoInteractions(traslado, auditoria);
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    @Test
    @DisplayName("RF-105, DEC-15: crea el club nuevo, cierra el anterior hoy en México, traslada asignaciones y audita")
    void cambiarNombre() {
        clubListoParaCambio();
        given(clubes.insertarPorCambioNombre(LIGA, "Club Águilas", ID)).willReturn(NUEVO);
        given(clubes.buscar(LIGA, NUEVO)).willReturn(Optional.of(CLUB_NUEVO));

        ClubDto club = cambiar("  Club Águilas  ", "  " + MOTIVO + "  ");

        assertThat(club).isEqualTo(CLUB_NUEVO);
        InOrder orden = inOrder(autorizacion, clubes, traslado);
        orden.verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        orden.verify(clubes).buscarParaActualizar(LIGA, ID);
        orden.verify(clubes).insertarPorCambioNombre(LIGA, "Club Águilas", ID);
        orden.verify(clubes).cerrar(ID, LocalDate.parse("2030-07-01"), MOTIVO);
        orden.verify(traslado).trasladar(ID, NUEVO);

        ArgumentCaptor<EventoAuditoria> eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(2)).registrar(eventos.capture());
        EventoAuditoria cierre = eventos.getAllValues().get(0);
        assertThat(cierre.modulo()).isEqualTo("M-03");
        assertThat(cierre.accion()).isEqualTo("CERRAR_CLUB");
        assertThat(cierre.entidad()).isEqualTo("clubes");
        assertThat(cierre.entidadId()).isEqualTo(ID.toString());
        assertThat(cierre.motivo()).isEqualTo(MOTIVO);
        assertThat(cierre.detalle()).isEqualTo(Map.of("estado", "CERRADO", "clubNuevoId", NUEVO.toString(),
                "causa", "CAMBIO_NOMBRE"));
        EventoAuditoria alta = eventos.getAllValues().get(1);
        assertThat(alta.accion()).isEqualTo("REGISTRAR_CLUB");
        assertThat(alta.entidadId()).isEqualTo(NUEVO.toString());
        assertThat(alta.motivo()).isNull();
        assertThat(alta.detalle()).isEqualTo(Map.of("estado", "ACTIVO", "clubAnteriorId", ID.toString()));
    }

    @Test
    @DisplayName("VAL-015: sin permiso de liga responde 403 antes de buscar el club")
    void cambiarNombreSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> cambiar("Club Águilas", MOTIVO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.FORBIDDEN, "VAL-015"));
        verifyNoInteractions(clubes, adeudos, traslado, auditoria);
    }

    @Test
    @DisplayName("RF-105: un club inexistente responde 404")
    void cambiarNombreInexistente() {
        given(clubes.buscarParaActualizar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> cambiar("Club Águilas", MOTIVO)).isInstanceOf(RecursoNoEncontradoException.class);
        sinEscrituras();
    }

    @Test
    @DisplayName("RN-041: un club CERRADO no cambia de nombre (409), antes de validar el cuerpo")
    void cambiarNombreClubCerrado() {
        given(clubes.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(CERRADO));

        assertThatThrownBy(() -> cambiar(null, null))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RN-041"));
        sinEscrituras();
    }

    @Test
    @DisplayName("RN-021: sin motivo o con menos de 10 caracteres responde 400, antes del nombre")
    void cambiarNombreSinMotivo() {
        clubListoParaCambio();

        assertThatThrownBy(() -> cambiar(null, "  corto   "))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        assertThatThrownBy(() -> cambiar("Club Águilas", null))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        sinEscrituras();
    }

    @Test
    @DisplayName("RF-105: un nombre en blanco responde 400 VALIDACION")
    void cambiarNombreEnBlanco() {
        clubListoParaCambio();

        assertThatThrownBy(() -> cambiar("   ", MOTIVO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST,
                        ErroresTecnicos.VALIDACION));
        verify(clubes, never()).contarEquiposInscritos(any(), any());
        sinEscrituras();
    }

    @Test
    @DisplayName("DEC-49, DEC-61: con equipos inscritos en temporada activa o en configuración responde 409 RN-041")
    void cambiarNombreConEquiposInscritos() {
        clubListoParaCambio();
        given(clubes.contarEquiposInscritos(LIGA, ID)).willReturn(2L);

        assertThatThrownBy(() -> cambiar("Club Águilas", MOTIVO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "RN-041").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("entre temporadas").contains("2 equipos inscritos");
                });
        verifyNoInteractions(adeudos);
        sinEscrituras();
    }

    @Test
    @DisplayName("DEC-61, RN-052: con cobros pendientes o vencidos responde 409 RN-041")
    void cambiarNombreConAdeudos() {
        clubListoParaCambio();
        given(adeudos.tieneCobrosPendientesOVencidos(ID)).willReturn(true);

        assertThatThrownBy(() -> cambiar("Club Águilas", MOTIVO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "RN-041").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("cobros pendientes o vencidos");
                });
        verify(clubes, never()).existeNombre(any(), any());
        sinEscrituras();
    }

    @Test
    @DisplayName("RF-105: el mismo nombre actual responde 409 DUPLICADO")
    void cambiarNombreIgualAlActual() {
        clubListoParaCambio();

        assertThatThrownBy(() -> cambiar(" Club Halcones ", MOTIVO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO).accept(excepcion);
                    assertThat(excepcion.getMessage()).isEqualTo("«Club Halcones» ya es el nombre de este club.");
                });
        sinEscrituras();
    }

    @Test
    @DisplayName("DEC-49, DEC-61: un nombre usado por otro club de la liga, aunque esté cerrado, responde 409 DUPLICADO")
    void cambiarNombreUsado() {
        clubListoParaCambio();
        given(clubes.existeNombre(LIGA, "Club Águilas")).willReturn(true);

        assertThatThrownBy(() -> cambiar("Club Águilas", MOTIVO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO).accept(excepcion);
                    assertThat(excepcion.getMessage()).isEqualTo("Ya existe un club llamado «Club Águilas» en la liga.");
                });
        sinEscrituras();
    }

    @Test
    @DisplayName("RF-105: una carrera con el índice único responde 409 DUPLICADO sin cerrar ni auditar")
    void cambiarNombreCarreraDuplicada() {
        clubListoParaCambio();
        given(clubes.insertarPorCambioNombre(LIGA, "Club Águilas", ID)).willThrow(new DuplicateKeyException("ux"));

        assertThatThrownBy(() -> cambiar("Club Águilas", MOTIVO))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT,
                        ErroresTecnicos.DUPLICADO));
        verify(clubes, never()).cerrar(any(), any(), any());
        verifyNoInteractions(traslado, auditoria);
    }
}
