package mx.tecnm.morelia.siglim.temporadas;

import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /api/v1/grupos/{id}}: solo se renombra (RF-005). Sin {@code nombre}, el
 * grupo se conserva y la respuesta es {@code 200} sin auditar, como en {@code PATCH /equipos/{id}}.
 * El grupo no cambia de categoría: sus equipos y jornadas cuelgan de él.
 */
public record ActualizarGrupoRequest(
        @Size(max = 60, message = "El nombre admite hasta 60 caracteres")
        String nombre) {
}
