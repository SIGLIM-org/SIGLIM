package mx.tecnm.morelia.siglim.temporadas;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.CategoriaDto.ReferenciaDto;

/**
 * Una versión de parámetros deportivos de la temporada (RF-006, RF-100). Las versiones no se editan:
 * un cambio es una versión nueva y la mayor es la vigente.
 *
 * <p>Refs: H-05.2, H-32.3, RF-006, RF-050, RF-100, DEC-25 a DEC-29, DEC-174, DEC-176, DEC-194
 *
 * @param plazoValidacionCedulaHoras plazo del árbitro para validar la cédula, en horas
 * @param fechasFaseRegular          total de fechas de la fase regular; se omite si no se definió
 *                                   (RN-049, B-15.1)
 * @param fechasFinalesUnidadDeportiva últimas fechas en que se advierte una cancha externa, 10 [DEMO]
 *                                   (RF-120, VAL-027)
 * @param vueltas                    vueltas del calendario, de 1 a 4 (DEC-174)
 * @param horaInicioJuego            inicio del horario de juego por defecto, {@code HH:mm:ss} [DEMO]
 *                                   (DEC-176)
 * @param horaFinJuego               fin del horario de juego por defecto, {@code HH:mm:ss} [DEMO]
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
        int equiposPorArbitro,
        Integer fechasFaseRegular,
        int fechasFinalesUnidadDeportiva,
        int vueltas,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss") LocalTime horaInicioJuego,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss") LocalTime horaFinJuego,
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
