package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** Reglas de {@link ClubService}. Sin Spring ni base de datos. */
class ClubServiceTest {

    private static final String CONSULTAR = "organizacion.club:CONSULTAR";
    private static final String EDITAR = "organizacion.club:EDITAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final ClubDto CLUB = new ClubDto(ID, "Club Halcones", EstadoClub.ACTIVO,
            OffsetDateTime.parse("2030-03-01T06:00:00-06:00"), null, null);

    private final ClubRepository clubes = mock(ClubRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final ClubService servicio = new ClubService(clubes, autorizacion, auditoria,
            new SiglimPropiedades(null, null, null, LIGA));

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
    @DisplayName("VAL-015: sin permiso no consulta ni escribe")
    void crearSinPermiso() {
        willThrow(Autorizacion.sinPermiso()).given(autorizacion).exigir(EDITAR, ObjetivoAutorizacion.deLiga());

        assertThatThrownBy(() -> servicio.crear(new CrearClubRequest("Club Halcones")))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error ->
                        assertThat(error.codigo()).isEqualTo("VAL-015"));
        verifyNoInteractions(clubes, auditoria);
    }
}
