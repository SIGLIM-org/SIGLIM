package mx.tecnm.morelia.siglim.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

/** Reglas de la consulta de auditoría sin Spring ni base de datos; el SQL se prueba en {@link ConsultaAuditoriaIT}. */
class ConsultaAuditoriaServiceTest {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final FiltroAuditoria FILTRO =
            new FiltroAuditoria(null, List.of(), List.of(), null, null, 1, 50, "ocurridoEn,desc");
    private static final RegistroAuditoriaDto FILA = new RegistroAuditoriaDto(7L,
            OffsetDateTime.parse("2030-03-10T10:00:00-06:00"), null, "M-08", "APROBAR_CEDULA", "cedulas", null, null, null);

    private final AuditoriaRepository repositorio = mock(AuditoriaRepository.class);
    private final AutorizacionConsultaAuditoria autorizacion = mock(AutorizacionConsultaAuditoria.class);
    private final ConsultaAuditoriaService servicio = new ConsultaAuditoriaService(repositorio, autorizacion,
            new SiglimPropiedades(null, null, null, LIGA));

    @Test
    @DisplayName("RF-134: autoriza primero, pasa liga, filtro y alcance al listado y al conteo, y arma la página")
    void listar() {
        given(autorizacion.alcanzaTodaLaLiga()).willReturn(true);
        given(repositorio.listar(LIGA, FILTRO, true)).willReturn(List.of(FILA));
        given(repositorio.contar(LIGA, FILTRO, true)).willReturn(120L);

        var pagina = servicio.listar(FILTRO);

        assertThat(pagina.contenido()).containsExactly(FILA);
        assertThat(pagina.pagina()).isEqualTo(1);
        assertThat(pagina.tamano()).isEqualTo(50);
        assertThat(pagina.totalElementos()).isEqualTo(120);
        assertThat(pagina.totalPaginas()).isEqualTo(3);
        InOrder orden = inOrder(autorizacion, repositorio);
        orden.verify(autorizacion).alcanzaTodaLaLiga();
        orden.verify(repositorio).listar(LIGA, FILTRO, true);
        orden.verify(repositorio).contar(LIGA, FILTRO, true);
    }

    @Test
    @DisplayName("VAL-015: sin permiso propaga el 403 y no toca el repositorio")
    void sinPermiso() {
        ReglaNegocioException denegacion = ReglaNegocioException.prohibida(ErroresTecnicos.SIN_PERMISO, "Sin permiso");
        given(autorizacion.alcanzaTodaLaLiga()).willThrow(denegacion);

        assertThatThrownBy(() -> servicio.listar(FILTRO)).isSameAs(denegacion);
        assertThatThrownBy(servicio::opcionesFiltro).isSameAs(denegacion);
        verifyNoInteractions(repositorio);
    }

    @Test
    @DisplayName("VAL-015: sin permiso responde 403 antes de validar el filtro, como los demás listados")
    void autorizaAntesDeValidar() {
        ReglaNegocioException denegacion = ReglaNegocioException.prohibida(ErroresTecnicos.SIN_PERMISO, "Sin permiso");
        given(autorizacion.alcanzaTodaLaLiga()).willThrow(denegacion);
        FiltroAuditoria invalido = new FiltroAuditoria(null, List.of("m-1"), List.of(), null, null, 0, 50, "ocurridoEn,desc");

        assertThatThrownBy(() -> servicio.listar(invalido)).isSameAs(denegacion);
    }

    @Test
    @DisplayName("RF-134: un filtro inválido responde 400 VALIDACION y no toca el repositorio")
    void filtroInvalido() {
        given(autorizacion.alcanzaTodaLaLiga()).willReturn(true);
        FiltroAuditoria invalido = new FiltroAuditoria(null, List.of("m-1"), List.of(), null, null, 0, 50, "ocurridoEn,desc");

        assertThatThrownBy(() -> servicio.listar(invalido)).isInstanceOfSatisfying(ReglaNegocioException.class,
                error -> assertThat(error.estado()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(repositorio);
    }

    @Test
    @DisplayName("RF-134: un ámbito que no es LIGA consulta con todaLaLiga = false y la base devuelve vacío")
    void ambitoNoLiga() {
        given(autorizacion.alcanzaTodaLaLiga()).willReturn(false);
        given(repositorio.listar(LIGA, FILTRO, false)).willReturn(List.of());
        given(repositorio.contar(LIGA, FILTRO, false)).willReturn(0L);

        var pagina = servicio.listar(FILTRO);

        assertThat(pagina.contenido()).isEmpty();
        assertThat(pagina.totalElementos()).isZero();
        verify(repositorio).listar(LIGA, FILTRO, false);
        verify(repositorio).contar(LIGA, FILTRO, false);
    }

    @Test
    @DisplayName("RF-134: las opciones de los filtros se consultan con el alcance del usuario")
    void opcionesFiltro() {
        OpcionesFiltroAuditoriaDto opciones = new OpcionesFiltroAuditoriaDto(List.of(), List.of("M-08"), List.of());
        given(autorizacion.alcanzaTodaLaLiga()).willReturn(true);
        given(repositorio.opcionesFiltro(LIGA, true)).willReturn(opciones);

        assertThat(servicio.opcionesFiltro()).isEqualTo(opciones);
        InOrder orden = inOrder(autorizacion, repositorio);
        orden.verify(autorizacion).alcanzaTodaLaLiga();
        orden.verify(repositorio).opcionesFiltro(LIGA, true);
    }
}
