package mx.tecnm.morelia.siglim.organizacion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta y consulta de los clubes afiliados a la liga.
 *
 * <ul>
 *   <li><b>Ámbito (ADR-0007).</b> Un club se autoriza con {@link ObjetivoAutorizacion#deClubes} de
 *       sí mismo: la directiva de un club solo alcanza el suyo y uno ajeno responde 404 igual que uno
 *       inexistente (VAL-024). El listado filtra con el alcance del permiso y cuenta con la misma
 *       condición. El alta es de toda la liga ({@link ObjetivoAutorizacion#deLiga()}).</li>
 *   <li><b>Nombre único en la liga.</b> Un nombre repetido responde {@code 409 DUPLICADO} y nombra el
 *       club; el índice {@code UNIQUE (liga_id, nombre)} es la última garantía.</li>
 *   <li><b>Sin edición ni borrado (RN-005, RN-041, DEC-49).</b> El único dato editable sería el
 *       nombre, y su cambio crea un club nuevo y cierra el anterior: es la acción
 *       {@code POST /clubes/{id}/cambio-nombre} de B-04, fuera de esta historia. No existe
 *       {@code PATCH} ni {@code DELETE}.</li>
 * </ul>
 *
 * <p>Refs: H-06.1, RF-008, RN-005, RN-041, VAL-024, DEC-15, DEC-49, ADR-0007
 */
@Service
public class ClubService {

    private static final String CONSULTAR = "organizacion.club:CONSULTAR";
    private static final String EDITAR = "organizacion.club:EDITAR";
    private static final String MODULO = "M-03";
    private static final String ENTIDAD = "clubes";

    private final ClubRepository clubes;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    ClubService(ClubRepository clubes, Autorizacion autorizacion, AuditoriaService auditoria,
            SiglimPropiedades propiedades) {
        this.clubes = clubes;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
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
     * @throws ReglaNegocioException 409 {@code DUPLICADO} si ya existe un club con ese nombre
     */
    @Transactional
    public ClubDto crear(CrearClubRequest peticion) {
        autorizacion.exigir(EDITAR, ObjetivoAutorizacion.deLiga());
        String nombre = nombreValido(peticion.nombre());
        if (clubes.existeNombre(ligaId, nombre)) {
            throw ReglaNegocioException.conflicto(ErroresTecnicos.DUPLICADO,
                    "Ya existe un club llamado «" + nombre + "» en la liga.");
        }

        UUID id = clubes.insertar(ligaId, nombre);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estado", EstadoClub.ACTIVO.name());
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "REGISTRAR_CLUB", ENTIDAD, id.toString(), null,
                detalle));
        return buscar(id);
    }

    private ClubDto buscar(UUID id) {
        return clubes.buscar(ligaId, id).orElseThrow(() -> new RecursoNoEncontradoException("Club " + id));
    }

    private static String nombreValido(String nombre) {
        String texto = nombre == null ? "" : nombre.strip();
        if (texto.isEmpty()) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION, "El nombre del club es obligatorio.");
        }
        return texto;
    }
}
