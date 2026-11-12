package mx.tecnm.morelia.siglim.registros;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.EnlaceTemporal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Documentos de un registro de jugador (convenciones de API §6.4 y §7). Sin reglas de negocio: el
 * archivo llega como bytes a {@link DocumentoRegistroService}, que autoriza, valida y lo sube a S3.
 *
 * <ul>
 *   <li>El archivo y {@code tipoDocumentoId} son opcionales aquí ({@code required = false}): los
 *       obligatorios los valida el servicio <b>después</b> del permiso (DEC-91). Un
 *       {@code tipoDocumentoId} que no es número no se puede convertir y responde
 *       {@code 400 VALIDACION} antes del servicio.</li>
 *   <li>Un archivo mayor que {@code spring.servlet.multipart.max-file-size} no llega aquí: Spring lo
 *       rechaza al leer la petición y el manejador global responde {@code 413 ARCHIVO_DEMASIADO_GRANDE}.
 *       El nombre del archivo y su {@code Content-Type} declarado se ignoran.</li>
 *   <li>Las cargas responden {@code 201} sin {@code Location} porque no existe
 *       {@code GET /documentos/{id}}. Todas las respuestas llevan {@code Cache-Control: no-store}: el
 *       enlace es una URL prefirmada y los demás describen documentos personales (RNF-018).</li>
 * </ul>
 *
 * <p>Refs: H-10.1, RF-108, RF-109, RNF-018, DEC-91 a DEC-96
 */
@RestController
@RequestMapping("/api/v1")
class DocumentoRegistroController {

    private final DocumentoRegistroService servicio;

    DocumentoRegistroController(DocumentoRegistroService servicio) {
        this.servicio = servicio;
    }

    @PostMapping(path = "/registros-jugador/{id}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentoDto> cargar(@PathVariable UUID id,
            @RequestParam(name = "tipoDocumentoId", required = false) Long tipoDocumentoId,
            @RequestPart(name = "archivo", required = false) MultipartFile archivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(servicio.cargar(id, tipoDocumentoId, bytes(archivo)));
    }

    @PostMapping(path = "/documentos/{id}/reemplazo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentoDto> reemplazar(@PathVariable UUID id,
            @RequestPart(name = "archivo", required = false) MultipartFile archivo) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(servicio.reemplazar(id, bytes(archivo)));
    }

    @GetMapping("/registros-jugador/{id}/requisitos")
    ResponseEntity<RequisitosRegistroDto> requisitos(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.requisitos(id));
    }

    @GetMapping("/documentos/{id}/enlace")
    ResponseEntity<EnlaceTemporal> enlace(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.enlace(id));
    }

    @PostMapping("/documentos/{id}/validacion")
    ResponseEntity<DocumentoDto> validar(@PathVariable UUID id, @RequestBody ValidarDocumentoRequest peticion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.validar(id, peticion));
    }

    /** Bytes del archivo, o {@code null} si no vino la parte (el servicio responde 400 tras el permiso). */
    private static byte[] bytes(MultipartFile archivo) {
        if (archivo == null) {
            return null;
        }
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo de la petición", e);
        }
    }
}
