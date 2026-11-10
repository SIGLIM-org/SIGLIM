package mx.tecnm.morelia.siglim.auditoria;

import java.util.List;

/**
 * Valores presentes en la auditoría de la liga para llenar los filtros de la consulta.
 *
 * @param perfiles perfiles que aparecen en la auditoría, activos o inactivos, por nombre
 * @param modulos  módulos distintos, ordenados
 * @param acciones acciones distintas, ordenadas
 */
public record OpcionesFiltroAuditoriaDto(List<PerfilResumenDto> perfiles, List<String> modulos,
                                         List<String> acciones) {

    public OpcionesFiltroAuditoriaDto {
        perfiles = List.copyOf(perfiles);
        modulos = List.copyOf(modulos);
        acciones = List.copyOf(acciones);
    }
}
