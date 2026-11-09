package mx.tecnm.morelia.siglim.organizacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Consulta de personas por CURP (RF-010, RF-011), sin Spring ni base de datos. */
class PersonaServiceTest {

    private static final String CONSULTAR = "organizacion.persona:CONSULTAR";
    private static final UUID ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String CURP = "GODE840101HDFRNS09";
    private static final PersonaDto PERSONA = new PersonaDto(ID, CURP, "Ana", "López", null);

    private final PersonaRepository personas = mock(PersonaRepository.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final PersonaService servicio = new PersonaService(personas, autorizacion);

    @Test
    @DisplayName("RF-010: consulta por CURP con el permiso en cualquier ámbito")
    void buscaPorCurp() {
        given(personas.buscarPorCurp(CURP)).willReturn(Optional.of(PERSONA));

        assertThat(servicio.buscarPorCurp("  " + CURP.toLowerCase() + " ")).isEqualTo(PERSONA);

        verify(autorizacion).alcance(CONSULTAR);
        verify(personas).buscarPorCurp(CURP);
    }

    @Test
    @DisplayName("RF-010: una CURP sin persona responde NO_ENCONTRADO (VAL-024)")
    void noExiste() {
        given(personas.buscarPorCurp(CURP)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.buscarPorCurp(CURP))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    @DisplayName("DEC-10: una CURP mal formada responde 400 VALIDACION sin consultar la tabla")
    void formatoInvalido() {
        assertThatThrownBy(() -> servicio.buscarPorCurp("GODE"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VALIDACION");
                    assertThat(error.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        verify(autorizacion).alcance(CONSULTAR);
        verifyNoInteractions(personas);
    }
}
