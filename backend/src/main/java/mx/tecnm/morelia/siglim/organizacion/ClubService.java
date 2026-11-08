package mx.tecnm.morelia.siglim.organizacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.TrasladoAsignacionesClub;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, consulta y cambio de nombre de los clubes afiliados a la liga.
 *
 * <ul>
 *   <li><b>Ámbito (ADR-0007).</b> Un club se autoriza con {@link ObjetivoAutorizacion#deClubes} de
 *       sí mismo: la directiva de un club solo alcanza el suyo y uno ajeno responde 404 igual que uno
 *       inexistente (VAL-024). El listado filtra con el alcance del permiso y cuenta con la misma
 *       condición. El alta es de toda la liga ({@link ObjetivoAutorizacion#deLiga()}).</li>
 *   <li><b>Nombre único en la liga.</b> Un nombre repetido responde {@code 409 DUPLICADO} y nombra el
 *       club; el índice {@code UNIQUE (liga_id, nombre)} es la última garantía.</li>
 *   <li><b>Sin edición ni borrado (RN-005).</b> No existe {@code PATCH} ni {@code DELETE}. El único
 *       dato que cambia es el nombre, con la acción {@code POST /clubes/{id}/cambio-nombre}.</li>
 *   <li><b>Cambio de nombre (B-04, RF-105, RN-041, DEC-15, DEC-49, DEC-61).</b> Crea un club nuevo
 *       {@code ACTIVO} con {@code club_anterior_id} y cierra el anterior ({@code CERRADO}, fecha del
 *       día en la zona de negocio y motivo). Es de toda la liga y se registra entre temporadas: se
 *       rechaza con {@code 409 RN-041} si el club tiene equipos inscritos (no dados de baja) en una
 *       temporada {@code ACTIVA} o en {@code CONFIGURACION}, o cobros {@code PENDIENTE} o
 *       {@code VENCIDO}, que se quedan en el club anterior (RN-005, RN-052). Las asignaciones de rol
 *       con ámbito del club se trasladan al nuevo (revocar y recrear); la directiva no se traslada
 *       (historial, RF-102). Todo ocurre en una transacción con el club anterior bloqueado
 *       ({@code FOR UPDATE}); el alta de equipos lo lee con {@code FOR SHARE}.</li>
 * </ul>
 *
 * <p>Refs: H-06.1, B-04, RF-008, RF-105, RN-005, RN-041, RN-052, VAL-024, DEC-15, DEC-49, DEC-61,
 * ADR-0007
 */
@Service
public class ClubService {

    private static final String CONSULTAR = "organizacion.club:CONSULTAR";
    private static final String EDITAR = "organizacion.club:EDITAR";
    private static final String MODULO = "M-03";
    private static final String ENTIDAD = "clubes";
    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private final ClubRepository clubes;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final ConsultaAdeudosClub adeudos;
    private final TrasladoAsignacionesClub trasladoAsignaciones;
    private final Clock reloj;
    private final UUID ligaId;

    ClubService(ClubRepository clubes, Autorizacion autorizacion, AuditoriaService auditoria,
            ConsultaAdeudosClub adeudos, TrasladoAsignacionesClub trasladoAsignaciones, Clock reloj,
            SiglimPropiedades propiedades) {
        this.clubes = clubes;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.adeudos = adeudos;
        this.trasladoAsignaciones = trasladoAsignaciones;
        this.reloj = reloj;
        this.ligaId = propiedades.ligaId();
    }

    /** Listado por nombre, filtrado por ámbito en el SQL y en el total (RF-008, ADR-0007). */
    @Transactional(readOnly = true)
    public Pagina<ClubDto> listar(FiltroClubes filtro) {
        AlcanceAutorizacion alcance = autorizacion.alcance(CONSULTAR);
        filtro.validar();
        return Pagina.de(clubes.listar(ligaId, filtro, alcance), filtro.pagina(), filtro.tamano(),
                clubes.contar(ligaId, filtro, alcance));
    }

    /** Detalle de un club; fuera de ámbito responde 404 (RF-008, VAL-024). */
    @Transactional(readOnly = true)
    public ClubDto consultar(UUID id) {
        ClubDto club = buscar(id);
        autorizacion.exigir(CONSULTAR, ObjetivoAutorizacion.deClubes(id));
        return club;
    }

    /**
     * Registra un club {@code ACTIVO} en la liga configurada (RF-008).
     *
     * @throws ReglaNegocioException 409 {@code DUPLICADO} si ya existe un club con ese nombre, también
     *                               si otra petición lo registró al mismo tiempo
     */
    @Transactional
    public ClubDto crear(CrearClubRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        String nombre = nombreValido(peticion.nombre());
        exigirNombreLibre(nombre);

        UUID id = insertarSinDuplicado(nombre, () -> clubes.insertar(ligaId, nombre));

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estado", EstadoClub.ACTIVO.name());
        auditar("REGISTRAR_CLUB", id, null, detalle);
        return buscar(id);
    }

    /**
     * Cambia el nombre del club: crea un club nuevo {@code ACTIVO} que apunta al anterior y cierra el
     * anterior con el motivo (RF-105, RN-041, DEC-15). Responde el club nuevo.
     *
     * <p>Orden: permiso de liga (403, antes de buscar para no revelar la existencia) → club de la liga
     * con candado (404) → club no cerrado ({@code 409 RN-041}) → motivo de al menos 10 caracteres
     * ({@code 400 RN-021}) → nombre ({@code 400 VALIDACION}) → sin equipos inscritos en una temporada
     * activa o en configuración ({@code 409 RN-041}, DEC-49, DEC-61) → sin cobros pendientes ni
     * vencidos ({@code 409 RN-041}, DEC-61) → nombre distinto del actual y libre en la liga, contando
     * los clubes cerrados ({@code 409 DUPLICADO}).
     *
     * <p>Efectos, en la misma transacción: club nuevo, cierre del anterior, traslado de las
     * asignaciones de ámbito {@code CLUB} y auditoría {@code CERRAR_CLUB} y {@code REGISTRAR_CLUB}.
     */
    @Transactional
    public ClubDto cambiarNombre(UUID id, CambioNombreClubRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        ClubDto anterior = clubes.buscarParaActualizar(ligaId, id).orElseThrow(() -> noExiste(id));
        if (anterior.estado() == EstadoClub.CERRADO) {
            throw ReglaNegocioException.conflicto("RN-041",
                    "El club «" + anterior.nombre() + "» ya está cerrado: no puede cambiar de nombre.");
        }
        String motivo = MotivoTransicion.exigir(peticion == null ? null : peticion.motivo(),
                "cambiar el nombre del club");
        String nombre = nombreValido(peticion == null ? null : peticion.nombre());
        long inscritos = clubes.contarEquiposInscritos(ligaId, id);
        if (inscritos > 0) {
            throw ReglaNegocioException.conflicto("RN-041",
                    "El cambio de nombre se registra entre temporadas, sin equipos inscritos en la temporada en"
                            + " curso o en configuración; el club «" + anterior.nombre() + "» tiene " + inscritos
                            + (inscritos == 1 ? " equipo inscrito." : " equipos inscritos."));
        }
        if (adeudos.tieneCobrosPendientesOVencidos(id)) {
            throw ReglaNegocioException.conflicto("RN-041",
                    "El club tiene cobros pendientes o vencidos: liquídalos antes de registrar el cambio de nombre.");
        }
        if (nombre.equals(anterior.nombre())) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "«" + nombre + "» ya es el nombre de este club.");
        }
        exigirNombreLibre(nombre);

        UUID nuevoId = insertarSinDuplicado(nombre, () -> clubes.insertarPorCambioNombre(ligaId, nombre, id));
        LocalDate cerradoEn = LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO));
        clubes.cerrar(id, cerradoEn, motivo);
        trasladoAsignaciones.trasladar(id, nuevoId);

        Map<String, Object> cierre = new LinkedHashMap<>();
        cierre.put("estado", EstadoClub.CERRADO.name());
        cierre.put("clubNuevoId", nuevoId.toString());
        cierre.put("causa", "CAMBIO_NOMBRE");
        auditar("CERRAR_CLUB", id, motivo, cierre);

        Map<String, Object> alta = new LinkedHashMap<>();
        alta.put("estado", EstadoClub.ACTIVO.name());
        alta.put("clubAnteriorId", id.toString());
        auditar("REGISTRAR_CLUB", nuevoId, null, alta);
        return buscar(nuevoId);
    }

    private ClubDto buscar(UUID id) {
        return clubes.buscar(ligaId, id).orElseThrow(() -> noExiste(id));
    }

    private static RecursoNoEncontradoException noExiste(UUID id) {
        return new RecursoNoEncontradoException("Club " + id);
    }

    /** Nombre libre en la liga, contando los clubes cerrados (comparación exacta, DEC-61). */
    private void exigirNombreLibre(String nombre) {
        if (clubes.existeNombre(ligaId, nombre)) {
            throw duplicado(nombre);
        }
    }

    /**
     * Inserta y traduce la carrera con {@code UNIQUE (liga_id, nombre)} a {@code 409 DUPLICADO} con el
     * mismo mensaje que la validación previa (sin ella, el traductor global daría un mensaje genérico).
     */
    private static UUID insertarSinDuplicado(String nombre, Supplier<UUID> insercion) {
        try {
            return insercion.get();
        } catch (DuplicateKeyException carrera) {
            throw duplicado(nombre);
        }
    }

    private static ReglaNegocioException duplicado(String nombre) {
        return ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                "Ya existe un club llamado «" + nombre + "» en la liga.");
    }

    // ── Auditoría (RF-093): solo identificadores y estados, nunca datos personales ──────────────

    private void auditar(String accion, UUID entidadId, String motivo, Map<String, Object> detalle) {
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, accion, ENTIDAD, entidadId.toString(), motivo,
                detalle));
    }

    private static String nombreValido(String nombre) {
        String texto = nombre == null ? "" : nombre.strip();
        if (texto.isEmpty()) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, "El nombre del club es obligatorio.");
        }
        return texto;
    }
}
