package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.MotivoOperacion;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.organizacion.EquipoDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.organizacion.EquipoDto.TemporadaResumenDto;
import mx.tecnm.morelia.siglim.organizacion.EquipoRepository.GrupoTemporada;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

/** Reglas de {@link EquipoService}. Sin Spring ni base de datos. */
class EquipoServiceTest {

    private static final String CONSULTAR = "organizacion.equipo:CONSULTAR";
    private static final String EDITAR = "organizacion.equipo:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CLUB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID GRUPO = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID OTRO_GRUPO = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID TEMPORADA = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID OTRA_TEMPORADA = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID CATEGORIA = UUID.fromString("77777777-7777-4777-8777-777777777777");
    /** 2 de noviembre a las 23:30 en Morelia: en UTC ya es 3 de noviembre. */
    private static final Instant AHORA = Instant.parse("2030-11-03T05:30:00Z");
    private static final String MOTIVO = "El club retiró al equipo de la categoría";

    private final EquipoRepository equipos = mock(EquipoRepository.class);
    private final ClubRepository clubes = mock(ClubRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MotivoOperacion motivoOperacion = mock(MotivoOperacion.class);
    private final EquipoService servicio = new EquipoService(equipos, clubes, autorizacion, auditoria,
            motivoOperacion, Clock.fixed(AHORA, ZoneOffset.UTC), new SiglimPropiedades(null, null, null, LIGA));

    // ── Datos de prueba ──────────────────────────────────────────────────────────────────────

    private static ClubDto club(EstadoClub estado) {
        return new ClubDto(CLUB, "Club Halcones", estado, OffsetDateTime.parse("2030-03-01T06:00:00-06:00"),
                estado == EstadoClub.CERRADO ? LocalDate.parse("2030-06-01") : null,
                estado == EstadoClub.CERRADO ? "Cambio de nombre" : null);
    }

    private static GrupoTemporada grupo(UUID id, UUID temporada, EstadoTemporada estado) {
        return new GrupoTemporada(id, id.equals(GRUPO) ? "A" : "B", temporada, "Temporada 2030", estado);
    }

    private static EquipoDto equipo(EstadoEquipo estado, EstadoTemporada temporada) {
        return new EquipoDto(ID, "Halcones", estado, new ReferenciaDto(CLUB, "Club Halcones"),
                new ReferenciaDto(GRUPO, "A"), new ReferenciaDto(CATEGORIA, "Libre"),
                new TemporadaResumenDto(TEMPORADA, "Temporada 2030", temporada),
                OffsetDateTime.parse("2030-03-01T06:00:00-06:00"),
                estado == EstadoEquipo.BAJA ? LocalDate.parse("2030-05-01") : null,
                estado == EstadoEquipo.BAJA ? MOTIVO : null);
    }

    private void existeEquipo(EstadoEquipo estado, EstadoTemporada temporada) {
        EquipoDto equipo = equipo(estado, temporada);
        given(equipos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.of(equipo));
        given(equipos.buscar(LIGA, ID)).willReturn(Optional.of(equipo));
    }

    private static Consumer<ReglaNegocioException> error(HttpStatus estado, String codigo) {
        return excepcion -> {
            assertThat(excepcion.estado()).isEqualTo(estado);
            assertThat(excepcion.codigo()).isEqualTo(codigo);
        };
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static CrearEquipoRequest crear() {
        return new CrearEquipoRequest(CLUB, GRUPO, " Halcones ");
    }

    // ── Listado y consulta ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ADR-0007: el listado usa el alcance del permiso en la consulta y en el total")
    void listarUsaAlcance() {
        AlcanceAutorizacion alcance = new AlcanceAutorizacion(false, Set.of(CLUB), false);
        FiltroEquipos filtro = new FiltroEquipos(TEMPORADA, null, null, null, 0, 20);
        given(autorizacion.alcance(CONSULTAR)).willReturn(alcance);
        given(equipos.listar(LIGA, filtro, alcance)).willReturn(List.of(equipo(EstadoEquipo.ACTIVO,
                EstadoTemporada.ACTIVA)));
        given(equipos.contar(LIGA, filtro, alcance)).willReturn(1L);

        var pagina = servicio.listar(filtro);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.totalElementos()).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-009: un tamaño de página fuera de rango responde 400 VALIDACION")
    void listarRechazaTamanoInvalido() {
        given(autorizacion.alcance(CONSULTAR)).willReturn(new AlcanceAutorizacion(true, Set.of(), false));

        assertThatThrownBy(() -> servicio.listar(new FiltroEquipos(null, null, null, null, 0, 0)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
        verify(equipos, never()).listar(any(), any(), any());
    }

    @Test
    @DisplayName("VAL-024: la consulta exige el permiso sobre el club del equipo")
    void consultarExigeAmbitoDelClub() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.ACTIVA);

        servicio.consultar(ID);

        verify(autorizacion).exigir(CONSULTAR, ObjetivoAutorizacion.deClubes(CLUB));
    }

    @Test
    @DisplayName("VAL-024: un equipo inexistente responde 404 antes de autorizar")
    void consultarInexistente() {
        given(equipos.buscar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ID)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }

    // ── Alta ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-009: registra el equipo en el grupo, exige el ámbito del club y audita REGISTRAR_EQUIPO")
    void crearRegistraYAudita() {
        given(clubes.buscar(LIGA, CLUB)).willReturn(Optional.of(club(EstadoClub.ACTIVO)));
        given(equipos.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.of(grupo(GRUPO, TEMPORADA,
                EstadoTemporada.ACTIVA)));
        given(equipos.insertar(CLUB, GRUPO, "Halcones")).willReturn(ID);
        given(equipos.buscar(LIGA, ID)).willReturn(Optional.of(equipo(EstadoEquipo.ACTIVO, EstadoTemporada.ACTIVA)));

        EquipoDto equipo = servicio.crear(crear());

        assertThat(equipo.estado()).isEqualTo(EstadoEquipo.ACTIVO);
        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(equipos).existeNombre(GRUPO, "Halcones", null);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.modulo()).isEqualTo("M-03");
        assertThat(evento.accion()).isEqualTo("REGISTRAR_EQUIPO");
        assertThat(evento.entidad()).isEqualTo("equipos");
        assertThat(evento.entidadId()).isEqualTo(ID.toString());
        assertThat(evento.detalle()).containsEntry("clubId", CLUB.toString())
                .containsEntry("grupoId", GRUPO.toString())
                .containsEntry("temporadaId", TEMPORADA.toString())
                .containsEntry("estado", "ACTIVO");
    }

    @Test
    @DisplayName("RF-009: también se registra con la temporada en configuración")
    void crearEnConfiguracion() {
        given(clubes.buscar(LIGA, CLUB)).willReturn(Optional.of(club(EstadoClub.ACTIVO)));
        given(equipos.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.of(grupo(GRUPO, TEMPORADA,
                EstadoTemporada.CONFIGURACION)));
        given(equipos.insertar(CLUB, GRUPO, "Halcones")).willReturn(ID);
        given(equipos.buscar(LIGA, ID)).willReturn(Optional.of(equipo(EstadoEquipo.ACTIVO,
                EstadoTemporada.CONFIGURACION)));

        assertThat(servicio.crear(crear()).id()).isEqualTo(ID);
    }

    @Test
    @DisplayName("VAL-024: un club inexistente responde 404 antes de autorizar")
    void crearClubInexistente() {
        given(clubes.buscar(LIGA, CLUB)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(crear())).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion, auditoria);
    }

