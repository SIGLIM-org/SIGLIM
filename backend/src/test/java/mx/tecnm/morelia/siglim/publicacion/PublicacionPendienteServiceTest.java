package mx.tecnm.morelia.siglim.publicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.publicacion.PublicacionPendienteDto.Perfil;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Reglas de {@link PublicacionPendienteService}: qué tipos ve la sesión según sus permisos. Sin Spring. */
class PublicacionPendienteServiceTest {

    private static final String APROBAR_JORNADA = "programacion.jornada:APROBAR";
    private static final String APROBAR_COMUNICADO = "publicacion.comunicado:APROBAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final AlcanceAutorizacion LIGA_COMPLETA = new AlcanceAutorizacion(true, Set.of(), false);
    private static final AlcanceAutorizacion SOLO_CLUB =
            new AlcanceAutorizacion(false, Set.of(UUID.randomUUID()), false);

    private final PublicacionPendienteRepository repositorio = mock(PublicacionPendienteRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final PublicacionPendienteService servicio = new PublicacionPendienteService(repositorio,
            autorizacion, new SiglimPropiedades(null, null, null, LIGA));

    private static PublicacionPendienteDto jornada() {
        return new PublicacionPendienteDto(TipoPublicacion.JORNADA, UUID.randomUUID(), "Jornada 3",
                "Temporada 2031 · Libre · A", 4L, new Perfil(UUID.randomUUID(), "Programador"),
                OffsetDateTime.parse("2031-03-01T10:00:00-06:00"));
    }

    @Test
    @DisplayName("RF-090, RF-121: con ambos permisos de aprobar la bandeja trae jornadas y comunicados")
    void ambosTipos() {
        given(autorizacion.alcance(APROBAR_JORNADA)).willReturn(LIGA_COMPLETA);
        given(autorizacion.alcance(APROBAR_COMUNICADO)).willReturn(LIGA_COMPLETA);
        given(repositorio.listar(LIGA, true, true, 20, 0)).willReturn(List.of(jornada()));
        given(repositorio.contar(LIGA, true, true)).willReturn(21L);

        var pagina = servicio.listar(0, 20);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.totalElementos()).isEqualTo(21);
        assertThat(pagina.totalPaginas()).isEqualTo(2);
    }

    @Test
    @DisplayName("RF-121: con solo el permiso de aprobar jornadas no se listan comunicados")
    void soloJornadas() {
        given(autorizacion.alcance(APROBAR_JORNADA)).willReturn(LIGA_COMPLETA);
        given(autorizacion.alcance(APROBAR_COMUNICADO)).willThrow(Autorizacion.sinPermiso());

        servicio.listar(1, 10);

        verify(repositorio).listar(LIGA, true, false, 10, 10);
        verify(repositorio).contar(LIGA, true, false);
    }

    @Test
    @DisplayName("VAL-015: sin ningún permiso de aprobar responde 403 y no consulta")
    void sinPermiso() {
        given(autorizacion.alcance(any())).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(() -> servicio.listar(0, 20))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.estado()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.codigo()).isEqualTo("VAL-015");
                });
        verifyNoInteractions(repositorio);
    }

    @Test
    @DisplayName("VAL-024: un permiso de aprobar sin ámbito de liga responde 404")
    void fueraDeAmbito() {
        given(autorizacion.alcance(APROBAR_JORNADA)).willReturn(SOLO_CLUB);
        given(autorizacion.alcance(APROBAR_COMUNICADO)).willThrow(Autorizacion.sinPermiso());

        assertThatThrownBy(() -> servicio.listar(0, 20)).isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(repositorio);
    }

    @Test
    @DisplayName("RF-121: la paginación inválida responde 400 VALIDACION")
    void paginacionInvalida() {
        given(autorizacion.alcance(any())).willReturn(LIGA_COMPLETA);

        assertThatThrownBy(() -> servicio.listar(-1, 20))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("VALIDACION"));
        assertThatThrownBy(() -> servicio.listar(0, 101))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("VALIDACION"));
        verify(repositorio, never()).listar(any(), anyBoolean(), anyBoolean(), anyInt(), anyLong());
    }
}
