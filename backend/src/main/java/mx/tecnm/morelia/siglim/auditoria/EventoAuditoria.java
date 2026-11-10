package mx.tecnm.morelia.siglim.auditoria;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Operación crítica que se registra en {@code siglim.auditoria} (RF-093).
 *
 * <p>Las validaciones replican las restricciones de la tabla: {@code modulo} con el CHECK
 * {@code ^M-[0-9]{2}$}, y {@code accion} y {@code entidad} obligatorias con su longitud máxima.
 * {@code ligaId} es obligatoria por decisión del equipo (H-02.2), aunque la columna admite nulos.
 *
 * @param ligaId    liga de la entidad que se modifica; la sesión no la trae
 * @param modulo    módulo del ERS, p. ej. {@code M-08}
 * @param accion    acción realizada, p. ej. {@code APROBAR_CEDULA}
 * @param entidad   tabla o recurso afectado, p. ej. {@code cedulas}
 * @param entidadId identificador del registro afectado; opcional
 * @param motivo    opcional: si viene vacío se usa el fijado con {@code MotivoOperacion}
 * @param detalle   datos adicionales en JSON; opcional y <b>sin datos personales</b>
 */
public record EventoAuditoria(UUID ligaId, String modulo, String accion, String entidad, String entidadId,
                              String motivo, Map<String, Object> detalle) {

    private static final Pattern MODULO = Pattern.compile("^M-[0-9]{2}$");
    private static final int LONGITUD_ACCION = 80;
    private static final int LONGITUD_ENTIDAD = 60;

    public EventoAuditoria {
        Objects.requireNonNull(ligaId, "ligaId es obligatorio");
        Objects.requireNonNull(modulo, "modulo es obligatorio");
        if (!MODULO.matcher(modulo).matches()) {
            throw new IllegalArgumentException("modulo debe tener el formato M-xx: " + modulo);
        }
        accion = obligatorio(accion, "accion", LONGITUD_ACCION);
        entidad = obligatorio(entidad, "entidad", LONGITUD_ENTIDAD);
        motivo = motivo == null || motivo.isBlank() ? null : motivo.strip();
        // No Map.copyOf: rechaza valores nulos, que en JSON son válidos.
        detalle = detalle == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(detalle));
    }

    private static String obligatorio(String valor, String campo, int longitudMaxima) {
        Objects.requireNonNull(valor, campo + " es obligatorio");
        if (valor.isBlank() || valor.length() > longitudMaxima) {
            throw new IllegalArgumentException(
                    campo + " no puede estar vacío ni pasar de " + longitudMaxima + " caracteres");
        }
        return valor;
    }
}
