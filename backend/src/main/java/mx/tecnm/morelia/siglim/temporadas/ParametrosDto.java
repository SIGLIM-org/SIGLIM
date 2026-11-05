package mx.tecnm.morelia.siglim.temporadas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;

/**
 * Una versión de parámetros deportivos de la temporada (RF-006, RF-100). Las versiones no se editan:
 * un cambio es una versión nueva y la mayor es la vigente.
 *
 * <p>Refs: H-05.2, RF-006, RF-050, RF-100, DEC-25 a DEC-29
 *
 * @param plazoValidacionCedulaHoras plazo del árbitro para validar la cédula, en horas
 * @param categorias                 filas propias por categoría, por el orden del catálogo
 * @param esDemo                     si los valores son de demostración: la Liga aún no los confirmó
 *                                   como oficiales (PEN-021)
 * @param camposDemo                 campos que la interfaz rotula «No oficial»; vacío si
 *                                   {@code esDemo} es {@code false} (D2)
 * @param creadoPor                  perfil que registró la versión; se omite si no hay
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParametrosDto(
        UUID id,
        UUID temporadaId,
        int version,
        int puntosVictoria,
        int puntosEmpate,
        int puntosDerrota,
        List<CriterioDesempate> criteriosDesempate,
        int golesDefaultGanador,
        int golesDefaultPerdedor,
        int plazoValidacionCedulaHoras,
        int duracionJuegoMin,
        int bloqueCanchaMin,
        int clasificadosLiguilla,
        FormatoLiguilla formatoLiguilla,
        boolean empateLiguillaPorPenales,
        int equiposAscienden,
        int equiposDescienden,
        boolean permiteCambioClub,
        List<ParametrosCategoriaDto> categorias,
        boolean esDemo,
        List<String> camposDemo,
        String motivo,
        ReferenciaDto creadoPor,
        OffsetDateTime creadoEn) {

    public ParametrosDto {
        criteriosDesempate = List.copyOf(criteriosDesempate);
        categorias = List.copyOf(categorias);
        camposDemo = List.copyOf(camposDemo);
    }
}
