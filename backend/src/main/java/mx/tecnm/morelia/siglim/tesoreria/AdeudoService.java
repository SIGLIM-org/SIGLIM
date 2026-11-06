package mx.tecnm.morelia.siglim.tesoreria;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.tesoreria.AdeudosDto.ClubAdeudoDto;
import mx.tecnm.morelia.siglim.tesoreria.AdeudosDto.EquipoAdeudoDto;
import mx.tecnm.morelia.siglim.tesoreria.AdeudosDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.tesoreria.CobroRepository.FilaAdeudo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adeudos por club y equipo (RF-140, UI-027).
 *
 * <ul>
 *   <li><b>Qué es adeudo.</b> Solo los cobros {@code PENDIENTE} y {@code VENCIDO}; un cobro
 *       {@code PAGADO} o {@code RECHAZADO} no suma. La suma y el conteo los hace el SQL, no el
 *       backend, para no traer todas las filas.</li>
 *   <li><b>Ámbito (ADR-0007).</b> El listado usa {@link Autorizacion#alcance(String)} en el
 *       {@code WHERE}; una Directiva de club solo recibe su club. El filtro {@code clubId} no es
 *       una autorización: un club fuera del ámbito simplemente no aparece, igual que en el
 *       listado de cobros (VAL-024).</li>
 *   <li><b>Desglose.</b> La respuesta suma por club y, dentro de él, por equipo. Los cobros
 *       cargados al club sin equipo suman en el club y no en un equipo. Ningún club aparece con
 *       total cero.</li>
 * </ul>
 *
 * <p>Refs: H-29.1, RF-140, UI-027, VAL-024, ADR-0007
 */
@Service
public class AdeudoService {

    private static final String CONSULTAR = "tesoreria.cobro:CONSULTAR";
    private static final BigDecimal CERO = new BigDecimal("0.00");

    private final CobroRepository cobros;
    private final Autorizacion autorizacion;
    private final UUID ligaId;

    AdeudoService(CobroRepository cobros, Autorizacion autorizacion, SiglimPropiedades propiedades) {
        this.cobros = cobros;
        this.autorizacion = autorizacion;
        this.ligaId = propiedades.ligaId();
    }

    /**
     * Adeudos visibles, opcionalmente de un club.
     *
     * @param clubId club a consultar; {@code null} para todos los del ámbito
     * @return totales generales y desglose por club y equipo, en pesos con dos decimales
     */
    @Transactional(readOnly = true)
    public AdeudosDto consultar(UUID clubId) {
        AlcanceAutorizacion alcance = autorizacion.alcance(CONSULTAR);
        List<FilaAdeudo> filas = cobros.adeudos(ligaId, clubId, alcance);

        Map<UUID, ClubAcumulado> porClub = new LinkedHashMap<>();
        for (FilaAdeudo fila : filas) {
            ClubAcumulado club = porClub.computeIfAbsent(fila.clubId(),
                    id -> new ClubAcumulado(id, fila.clubNombre()));
            club.sumar(fila);
            if (fila.equipoId() != null) {
                club.equipos.computeIfAbsent(fila.equipoId(),
                        id -> new EquipoAcumulado(id, fila.equipoNombre())).sumar(fila);
            }
        }

        BigDecimal total = CERO;
        BigDecimal pendiente = CERO;
        BigDecimal vencido = CERO;
        long cobros = 0;
        List<ClubAdeudoDto> clubes = new ArrayList<>(porClub.size());
        for (ClubAcumulado club : porClub.values()) {
            total = total.add(club.total);
            pendiente = pendiente.add(club.pendiente);
            vencido = vencido.add(club.vencido);
            cobros += club.cobros;
            clubes.add(club.aDto());
        }
        return new AdeudosDto(total, pendiente, vencido, cobros, clubes);
    }

    /** Acumulado de un club mientras se agrupan las filas del SQL. */
    private static final class ClubAcumulado {

        private final UUID id;
        private final String nombre;
        private final Map<UUID, EquipoAcumulado> equipos = new LinkedHashMap<>();
        private BigDecimal total = CERO;
        private BigDecimal pendiente = CERO;
        private BigDecimal vencido = CERO;
        private long cobros;

        private ClubAcumulado(UUID id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }

        private void sumar(FilaAdeudo fila) {
            total = total.add(valor(fila.total()));
            pendiente = pendiente.add(valor(fila.pendiente()));
            vencido = vencido.add(valor(fila.vencido()));
            cobros += fila.cobros();
        }

        private ClubAdeudoDto aDto() {
            List<EquipoAdeudoDto> equiposDto = equipos.values().stream()
                    .map(EquipoAcumulado::aDto)
                    .toList();
            return new ClubAdeudoDto(new ReferenciaDto(id, nombre), total, pendiente, vencido, cobros, equiposDto);
        }
    }

    /** Acumulado de un equipo dentro de su club. */
    private static final class EquipoAcumulado {

        private final UUID id;
        private final String nombre;
        private BigDecimal total = CERO;
        private BigDecimal pendiente = CERO;
        private BigDecimal vencido = CERO;
        private long cobros;

        private EquipoAcumulado(UUID id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }

        private void sumar(FilaAdeudo fila) {
            total = total.add(valor(fila.total()));
            pendiente = pendiente.add(valor(fila.pendiente()));
            vencido = vencido.add(valor(fila.vencido()));
            cobros += fila.cobros();
        }

        private EquipoAdeudoDto aDto() {
            return new EquipoAdeudoDto(new ReferenciaDto(id, nombre), total, pendiente, vencido, cobros);
        }
    }

    private static BigDecimal valor(BigDecimal monto) {
        return monto == null ? CERO : monto;
    }
}
