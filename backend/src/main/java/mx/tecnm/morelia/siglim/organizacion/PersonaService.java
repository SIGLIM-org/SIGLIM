package mx.tecnm.morelia.siglim.organizacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identidad única de las personas por CURP y tutela de los menores (RF-010, RF-011, RF-106).
 *
 * <ul>
 *   <li><b>Identidad global (DEC-78).</b> La CURP se busca con {@code persona_por_curp}, que ve a
 *       las personas de cualquier club: así una Directiva reutiliza a quien ya estuvo en otro club
 *       en lugar de duplicarlo, y un alta con una CURP existente responde {@code 409 RF-010} con el
 *       {@code personaId}. El permiso se comprueba en cualquier ámbito con
 *       {@code autorizacion.alcance}, porque la persona todavía no pertenece a un club.</li>
 *   <li><b>Edición (DEC-77, DEC-83).</b> Correo y teléfono se editan con
 *       {@code organizacion.persona:EDITAR} en cualquier ámbito; CURP, nombre, apellidos y fecha de
 *       nacimiento exigen ese permiso en toda la liga (lo corrige el Secretario de la liga). La
 *       persona debe ser visible por RLS; si no, {@code 404}.</li>
 *   <li><b>Tutela (DEC-75, DEC-79, DEC-82).</b> Vincula por CURP a un menor con un tutor adulto; la
 *       cuenta del tutor no se exige. Nadie se registra a sí mismo como tutor ({@code 403 VAL-022}).</li>
 *   <li><b>Auditoría (RF-093).</b> {@code REGISTRAR_PERSONA}, {@code EDITAR_PERSONA} (solo los
 *       nombres de los campos cambiados) y {@code REGISTRAR_TUTELA}, en M-03 y sin datos personales.</li>
 *   <li><b>Confidencialidad.</b> Las respuestas son datos personales: el controlador las marca
 *       {@code Cache-Control: no-store} (RNF-012).</li>
 * </ul>
 *
 * <p>Refs: H-22.2, H-09.1, H-09.3, RF-010, RF-011, RF-106, VAL-022, VAL-025, DEC-10, DEC-62, DEC-75,
 * DEC-77, DEC-78, DEC-79, DEC-82, DEC-83, DEC-84, DEC-104, RNF-012
 */
@Service
public class PersonaService {

    private static final String CONSULTAR = "organizacion.persona:CONSULTAR";
    private static final String EDITAR = "organizacion.persona:EDITAR";
    private static final String MODULO = "M-03";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    /** Mismo formato que ck_personas_curp (DEC-10). */
    private static final Pattern FORMATO_CURP =
            Pattern.compile("^[A-Z][AEIOUX][A-Z]{2}[0-9]{6}[HMX][A-Z]{5}[0-9A-Z][0-9]$");
    /** Mismo patrón que {@code ck_personas_correo}. */
    private static final Pattern FORMATO_CORREO =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", Pattern.CASE_INSENSITIVE);
    private static final int LONGITUD_NOMBRE = 100;
    private static final int LONGITUD_CORREO = 254;
    private static final int LONGITUD_TELEFONO = 20;
    private static final int LONGITUD_PARENTESCO = 40;

    private final PersonaRepository personas;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final Clock reloj;
    private final UUID ligaId;

