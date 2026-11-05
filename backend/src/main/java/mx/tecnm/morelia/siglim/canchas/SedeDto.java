package mx.tecnm.morelia.siglim.canchas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/** Sede o unidad deportiva que agrupa canchas (RF-028). Los campos opcionales vacíos se omiten. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SedeDto(UUID id, String nombre, String direccion) {
}
