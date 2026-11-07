package mx.tecnm.morelia.siglim.tesoreria;

import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Pagos de un cobro (convenciones de API §6.13). Sin reglas de negocio: el comprobante llega como
 * bytes al servicio, que valida, autoriza y lo sube a S3.
 *
 * <p>Un archivo mayor que {@code spring.servlet.multipart.max-file-size} no llega aquí: Spring lo
 * rechaza al leer la petición y el manejador global responde {@code 413 ARCHIVO_DEMASIADO_GRANDE}.
 *
 * <p>Refs: H-31.1, RF-139, RF-140
 */
@RestController
@RequestMapping("/api/v1/cobros/{cobroId}/pagos")
class PagoController {

    private final PagoService servicio;

    PagoController(PagoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<PagoDto> registrar(@PathVariable UUID cobroId,
            @Valid @ModelAttribute RegistrarPagoRequest peticion,
            @RequestPart("archivo") MultipartFile archivo) {
        PagoDto pago = servicio.registrar(cobroId, peticion, bytes(archivo));
        return ResponseEntity.created(URI.create("/api/v1/cobros/" + cobroId + "/pagos")).body(pago);
    }

    @GetMapping
    HistorialPagosDto historial(@PathVariable UUID cobroId) {
        return servicio.historial(cobroId);
    }

    private static byte[] bytes(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el comprobante de la petición", e);
        }
    }
}