    PersonaService(PersonaRepository personas, Autorizacion autorizacion, AuditoriaService auditoria,
            Clock reloj, SiglimPropiedades propiedades) {
        this.personas = personas;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * La persona con esa CURP, de cualquier club, o {@code 404 NO_ENCONTRADO} (DEC-78), con
     * {@code esMenor} calculado con {@code es_menor} y la fecha de hoy en {@code America/Mexico_City},
     * la misma que usa el alta de un registro para VAL-025 (DEC-89, DEC-104). No expone la fecha de
     * nacimiento.
     *
     * <p>Refs: H-22.2, H-09.3, DEC-78, DEC-104, VAL-025
     */
    @Transactional(readOnly = true)
    public PersonaDto buscarPorCurp(String curp) {
        autorizacion.alcance(CONSULTAR);
        String normalizada = curpValida(curp);
        PersonaDto persona = personas.buscarPorCurp(normalizada)
                .orElseThrow(PersonaService::curpSinPersona);
        boolean menor = personas.esMenor(persona.id(), hoy())
                .orElseThrow(PersonaService::curpSinPersona);
        return persona.conMinoria(menor);
    }

    /**
     * Registra a una persona nueva (DEC-62, DEC-84).
     *
     * <p>Orden: permiso ({@code 403 VAL-015}) → datos ({@code 400 VALIDACION}) → CURP ya registrada en
     * cualquier club ({@code 409 RF-010} con {@code personaId}).
     */
    @Transactional
    public PersonaDetalleDto registrar(CrearPersonaRequest peticion) {
        autorizacion.alcance(EDITAR);
        if (peticion == null) {
            throw invalida("Envía los datos de la persona.");
        }
        PersonaDetalleDto nueva = new PersonaDetalleDto(null,
                curpValida(peticion.curp()),
                obligatorio(peticion.nombre(), "nombre"),
                obligatorio(peticion.apellidoPaterno(), "apellido paterno"),
                opcional(peticion.apellidoMaterno(), "apellido materno", LONGITUD_NOMBRE),
                fechaNacimientoValida(peticion.fechaNacimiento()),
                correoValido(peticion.correo()),
                opcional(peticion.telefono(), "teléfono", LONGITUD_TELEFONO));

        personas.buscarPorCurp(nueva.curp()).ifPresent(existente -> {
            throw yaRegistrada(existente.id());
        });
        UUID id = personas.insertar(nueva, UsuarioActual.idRequerido())
                .orElseThrow(() -> yaRegistrada(personas.buscarPorCurp(nueva.curp())
                        .map(PersonaDto::id)
                        .orElseThrow(() -> new IllegalStateException("La CURP chocó y no se encontró"))));

        auditar("REGISTRAR_PERSONA", "personas", id, null);
        return detalle(id);
    }

    /**
     * Edita una persona; lo ausente conserva su valor (DEC-77, DEC-83). Sin cambios responde la
     * persona sin auditar.
     *
     * <p>Orden: permiso ({@code 403 VAL-015}) → cuerpo sin campos ({@code 400 VALIDACION}) → persona
     * visible ({@code 404}) → cambio de identidad sin el permiso en toda la liga ({@code 403 VAL-015})
     * → datos ({@code 400 VALIDACION}) → CURP de otra persona ({@code 409 DUPLICADO}).
     */
    @Transactional
    public PersonaDetalleDto actualizar(UUID id, ActualizarPersonaRequest peticion) {
        AlcanceAutorizacion alcance = autorizacion.alcance(EDITAR);
        if (peticion == null || peticion.sinCampos()) {
            throw invalida("Envía al menos un dato para cambiar.");
        }
        PersonaDetalleDto actual = personas.buscarDetalleParaActualizar(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Persona " + id));

        List<String> identidad = camposDeIdentidadCambiados(actual, peticion);
        if (!identidad.isEmpty() && !alcance.todaLaLiga()) {
            throw ReglaNegocioException.prohibida(ErroresTecnicos.SIN_PERMISO,
                    "La CURP, el nombre, los apellidos y la fecha de nacimiento los corrige el Secretario "
                            + "de la liga. Tú puedes cambiar el correo y el teléfono.");
        }

        PersonaDetalleDto nueva = new PersonaDetalleDto(id,
                peticion.curp() == null ? actual.curp() : curpValida(peticion.curp()),
                peticion.nombre() == null ? actual.nombre() : obligatorio(peticion.nombre(), "nombre"),
                peticion.apellidoPaterno() == null ? actual.apellidoPaterno()
                        : obligatorio(peticion.apellidoPaterno(), "apellido paterno"),
                peticion.apellidoMaterno() == null ? actual.apellidoMaterno()
                        : opcional(peticion.apellidoMaterno(), "apellido materno", LONGITUD_NOMBRE),
                peticion.fechaNacimiento() == null ? actual.fechaNacimiento()
                        : fechaNacimientoValida(peticion.fechaNacimiento()),
                peticion.correo() == null ? actual.correo() : correoValido(peticion.correo()),
                peticion.telefono() == null ? actual.telefono()
                        : opcional(peticion.telefono(), "teléfono", LONGITUD_TELEFONO));

        List<String> campos = camposCambiados(actual, nueva);
        if (campos.isEmpty()) {
            return actual;
        }
        if (!nueva.curp().equals(actual.curp())) {
            personas.buscarPorCurp(nueva.curp())
                    .filter(otra -> !otra.id().equals(id))
                    .ifPresent(otra -> {
                        throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                                "Ya hay otra persona registrada con esa CURP. Revisa la CURP antes de guardar.");
                    });
        }

        personas.actualizar(nueva);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("campos", campos);
        auditar("EDITAR_PERSONA", "personas", id, detalle);
        return detalle(id);
    }

    /**
     * Vincula a un menor con su tutor (RF-106, DEC-75, DEC-82). La cuenta del tutor no se exige: se
     * crea después con la invitación de H-03.
     *
     * <p>Orden: permiso ({@code 403 VAL-015}) → datos ({@code 400 VALIDACION}) → el menor existe
     * ({@code 404}) → el tutor no es quien hace la petición ({@code 403 VAL-022}) → la persona es menor
     * ({@code 422 RF-106}) → el tutor existe ({@code 422 REFERENCIA_INVALIDA}) → el tutor es adulto
     * ({@code 422 RF-106}) → no hay una tutela vigente igual ({@code 409 DUPLICADO}).
     */
    @Transactional
    public TutelaDto registrarTutela(UUID menorId, RegistrarTutelaRequest peticion) {
        autorizacion.alcance(EDITAR);
        if (peticion == null || peticion.tutorId() == null) {
            throw invalida("Indica quién es el tutor.");
        }
        String parentesco = textoObligatorio(peticion.parentesco(), "parentesco", LONGITUD_PARENTESCO);

        LocalDate hoy = hoy();
        boolean esMenor = personas.esMenor(menorId, hoy)
                .orElseThrow(() -> new RecursoNoEncontradoException("Persona " + menorId));
        if (personas.personaDelUsuario().filter(peticion.tutorId()::equals).isPresent()) {
            throw ReglaNegocioException.prohibida("VAL-022",
                    "No puedes registrarte como tutor desde tu propia cuenta. Pide a otro integrante de "
                            + "la directiva o a la liga que registre la tutela.");
        }
        if (!esMenor) {
            throw ReglaNegocioException.noProcesable("RF-106",
                    "Solo se registra un tutor para una persona menor de edad. Revisa la fecha de "
                            + "nacimiento de la persona.");
        }
        boolean tutorMenor = personas.esMenor(peticion.tutorId(), hoy)
                .orElseThrow(() -> ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                        "La persona indicada como tutor no está registrada. Regístrala primero con su CURP."));
        if (tutorMenor) {
            throw ReglaNegocioException.noProcesable("RF-106",
                    "El tutor debe ser mayor de edad. Indica a otra persona como tutor.");
        }
        if (personas.esTutorVigente(menorId, peticion.tutorId())) {
            throw tutelaDuplicada();
        }
        TutelaDto tutela = personas.insertarTutela(menorId, peticion.tutorId(), parentesco,
                        UsuarioActual.idRequerido())
                .orElseThrow(PersonaService::tutelaDuplicada);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("menorId", menorId.toString());
        detalle.put("tutorId", peticion.tutorId().toString());
        auditar("REGISTRAR_TUTELA", "tutelas", tutela.id(), detalle);
        return tutela;
    }

