package mx.tecnm.morelia.siglim.auditoria;

import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la auditoría (convenciones de API §6.1). Solo lectura: cualquier otro método responde
 * {@code 405} desde el manejador global. Responde con {@code Cache-Control: no-store} porque contiene
 * nombres de usuarios. Sin reglas de negocio.
 *
 * <p>Refs: H-21.1, RF-134, RNF-012, RNF-015
 */
@RestController
@RequestMapping("/api/v1/auditoria")
class AuditoriaController {

    private final ConsultaAuditoriaService servicio;

    AuditoriaController(ConsultaAuditoriaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    ResponseEntity<Pagina<RegistroAuditoriaDto>> listar(@RequestParam(required = false) UUID perfilId,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamano,
            @RequestParam(defaultValue = "ocurridoEn,desc") String orden) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.listar(new FiltroAuditoria(perfilId, FiltroAuditoria.separarValores(modulo),
                        FiltroAuditoria.separarValores(accion), desde, hasta, pagina, tamano, orden)));
    }

    @GetMapping("/opciones-filtro")
    ResponseEntity<OpcionesFiltroAuditoriaDto> opcionesFiltro() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicio.opcionesFiltro());
    }
}