    @Test
    @DisplayName("VAL-024: un club fuera de ámbito responde 404 y no consulta el grupo")
    void crearClubFueraDeAmbito() {
        given(clubes.buscar(LIGA, CLUB)).willReturn(Optional.of(club(EstadoClub.ACTIVO)));
        willThrow(Autorizacion.fueraDeAmbito(EDITAR)).given(autorizacion)
                .exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.crear(crear())).isInstanceOf(RecursoNoEncontradoException.class);
        verify(equipos, never()).buscarGrupo(any(), any());
        verify(equipos, never()).insertar(any(), any(), any());
    }

    @Test
    @DisplayName("RN-041: un club cerrado no recibe equipos nuevos (409)")
    void crearEnClubCerrado() {
        given(clubes.buscar(LIGA, CLUB)).willReturn(Optional.of(club(EstadoClub.CERRADO)));

        assertThatThrownBy(() -> servicio.crear(crear()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, "RN-041").accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("Club Halcones");
                });
        verify(equipos, never()).insertar(any(), any(), any());
    }

    @Test
    @DisplayName("RF-009: un grupo inexistente responde 422 REFERENCIA_INVALIDA")
    void crearGrupoInexistente() {
        given(clubes.buscar(LIGA, CLUB)).willReturn(Optional.of(club(EstadoClub.ACTIVO)));
        given(equipos.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(crear()))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, ErroresTecnicos.REFERENCIA_INVALIDA));
    }

    @Test
    @DisplayName("RF-003: no se registran equipos en una temporada cerrada (409)")
    void crearEnTemporadaCerrada() {
        given(clubes.buscar(LIGA, CLUB)).willReturn(Optional.of(club(EstadoClub.ACTIVO)));
        given(equipos.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.of(grupo(GRUPO, TEMPORADA,
                EstadoTemporada.CERRADA)));

        assertThatThrownBy(() -> servicio.crear(crear()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        verify(equipos, never()).insertar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-009: un nombre repetido en el grupo responde 409 DUPLICADO")
    void crearDuplicado() {
        given(clubes.buscar(LIGA, CLUB)).willReturn(Optional.of(club(EstadoClub.ACTIVO)));
        given(equipos.buscarGrupo(LIGA, GRUPO)).willReturn(Optional.of(grupo(GRUPO, TEMPORADA,
                EstadoTemporada.ACTIVA)));
        given(equipos.existeNombre(GRUPO, "Halcones", null)).willReturn(true);

        assertThatThrownBy(() -> servicio.crear(crear()))
                .isInstanceOfSatisfying(ReglaNegocioException.class, excepcion -> {
                    error(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO).accept(excepcion);
                    assertThat(excepcion.getMessage()).contains("Halcones").contains("«A»");
                });
        verify(equipos, never()).insertar(any(), any(), any());
    }

    // ── Edición ──────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-009: el PATCH cambia el nombre, exige el ámbito del club y audita los campos")
    void actualizarNombre() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.ACTIVA);

        servicio.actualizar(ID, new ActualizarEquipoRequest(" Halcones B ", null));

        verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));
        verify(equipos).actualizar(ID, "Halcones B", GRUPO);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("EDITAR_EQUIPO");
        assertThat(evento.detalle()).isEqualTo(Map.of("campos", List.of("nombre")));
    }

    @Test
    @DisplayName("RN-007: en configuración el equipo cambia a otro grupo de la misma temporada")
    void actualizarGrupoEnConfiguracion() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.CONFIGURACION);
        given(equipos.buscarGrupo(LIGA, OTRO_GRUPO)).willReturn(Optional.of(grupo(OTRO_GRUPO, TEMPORADA,
                EstadoTemporada.CONFIGURACION)));

        servicio.actualizar(ID, new ActualizarEquipoRequest(null, OTRO_GRUPO));

        verify(equipos).existeNombre(OTRO_GRUPO, "Halcones", ID);
        verify(equipos).actualizar(ID, "Halcones", OTRO_GRUPO);
        assertThat(eventoAuditado().detalle()).isEqualTo(Map.of("campos", List.of("grupoId")));
    }

    @Test
    @DisplayName("RN-007: con la temporada activa el equipo no cambia de grupo (409)")
    void actualizarGrupoEnTemporadaActiva() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.ACTIVA);

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarEquipoRequest(null, OTRO_GRUPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RN-007"));
        verify(equipos, never()).actualizar(any(), any(), any());
    }

    @Test
    @DisplayName("RN-007: el equipo no cambia a un grupo de otra temporada (422)")
    void actualizarGrupoDeOtraTemporada() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.CONFIGURACION);
        given(equipos.buscarGrupo(LIGA, OTRO_GRUPO)).willReturn(Optional.of(grupo(OTRO_GRUPO, OTRA_TEMPORADA,
                EstadoTemporada.CONFIGURACION)));

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarEquipoRequest(null, OTRO_GRUPO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.UNPROCESSABLE_CONTENT, "RN-007"));
        verify(equipos, never()).actualizar(any(), any(), any());
    }

    @Test
    @DisplayName("RN-007: el mismo grupo con la temporada activa no es un cambio")
    void actualizarMismoGrupoEnActiva() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.ACTIVA);

        servicio.actualizar(ID, new ActualizarEquipoRequest("Halcones", GRUPO));

        verify(equipos, never()).actualizar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-009: un PATCH sin cambios responde el equipo y no audita")
    void actualizarSinCambios() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.ACTIVA);

        EquipoDto equipo = servicio.actualizar(ID, new ActualizarEquipoRequest(null, null));

        assertThat(equipo.id()).isEqualTo(ID);
        verify(equipos, never()).actualizar(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-009: un nombre ocupado en el grupo responde 409 DUPLICADO")
    void actualizarNombreDuplicado() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.ACTIVA);
        given(equipos.existeNombre(GRUPO, "Toros", ID)).willReturn(true);

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarEquipoRequest("Toros", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.CONFLICT, ErroresTecnicos.DUPLICADO));
        verify(equipos, never()).actualizar(any(), any(), any());
    }

    @Test
    @DisplayName("RF-009: un nombre en blanco responde 400 VALIDACION")
    void actualizarNombreEnBlanco() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.ACTIVA);

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarEquipoRequest("  ", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error(HttpStatus.BAD_REQUEST, ErroresTecnicos.VALIDACION));
    }

    @Test
    @DisplayName("RF-009: un equipo dado de baja no admite PATCH (409)")
    void actualizarEquipoDeBaja() {
        existeEquipo(EstadoEquipo.BAJA, EstadoTemporada.ACTIVA);

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarEquipoRequest("Otro", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-009"));
        verify(equipos, never()).actualizar(any(), any(), any());
    }

    @Test
    @DisplayName("RF-003: no se edita un equipo de una temporada cerrada (409)")
    void actualizarEnTemporadaCerrada() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.CERRADA);

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarEquipoRequest("Otro", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        verify(equipos, never()).actualizar(any(), any(), any());
    }

    @Test
    @DisplayName("VAL-024: un equipo de otro club responde 404 antes de revisar su estado")
    void actualizarFueraDeAmbito() {
        existeEquipo(EstadoEquipo.BAJA, EstadoTemporada.CERRADA);
        willThrow(Autorizacion.fueraDeAmbito(EDITAR)).given(autorizacion)
                .exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));

        assertThatThrownBy(() -> servicio.actualizar(ID, new ActualizarEquipoRequest("Otro", null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ── Baja ─────────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-009, RN-021: la baja fija el motivo, usa la fecha de Morelia y audita DAR_BAJA_EQUIPO")
    void darDeBaja() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.ACTIVA);

        servicio.darDeBaja(ID, new BajaEquipoRequest("  " + MOTIVO + "  "));

        LocalDate hoyEnMorelia = LocalDate.parse("2030-11-02");
        InOrder orden = inOrder(autorizacion, motivoOperacion, equipos);
        orden.verify(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deClubes(CLUB));
        orden.verify(motivoOperacion).fijar(MOTIVO);
        orden.verify(equipos).darDeBaja(ID, MOTIVO, hoyEnMorelia);
        EventoAuditoria evento = eventoAuditado();
        assertThat(evento.accion()).isEqualTo("DAR_BAJA_EQUIPO");
        assertThat(evento.entidad()).isEqualTo("equipos");
        assertThat(evento.motivo()).isEqualTo(MOTIVO);
        assertThat(evento.detalle()).containsEntry("estado", "BAJA").containsEntry("bajaEn", "2030-11-02");
    }

    @Test
    @DisplayName("RN-021: un motivo de menos de 10 caracteres responde 400 y no da de baja")
    void darDeBajaConMotivoCorto() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.ACTIVA);

        assertThatThrownBy(() -> servicio.darDeBaja(ID, new BajaEquipoRequest("  corto   ")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        assertThatThrownBy(() -> servicio.darDeBaja(ID, new BajaEquipoRequest(null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.BAD_REQUEST, "RN-021"));
        verify(motivoOperacion, never()).fijar(anyString());
        verify(equipos, never()).darDeBaja(any(), any(), any());
    }

    @Test
    @DisplayName("RF-009: una baja repetida responde 409")
    void darDeBajaRepetida() {
        existeEquipo(EstadoEquipo.BAJA, EstadoTemporada.ACTIVA);

        assertThatThrownBy(() -> servicio.darDeBaja(ID, new BajaEquipoRequest(MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-009"));
        verify(equipos, never()).darDeBaja(any(), any(), any());
        verifyNoInteractions(auditoria);
    }

    @Test
    @DisplayName("RF-003: no se da de baja un equipo de una temporada cerrada (409)")
    void darDeBajaEnTemporadaCerrada() {
        existeEquipo(EstadoEquipo.ACTIVO, EstadoTemporada.CERRADA);

        assertThatThrownBy(() -> servicio.darDeBaja(ID, new BajaEquipoRequest(MOTIVO)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error(HttpStatus.CONFLICT, "RF-003"));
        verify(equipos, never()).darDeBaja(any(), any(), any());
    }

    @Test
    @DisplayName("VAL-024: dar de baja un equipo inexistente responde 404")
    void darDeBajaInexistente() {
        given(equipos.buscarParaActualizar(LIGA, ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.darDeBaja(ID, new BajaEquipoRequest(MOTIVO)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(autorizacion);
    }
}