    // ── Validaciones ────────────────────────────────────────────────────────────────────────────

    private static String curpValida(String curp) {
        String limpia = curp == null ? "" : curp.trim().toUpperCase(Locale.ROOT);
        if (!FORMATO_CURP.matcher(limpia).matches()) {
            throw invalida("La CURP debe tener 18 caracteres y el formato oficial.");
        }
        return limpia;
    }

    private static String obligatorio(String valor, String campo) {
        return textoObligatorio(valor, campo, LONGITUD_NOMBRE);
    }

    private static String textoObligatorio(String valor, String campo, int longitudMaxima) {
        String limpio = valor == null ? "" : valor.strip();
        if (limpio.isEmpty()) {
            throw invalida("Escribe el " + campo + ".");
        }
        if (limpio.length() > longitudMaxima) {
            throw invalida("El " + campo + " admite hasta " + longitudMaxima + " caracteres.");
        }
        return limpio;
    }

    /** Opcional: vacío o solo espacios se guarda como {@code NULL}. */
    private static String opcional(String valor, String campo, int longitudMaxima) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.strip();
        if (limpio.length() > longitudMaxima) {
            throw invalida("El " + campo + " admite hasta " + longitudMaxima + " caracteres.");
        }
        return limpio;
    }

    private static String correoValido(String correo) {
        String limpio = opcional(correo, "correo", LONGITUD_CORREO);
        if (limpio != null && !FORMATO_CORREO.matcher(limpio).matches()) {
            throw invalida("El correo no tiene un formato válido. Revísalo, por ejemplo: nombre@dominio.mx.");
        }
        return limpio;
    }

    private LocalDate fechaNacimientoValida(LocalDate fecha) {
        if (fecha == null) {
            throw invalida("Escribe la fecha de nacimiento.");
        }
        if (fecha.isAfter(hoy())) {
            throw invalida("La fecha de nacimiento no puede ser futura.");
        }
        return fecha;
    }

    /** Campos de identidad que la petición cambia, comparados ya recortados (DEC-77). */
    private static List<String> camposDeIdentidadCambiados(PersonaDetalleDto actual, ActualizarPersonaRequest peticion) {
        List<String> campos = new ArrayList<>();
        if (peticion.curp() != null && !peticion.curp().trim().toUpperCase(Locale.ROOT).equals(actual.curp())) {
            campos.add("curp");
        }
        if (peticion.nombre() != null && !peticion.nombre().strip().equals(actual.nombre())) {
            campos.add("nombre");
        }
        if (peticion.apellidoPaterno() != null && !peticion.apellidoPaterno().strip().equals(actual.apellidoPaterno())) {
            campos.add("apellidoPaterno");
        }
        if (peticion.apellidoMaterno() != null
                && !Objects.equals(vacioANulo(peticion.apellidoMaterno()), actual.apellidoMaterno())) {
            campos.add("apellidoMaterno");
        }
        if (peticion.fechaNacimiento() != null && !peticion.fechaNacimiento().equals(actual.fechaNacimiento())) {
            campos.add("fechaNacimiento");
        }
        return campos;
    }

    private static List<String> camposCambiados(PersonaDetalleDto actual, PersonaDetalleDto nueva) {
        List<String> campos = new ArrayList<>();
        agregarSiCambia(campos, "curp", actual.curp(), nueva.curp());
        agregarSiCambia(campos, "nombre", actual.nombre(), nueva.nombre());
        agregarSiCambia(campos, "apellidoPaterno", actual.apellidoPaterno(), nueva.apellidoPaterno());
        agregarSiCambia(campos, "apellidoMaterno", actual.apellidoMaterno(), nueva.apellidoMaterno());
        agregarSiCambia(campos, "fechaNacimiento", actual.fechaNacimiento(), nueva.fechaNacimiento());
        agregarSiCambia(campos, "correo", actual.correo(), nueva.correo());
        agregarSiCambia(campos, "telefono", actual.telefono(), nueva.telefono());
        return campos;
    }

    private static void agregarSiCambia(List<String> campos, String campo, Object antes, Object despues) {
        if (!Objects.equals(antes, despues)) {
            campos.add(campo);
        }
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    // ── Apoyo ──────────────────────────────────────────────────────────────────────────────────

    private LocalDate hoy() {
        return LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO));
    }

    private PersonaDetalleDto detalle(UUID id) {
        return personas.buscarDetalle(id)
                .orElseThrow(() -> new IllegalStateException("La persona " + id + " no es visible tras guardarla"));
    }

    private void auditar(String accion, String entidad, UUID id, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, entidad, id.toString(), null, detalle));
    }

    private static ReglaNegocioException yaRegistrada(UUID personaId) {
        return ReglaNegocioException.conflicto("RF-010",
                        "Esta persona ya está registrada con esa CURP. Búscala por su CURP y reutiliza su "
                                + "registro en lugar de crear otro.")
                .con("personaId", personaId);
    }

    private static RecursoNoEncontradoException curpSinPersona() {
        return new RecursoNoEncontradoException("Persona con la CURP consultada");
    }

    private static ReglaNegocioException tutelaDuplicada() {
        return ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                "Ese tutor ya está registrado para este menor. No hace falta registrarlo otra vez.");
    }

    private static ReglaNegocioException invalida(String mensaje) {
        return ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, mensaje);
    }
}
