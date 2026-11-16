package mx.tecnm.morelia.siglim.publicacion;

import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bandeja del Presidente: jornadas y comunicados en {@code REVISION} que esperan su dictamen
 * (RF-090, RF-121, UI-009).
 *
 * <ul>
 *   <li><b>Permisos.</b> Las jornadas se listan con {@code programacion.jornada:APROBAR} y los
 *       comunicados con {@code publicacion.comunicado:APROBAR}, ambos con ámbito de liga
 *       (convenciones de API §6.11). Con uno solo de los dos, la bandeja trae solo ese tipo.</li>
 *   <li><b>Denegación.</b> Sin ninguno de los dos, {@code 403 VAL-015}; con alguno pero sin ámbito
 *       de liga, {@code 404} (VAL-024): las jornadas y los comunicados son de la liga.</li>
 *   <li><b>Solo lectura.</b> No audita: consultar la bandeja no es una operación crítica (RF-093).</li>
 * </ul>
 *
 * <p>Refs: H-13.1, RF-090, RF-121, UI-009, VAL-015, VAL-024, ADR-0007
 */
@Service
public class PublicacionPendienteService {

    private static final String APROBAR_JORNADA = "programacion.jornada:APROBAR";
    private static final String APROBAR_COMUNICADO = "publicacion.comunicado:APROBAR";
    private static final int TAMANO_MAXIMO = 100;

    private final PublicacionPendienteRepository pendientes;
    private final Autorizacion autorizacion;
    private final UUID ligaId;

    PublicacionPendienteService(PublicacionPendienteRepository pendientes, Autorizacion autorizacion,
            SiglimPropiedades propiedades) {
        this.pendientes = pendientes;
        this.autorizacion = autorizacion;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Pendientes de dictamen, del envío más antiguo al más reciente (RF-090, RF-121).
     *
     * @param pagina página, desde 0
     * @param tamano filas por página, de 1 a 100
     * @throws ReglaNegocioException 403 {@code VAL-015} sin ninguno de los permisos de aprobación;
     *                               400 {@code VALIDACION} con paginación inválida
     */
    @Transactional(readOnly = true)
    public Pagina<PublicacionPendienteDto> listar(int pagina, int tamano) {
        Alcance jornadas = alcanceDe(APROBAR_JORNADA);
        Alcance comunicados = alcanceDe(APROBAR_COMUNICADO);
        if (jornadas == Alcance.SIN_PERMISO && comunicados == Alcance.SIN_PERMISO) {
            throw Autorizacion.sinPermiso();
        }
        if (jornadas != Alcance.LIGA && comunicados != Alcance.LIGA) {
            throw Autorizacion.fueraDeAmbito(jornadas == Alcance.FUERA_DE_AMBITO ? APROBAR_JORNADA
                    : APROBAR_COMUNICADO);
        }
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw ReglaNegocioException.invalida(ErroresTecnicos.VALIDACION,
                    "La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO + ".");
        }

        boolean conJornadas = jornadas == Alcance.LIGA;
        boolean conComunicados = comunicados == Alcance.LIGA;
        long desplazamiento = (long) pagina * tamano;
        List<PublicacionPendienteDto> contenido = pendientes.listar(ligaId, conJornadas, conComunicados,
                tamano, desplazamiento);
        return Pagina.de(contenido, pagina, tamano, pendientes.contar(ligaId, conJornadas, conComunicados));
    }

    private Alcance alcanceDe(String permiso) {
        try {
            return autorizacion.alcance(permiso).todaLaLiga() ? Alcance.LIGA : Alcance.FUERA_DE_AMBITO;
        } catch (ReglaNegocioException sinPermiso) {
            if (ErroresTecnicos.SIN_PERMISO.equals(sinPermiso.codigo())) {
                return Alcance.SIN_PERMISO;
            }
            throw sinPermiso;
        }
    }

    private enum Alcance {
        LIGA,
        FUERA_DE_AMBITO,
        SIN_PERMISO
    }
}
