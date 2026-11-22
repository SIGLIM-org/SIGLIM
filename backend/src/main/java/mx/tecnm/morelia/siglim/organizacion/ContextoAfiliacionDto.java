package mx.tecnm.morelia.siglim.organizacion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;

/**
 * Temporadas, categorías y grupos que la pantalla de afiliación necesita para capturar o revisar un
 * tarjetón (F-16, RF-101, DEC-09).
 *
 * <p>Se sirve con los permisos de afiliación para que la Directiva no dependa del permiso de
 * consultar temporadas de M-02. No incluye datos de otros módulos: solo el contexto del tarjetón.
 *
 * <p>Refs: H-08.2, RF-101, UI-023, DEC-09
 *
 * @param temporadas temporadas de la liga, de la más reciente a la más antigua
 */
public record ContextoAfiliacionDto(List<TemporadaContextoDto> temporadas) {

    public ContextoAfiliacionDto {
        temporadas = List.copyOf(temporadas);
    }

    /** Temporada con las categorías que participan en ella y sus grupos. */
    public record TemporadaContextoDto(
            UUID id,
            String nombre,
            EstadoTemporada estado,
            LocalDate fechaInicio,
            List<CategoriaContextoDto> categorias) {

        public TemporadaContextoDto {
            categorias = List.copyOf(categorias);
        }
    }

    /** Categoría del catálogo que participa en la temporada. */
    public record CategoriaContextoDto(
            UUID id,
            String nombre,
            List<GrupoContextoDto> grupos) {

        public CategoriaContextoDto {
            grupos = List.copyOf(grupos);
        }
    }

    /** Grupo de la categoría en la temporada, con sus equipos activos. */
    public record GrupoContextoDto(UUID id, String nombre, long equipos) {
    }
}
