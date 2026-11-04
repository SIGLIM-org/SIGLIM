package mx.tecnm.morelia.siglim.disciplina;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Artículo del reglamento con su sanción, sujeto, efecto y aplicación automática (F-32, RF-129;
 * se consulta al resolver un expediente, RF-060). Los valores numéricos van solo para el tipo de
 * sanción correspondiente; {@code esDemo} marca el valor de demostración [DEMO] (H-26 criterio 3).
 *
 * @param id                      identificador
 * @param numero                  número del artículo en el reglamento
 * @param descripcion             descripción de la falta o la sanción
 * @param tipoSancion             tipo de sanción (DEC-30)
 * @param sujeto                  a quién aplica: jugador, equipo o club
 * @param efecto                  hacia adelante o retroactivo (RF-131)
 * @param aplicacionAutomatica    si se aplica sin resolución (DEC-31); una multa genera un cobro
 * @param partidosSuspension      partidos de suspensión si {@code tipoSancion = SUSPENSION_PARTIDOS}
 * @param puntos                  puntos de la tabla si {@code tipoSancion = PERDIDA_PUNTOS}
 * @param montoMulta              monto en pesos si {@code tipoSancion = MULTA} (RF-133)
 * @param otorgaDefaultRestantes  en una baja, otorga por default al rival los partidos pendientes (DEC-32)
 * @param activo                  si el artículo se sigue aplicando
 * @param esDemo                  valor de demostración [DEMO]: configurable y rotulado «no oficial»
 */
public record ArticuloDto(UUID id, String numero, String descripcion, TipoSancion tipoSancion,
                          SujetoSancion sujeto, EfectoSancion efecto, boolean aplicacionAutomatica,
                          Integer partidosSuspension, Integer puntos, BigDecimal montoMulta,
                          boolean otorgaDefaultRestantes, boolean activo, boolean esDemo) {
}
