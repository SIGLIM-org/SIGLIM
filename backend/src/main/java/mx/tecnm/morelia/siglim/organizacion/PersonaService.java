package mx.tecnm.morelia.siglim.organizacion;

import java.util.Locale;
import java.util.regex.Pattern;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta una persona por su CURP para reutilizar su identidad (RF-010, RF-011).
 *
 * <ul>
 *   <li><b>Permiso.</b> {@code organizacion.persona:CONSULTAR} en cualquier ámbito: la identidad es
 *       global y no pertenece a un club; se comprueba con {@code autorizacion.alcance}, igual que
 *       los catálogos de la liga. La consulta exige la CURP exacta y no permite listar personas.</li>
 *   <li><b>Formato.</b> Se normaliza a mayúsculas y se valida con el mismo patrón de la base
 *       (DEC-10). Una CURP mal formada responde {@code 400 VALIDACION} sin consultar la tabla.</li>
 *   <li><b>Confidencialidad.</b> La respuesta es un dato personal: el controlador la marca
 *       {@code Cache-Control: no-store} (RNF-012).</li>
 * </ul>
 *
 * <p>Refs: H-22.2, RF-010, RF-011, DEC-10, RNF-012
 */
@Service
public class PersonaService {

    private static final String CONSULTAR = "organizacion.persona:CONSULTAR";

    /** Mismo formato que {@code ck_personas_curp} de la migración inicial. */
    private static final Pattern FORMATO_CURP =
            Pattern.compile("^[A-Z][AEIOUX][A-Z]{2}[0-9]{6}[HM][A-Z]{5}[0-9A-Z][0-9]$");

    private final PersonaRepository personas;
    private final Autorizacion autorizacion;

    PersonaService(PersonaRepository personas, Autorizacion autorizacion) {
        this.personas = personas;
        this.autorizacion = autorizacion;
    }

    /** La persona con esa CURP o {@code 404 NO_ENCONTRADO} (VAL-024). */
    @Transactional(readOnly = true)
    public PersonaDto buscarPorCurp(String curp) {
        autorizacion.alcance(CONSULTAR);
        String normalizada = normalizar(curp);
        return personas.buscarPorCurp(normalizada)
                .orElseThrow(() -> new RecursoNoEncontradoException("Persona con la CURP consultada"));
    }

    private static String normalizar(String curp) {
        String limpia = curp == null ? "" : curp.trim().toUpperCase(Locale.ROOT);
        if (!FORMATO_CURP.matcher(limpia).matches()) {
            throw ReglaNegocioException.invalida("VALIDACION",
                    "La CURP debe tener 18 caracteres y el formato oficial.");
        }
        return limpia;
    }
}
