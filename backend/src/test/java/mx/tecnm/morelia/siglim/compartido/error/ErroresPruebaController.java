package mx.tecnm.morelia.siglim.compartido.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Rutas que solo existen en las pruebas para lanzar cada tipo de error. No forman parte de la API. */
@RestController
@RequestMapping("/api/v1/prueba/errores")
class ErroresPruebaController {

    record Peticion(@NotBlank(message = "El nombre es obligatorio") String nombre,
                    @Size(min = 10, message = "El motivo debe tener al menos 10 caracteres") String motivo) {
    }

    @GetMapping("/regla")
    String regla() {
        throw ReglaNegocioException.conflicto("VAL-004",
                "La cancha «Unidad Deportiva Morelos 2» ya tiene el partido Halcones vs. Toros de 10:00 a 11:45.");
    }

    @GetMapping("/regla-aceptable")
    String reglaAceptable() {
        throw ReglaNegocioException.conflicto("RF-030", "El bloqueo afecta a 1 partido.")
                .con("aceptable", true)
                .con("partidosAfectados", List.of(Map.of("id", "partido-1")));
    }

    @GetMapping("/no-encontrado")
    String noEncontrado() {
        throw new RecursoNoEncontradoException("temporada 3f2a de prueba");
    }

    @PostMapping("/validacion")
    String validacion(@Valid @RequestBody Peticion peticion) {
        return "ok";
    }

    @GetMapping("/inesperado")
    String inesperado() {
        throw new IllegalStateException("detalle interno que nunca debe salir: SELECT * FROM siglim.perfiles");
    }

    @GetMapping("/archivo/grande")
    String archivoGrande() {
        throw ArchivoRechazadoException.demasiadoGrande(DataSize.ofMegabytes(5));
    }

    @GetMapping("/archivo/tipo")
    String archivoTipo() {
        throw ArchivoRechazadoException.tipoNoPermitido();
    }

    /** Lo que lanza Spring cuando la petición multipart excede {@code spring.servlet.multipart}. */
    @GetMapping("/archivo/limite-spring")
    String archivoLimiteSpring() {
        throw new MaxUploadSizeExceededException(DataSize.ofMegabytes(5).toBytes());
    }

    @GetMapping("/archivo/s3-caido")
    String archivoS3Caido() {
        throw new ServicioNoDisponibleException("S3 no respondió (prueba)", new IllegalStateException("causa"));
    }

    /** Simula el error que Spring entrega cuando PostgreSQL rechaza la operación. */
    @GetMapping("/bd/{sqlState}")
    String baseDeDatosLectura(@PathVariable String sqlState, @RequestParam(required = false) String hint,
            @RequestParam(required = false) String restriccion) throws Exception {
        throw errorBd(sqlState, hint, restriccion);
    }

    @PostMapping("/bd/{sqlState}")
    String baseDeDatosEscritura(@PathVariable String sqlState) {
        throw errorBd(sqlState, null, null);
    }

    private static DataIntegrityViolationException errorBd(String sqlState, String hint, String restriccion) {
        StringBuilder servidor = new StringBuilder("SERROR\0C").append(sqlState).append('\0');
        servidor.append('M').append(hint == null ? "mensaje del servidor" : hint + ": Mensaje del trigger.").append('\0');
        if (hint != null) {
            servidor.append('H').append(hint).append('\0');
        }
        if (restriccion != null) {
            servidor.append('n').append(restriccion).append('\0');
        }
        return new DataIntegrityViolationException("error de prueba",
                new PSQLException(new ServerErrorMessage(servidor.toString())));
    }
}
