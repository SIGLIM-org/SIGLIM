package mx.tecnm.morelia.siglim.arbitraje;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.arbitraje.CandidatoArbitralDto.CausaDto;
import mx.tecnm.morelia.siglim.arbitraje.DesignacionRepository.PartidoBloqueado;
import mx.tecnm.morelia.siglim.arbitraje.SelectorArbitroCentral.Eleccion;
import mx.tecnm.morelia.siglim.arbitraje.SelectorArbitroCentral.Elegido;
import mx.tecnm.morelia.siglim.arbitraje.SelectorArbitroCentral.SinCandidato;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.UsuarioActual;
import mx.tecnm.morelia.siglim.programacion.DesignadorArbitroCentral;
import mx.tecnm.morelia.siglim.programacion.ResultadoCentrales;
import mx.tecnm.morelia.siglim.programacion.ResultadoCentrales.CentralDesignado;
import mx.tecnm.morelia.siglim.programacion.ResultadoCentrales.SinCentral;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Designación automática y equitativa del árbitro central (H-34.1, DEC-182): implementa el puerto
 * {@link DesignadorArbitroCentral} de {@code programacion} (DEC-213, ADR-0006), que lo llama al
 * asignar una fecha (H-34.2, DEC-217) y al verificarla (DEC-220).
 *
 * <ul>
 *   <li><b>Permiso.</b> {@code arbitraje.designacion:AUTOMATICA} en ámbito de liga (PROGRAMADOR), no
 *       {@code EDITAR}: el cambio manual sigue siendo de la Comisión de Arbitraje.</li>
 *   <li><b>Transacción.</b> {@code Propagation.MANDATORY}: se une a la de la asignación, para que las
 *       designaciones se confirmen o se reviertan con los partidos que la originaron.</li>
 *   <li><b>Bloqueos (DEC-215), antes de elegir.</b> Primero todos los partidos de la lista, por
 *       {@code id}, y después todos los árbitros {@code VALIDADO} de la liga, por {@code id}. Es el
 *       mismo orden partido → árbitro de {@link DesignacionService}: si se bloqueara cada árbitro al
 *       insertar, una designación manual simultánea (H-14.1) que ya tiene su partido y espera a su
 *       árbitro podría cruzarse con esta y caer en un interbloqueo; y sin bloquear antes de leer, la
 *       manual podría confirmar un choque que esta no vio, y el {@code 409 RF-039} del trigger
 *       tumbaría la asignación de toda la fecha.</li>
 *   <li><b>Por partido</b>, por inicio y después id por texto: se omiten los que no están
 *       {@code PROGRAMADO} ni {@code REPROGRAMADO} y los que ya tienen central vigente. A los demás se
 *       les vuelven a pedir los candidatos de {@link ElegibilidadArbitral} y el último partido de
 *       cada árbitro; como se consultan dentro de la misma transacción, la carga y los choques ya
 *       incluyen las designaciones de esta corrida, sin estado en memoria entre partidos. Elige
 *       {@link SelectorArbitroCentral} (DEC-214).</li>
 *   <li><b>Desempate sin {@code designado_en}.</b> {@code now()} es constante dentro de la
 *       transacción: todas las designaciones de la corrida tendrían la misma fecha. Se usa el inicio
 *       del último partido del árbitro.</li>
 *   <li><b>Sin candidato</b>, {@code SinCentral} con {@code RF-023} y la causa de DEC-216; no se
 *       audita porque no cambia nada.</li>
 *   <li><b>Explicación (DEC-220).</b> {@link #explicarSinCentral} es de solo lectura, sin
 *       {@code FOR UPDATE} ni permiso propio: la usa la verificación de la fecha, que ya exigió
 *       {@code programacion.jornada:CONSULTAR}, y solo devuelve conteos.</li>
 *   <li><b>Auditoría (RF-093).</b> {@code DESIGNAR_ARBITRO} en {@code M-05} sobre
 *       {@code designaciones}, sin motivo, con {@code partidoId}, {@code arbitroId},
 *       {@code funcion} y {@code automatica}; sin nombres ni CURP.</li>
 * </ul>
 *
 * <p>Refs: H-34.1, RF-022, RF-023, RF-039, RF-093, RF-115, VAL-015, VAL-019, DEC-182, DEC-201,
 * DEC-204, DEC-213 a DEC-217, DEC-220, ADR-0006, ADR-0007, ADR-0015
 */
@Component
public class DesignacionAutomatica implements DesignadorArbitroCentral {

    static final String AUTOMATICA = "arbitraje.designacion:AUTOMATICA";
    static final String CODIGO_SIN_CENTRAL = "RF-023";
    private static final String MODULO = "M-05";
    private static final String ENTIDAD = "designaciones";
    private static final Comparator<PartidoBloqueado> POR_INICIO = Comparator
            .comparing((PartidoBloqueado p) -> p.inicio().toInstant())
            .thenComparing(p -> p.id().toString());

    private final DesignacionRepository designaciones;
    private final ElegibilidadArbitral elegibilidad;
    private final Autorizacion autorizacion;
    private final AuditoriaService auditoria;
    private final UUID ligaId;

    DesignacionAutomatica(DesignacionRepository designaciones, ElegibilidadArbitral elegibilidad,
            Autorizacion autorizacion, AuditoriaService auditoria, SiglimPropiedades propiedades) {
        this.designaciones = designaciones;
        this.elegibilidad = elegibilidad;
        this.autorizacion = autorizacion;
        this.auditoria = auditoria;
        this.ligaId = propiedades.ligaId();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ResultadoCentrales designarCentrales(List<UUID> partidoIds) {
        autorizacion.exigir(AUTOMATICA, ObjetivoAutorizacion.deLiga());
        if (partidoIds == null || partidoIds.isEmpty()) {
            return ResultadoCentrales.vacio();
        }
        List<PartidoBloqueado> partidos = new ArrayList<>(
                designaciones.bloquearPartidos(ligaId, new LinkedHashSet<>(partidoIds)));
        designaciones.bloquearArbitrosValidados(ligaId);
        partidos.sort(POR_INICIO);

        UUID perfil = UsuarioActual.idRequerido();
        List<CentralDesignado> designados = new ArrayList<>();
        List<SinCentral> sinCentral = new ArrayList<>();
        for (PartidoBloqueado partido : partidos) {
            if (!DesignacionService.designable(partido.estado()) || tieneCentral(partido.id())) {
                continue;
            }
            Eleccion eleccion = SelectorArbitroCentral.elegir(elegibilidad.candidatos(ligaId, partido.id()),
                    designaciones.ultimoPartidoPorArbitro(partido.id()));
            switch (eleccion) {
                case Elegido(CandidatoArbitralDto candidato) ->
                        designados.add(designar(partido.id(), candidato, perfil));
                case SinCandidato(String causa) ->
                        sinCentral.add(new SinCentral(partido.id(), CODIGO_SIN_CENTRAL, causa));
            }
        }
        return new ResultadoCentrales(designados, sinCentral);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Map<UUID, String> explicarSinCentral(List<UUID> partidoIds) {
        if (partidoIds == null || partidoIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> causas = new LinkedHashMap<>();
        for (UUID partidoId : new LinkedHashSet<>(partidoIds)) {
            boolean explicable = designaciones.buscarPartido(ligaId, partidoId)
                    .filter(partido -> DesignacionService.designable(partido.estado()))
                    .isPresent();
            if (!explicable || tieneCentral(partidoId)) {
                continue;
            }
            List<CandidatoArbitralDto> candidatos = elegibilidad.candidatos(ligaId, partidoId);
            long elegibles = candidatos.stream().filter(CandidatoArbitralDto::elegible).count();
            causas.put(partidoId, elegibles == 0
                    ? SelectorArbitroCentral.causa(candidatos)
                    : SelectorArbitroCentral.causaConElegibles((int) elegibles));
        }
        return Collections.unmodifiableMap(causas);
    }

    private boolean tieneCentral(UUID partidoId) {
        return designaciones.vigentesDelPartido(partidoId).stream()
                .anyMatch(d -> d.funcion() == FuncionArbitral.CENTRAL);
    }

    private CentralDesignado designar(UUID partidoId, CandidatoArbitralDto candidato, UUID perfil) {
        UUID arbitroId = candidato.arbitro().id();
        UUID id = designaciones.insertar(partidoId, arbitroId, FuncionArbitral.CENTRAL, perfil, true);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("partidoId", partidoId.toString());
        detalle.put("arbitroId", arbitroId.toString());
        detalle.put("funcion", FuncionArbitral.CENTRAL.name());
        detalle.put("automatica", true);
        auditoria.registrar(new EventoAuditoria(ligaId, MODULO, "DESIGNAR_ARBITRO", ENTIDAD, id.toString(), null,
                detalle));
        return new CentralDesignado(partidoId, arbitroId, id,
                candidato.advertencias().stream().map(CausaDto::codigo).toList());
    }
}
