package mx.tecnm.morelia.siglim.auditoria;

import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.api.Pagina;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de la auditoría inalterable, de solo lectura (convenciones de API §6.1).
 *
 * <ul>
 *   <li><b>Permiso.</b> {@code auditoria.registro:CONSULTAR}; sin él, {@code 403 VAL-015}.</li>
 *   <li><b>Ámbito [PROPUESTA].</b> La tabla no tiene club ni persona: solo el ámbito {@code LIGA} ve
 *       filas. Con {@code CLUB} o {@code PROPIO} el listado y su total salen vacíos, igual que la
 *       política RLS {@code auditoria_leer}.</li>
 *   <li><b>Filtros.</b> Perfil, uno o varios módulos {@code M-xx} y una o varias acciones. Un formato
 *       inválido responde {@code 400 VALIDACION}; una acción bien formada que no existe, una página
 *       vacía: no hay catálogo fijo de acciones.</li>
 *   <li><b>Fechas [PROPUESTA].</b> {@code desde} incluido y {@code hasta} excluido, opcionales y sin
 *       rango máximo; {@code desde >= hasta} responde {@code 400 VALIDACION}.</li>
 *   <li><b>Página y orden.</b> De 50 filas por defecto y 100 como máximo, por {@code ocurridoEn}
 *       descendente por defecto, con el id como desempate en la misma dirección.</li>
 *   <li><b>Datos personales.</b> Del perfil solo salen el id y el nombre mostrado; {@code detalle} se
 *       devuelve tal cual porque por contrato no los lleva ({@link AuditoriaService}).</li>
 *   <li><b>Opciones de los filtros.</b> Mismo permiso y ámbito: valores presentes en la auditoría de
 *       la liga. Existen porque el Presidente consulta la auditoría sin
 *       {@code seguridad.usuario:CONSULTAR}, así que no podría pedir la lista de usuarios.</li>
 * </ul>
 *
 * <p>Refs: H-21.1, RF-134, RNF-012, RNF-015, VAL-015, ADR-0006, ADR-0007, ADR-0011
 */
@Service
public class ConsultaAuditoriaService {

    private final AuditoriaRepository repositorio;
    private final AutorizacionConsultaAuditoria autorizacionConsulta;
    private final UUID ligaId;

    ConsultaAuditoriaService(AuditoriaRepository repositorio, AutorizacionConsultaAuditoria autorizacionConsulta,
            SiglimPropiedades propiedades) {
        this.repositorio = repositorio;
        this.autorizacionConsulta = autorizacionConsulta;
        this.ligaId = propiedades.ligaId();
    }

    /** Listado filtrado por ámbito en el SQL, también en el total (RF-134, ADR-0007). */
    @Transactional(readOnly = true)
    public Pagina<RegistroAuditoriaDto> listar(FiltroAuditoria filtro) {
        boolean todaLaLiga = autorizacionConsulta.alcanzaTodaLaLiga();
        filtro.validar();
        return Pagina.de(repositorio.listar(ligaId, filtro, todaLaLiga), filtro.pagina(), filtro.tamano(),
                repositorio.contar(ligaId, filtro, todaLaLiga));
    }

    /** Perfiles, módulos y acciones presentes en la auditoría que alcanza el usuario (RF-134). */
    @Transactional(readOnly = true)
    public OpcionesFiltroAuditoriaDto opcionesFiltro() {
        boolean todaLaLiga = autorizacionConsulta.alcanzaTodaLaLiga();
        return repositorio.opcionesFiltro(ligaId, todaLaLiga);
    }
}
