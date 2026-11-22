package mx.tecnm.morelia.siglim.programacion;

import java.time.OffsetDateTime;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.CanchaDeLiga;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.DatosPartido;
import mx.tecnm.morelia.siglim.programacion.JornadaRepository.EquipoDeLiga;
import org.springframework.stereotype.Component;

/**
 * Valida la nueva fecha y cancha de un partido que se reprograma (H-15.1, RF-043) con las mismas
 * reglas de conflictos duros de la programación manual (RF-039, VAL-003 a VAL-006), para que la
 * reprogramación de M-08 no duplique ni contradiga a M-07.
 *
 * <p>Es la única entrada pública de esas reglas hacia {@code partidos} (ADR-0006: un módulo de nivel
 * superior usa uno inferior). No escribe nada ni autoriza: lo hace el servicio que la llama, dentro de
 * su transacción.
 *
 * <p>Refs: H-15.1, RF-039, RF-043, VAL-003, VAL-004, VAL-005, VAL-006, DEC-160
 */
@Component
public class ValidacionReprogramacion {

    private final JornadaRepository jornadas;
    private final ValidadorConflictos validador;
    private final UUID ligaId;

    ValidacionReprogramacion(JornadaRepository jornadas, ValidadorConflictos validador,
            SiglimPropiedades propiedades) {
        this.jornadas = jornadas;
        this.validador = validador;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Comprueba que el partido pueda jugarse en esa fecha y cancha sin conflictos duros, excluyéndose
     * a sí mismo de los traslapes.
     *
     * @param partidoId partido de la liga que se reprograma
     * @param inicio    nueva fecha y hora de inicio
     * @param canchaId  cancha nueva o la misma del partido
     * @throws RecursoNoEncontradoException 404 si el partido no es de la liga
     * @throws ReglaNegocioException 422 {@code REFERENCIA_INVALIDA} si la cancha no es de la liga;
     *         409 {@code VAL-003} a {@code VAL-006} o {@code RF-100} con el primer conflicto explicado
     */
    public void validar(UUID partidoId, OffsetDateTime inicio, UUID canchaId) {
        DatosPartido partido = jornadas.buscarPartidoParaActualizar(ligaId, partidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Partido " + partidoId));
        CanchaDeLiga cancha = jornadas.bloquearCancha(ligaId, canchaId)
                .orElseThrow(() -> ReglaNegocioException.noProcesable(ErroresTecnicos.REFERENCIA_INVALIDA,
                        "La cancha indicada no existe en la liga."));
        EquipoDeLiga local = new EquipoDeLiga(partido.equipoLocalId(), partido.equipoLocalNombre(),
                partido.grupoId(), partido.clubLocalId(), partido.equipoLocalEstado());
        EquipoDeLiga visitante = new EquipoDeLiga(partido.equipoVisitanteId(), partido.equipoVisitanteNombre(),
                partido.grupoId(), partido.clubVisitanteId(), partido.equipoVisitanteEstado());
        validador.validar(partido.grupoId(), partidoId, local, visitante, cancha, inicio);
    }
}
