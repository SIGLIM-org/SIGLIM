package mx.tecnm.morelia.siglim.seguridad;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rutas que existen solo en las pruebas del arnés de H-02.3 ({@link ArnesAutorizacionEndpointTest}).
 * No forman parte de la API.
 *
 * <p>Las tres primeras siguen el patrón correcto (controlador sin reglas que delega en el servicio).
 * Las dos últimas están <b>mal a propósito</b>, para comprobar que el arnés las detecta: una traga la
 * denegación del servicio y otra queda en la ruta pública sin exigir sesión.
 */
@RestController
class EndpointsArnesPruebaController {

    /** Servicio ficticio; en las pruebas es un {@code @MockitoBean}. */
    interface ServicioArnesPrueba {

        Recurso consultar(UUID id);

        Recurso crear(CrearRecursoRequest solicitud);

        void cerrar(UUID id);
    }

    /**
     * Existe para que {@code SiglimApplicationTest} ({@code @SpringBootTest}) pueda crear este
     * controlador, que el escaneo de componentes también encuentra allí. En {@code @WebMvcTest} la
     * reemplaza el {@code @MockitoBean}.
     */
    @Component
    static class ServicioArnesPruebaSinImplementar implements ServicioArnesPrueba {

        @Override
        public Recurso consultar(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Recurso crear(CrearRecursoRequest solicitud) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cerrar(UUID id) {
            throw new UnsupportedOperationException();
        }
    }

    record Recurso(UUID id, String nombre) {
    }

    record CrearRecursoRequest(@NotBlank String nombre) {
    }

    private final ServicioArnesPrueba servicio;

    EndpointsArnesPruebaController(ServicioArnesPrueba servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/api/v1/prueba-arnes/recursos/{id}")
    Recurso consultar(@PathVariable UUID id) {
        return servicio.consultar(id);
    }

    @PostMapping("/api/v1/prueba-arnes/recursos")
    @ResponseStatus(HttpStatus.CREATED)
    Recurso crear(@Valid @RequestBody CrearRecursoRequest solicitud) {
        return servicio.crear(solicitud);
    }

    @PostMapping("/api/v1/prueba-arnes/recursos/{id}/cerrar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cerrar(@PathVariable UUID id) {
        servicio.cerrar(id);
    }

    /** MAL: convierte cualquier denegación en un 200 con datos «vacíos». */
    @GetMapping("/api/v1/prueba-arnes/traga-denegacion/{id}")
    Recurso tragaDenegacion(@PathVariable UUID id) {
        try {
            return servicio.consultar(id);
        } catch (RuntimeException e) {
            return new Recurso(id, "sin datos");
        }
    }

    /** MAL: bajo {@code /api/v1/publico/**} un GET no exige sesión. */
    @GetMapping("/api/v1/publico/prueba-arnes/{id}")
    Recurso publicoSinSesion(@PathVariable UUID id) {
        return servicio.consultar(id);
    }
}
