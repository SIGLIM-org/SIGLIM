package mx.tecnm.morelia.siglim.programacion;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Resultado de {@code POST /api/v1/temporadas/{id}/calendario} (H-32.2): qué grupos se generaron,
 * cuáles se omitieron y por qué, y las advertencias que no impiden generar (DEC-187, DEC-193).
 *
 * <p>Refs: H-32.2, RF-034, RF-037, DEC-187, DEC-189, DEC-190, DEC-193
 *
 * @param versionParametros versión vigente de los parámetros de la que salieron las vueltas (DEC-190)
 * @param vueltas           vueltas del calendario
 * @param totalFechas       {@code F}: la última fecha del calendario vigente de la temporada
 * @param fechasCreadas     fechas de programación que se crearon en esta generación
 * @param grupos            grupos generados
 * @param gruposOmitidos    grupos con menos de 2 equipos {@code ACTIVO} (DEC-187)
 * @param advertencias      avisos que no bloquean
 */
public record CalendarioGeneradoDto(
        int versionParametros,
        int vueltas,
        int totalFechas,
        int fechasCreadas,
        List<GrupoGenerado> grupos,
        List<GrupoOmitido> gruposOmitidos,
        List<Advertencia> advertencias) {

    public CalendarioGeneradoDto {
        grupos = grupos == null ? List.of() : List.copyOf(grupos);
        gruposOmitidos = gruposOmitidos == null ? List.of() : List.copyOf(gruposOmitidos);
        advertencias = advertencias == null ? List.of() : List.copyOf(advertencias);
    }

    /**
     * Grupo generado.
     *
     * @param equipos     equipos {@code ACTIVO} con que se generó
     * @param fechas      fechas que ocupa su calendario
     * @param reemplazado {@code true} si ya tenía calendario y se reemplazó con motivo (DEC-189)
     */
    public record GrupoGenerado(UUID grupoId, String nombre, int equipos, int fechas, boolean reemplazado) {
    }

    /**
     * Grupo que no se generó (DEC-187).
     *
     * @param causa explicación para la persona
     */
    public record GrupoOmitido(UUID grupoId, String nombre, int equiposActivos, String causa) {
    }

    /**
     * Aviso que no impide generar (DEC-193).
     *
     * @param tipo    {@code FECHAS_DESPUES_DEL_FIN} o {@code JORNADAS_MANUALES}
     * @param grupoId grupo al que se refiere; se omite si es de toda la temporada
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Advertencia(String tipo, UUID grupoId, String mensaje) {
    }
}
