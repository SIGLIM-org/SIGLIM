package mx.tecnm.morelia.siglim.tesoreria;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Pasarela de pago simulada (DEC-37, ADR-0012): genera una referencia ficticia con el formato
 * {@code SIM-AAAAMMDD-XXXXXX} y acepta el resultado que la persona elige en la pantalla. <b>No hay
 * ningún cargo real</b> ni se piden datos de tarjeta.
 *
 * <p>La fecha de la referencia es la del día de negocio en {@code America/Mexico_City}, aunque el
 * servidor esté en otra zona. El sufijo es aleatorio y se genera con {@link SecureRandom}; la
 * unicidad final la garantiza el índice de {@code intentos_pago.referencia}, y el servicio reintenta
 * si por azar se repite.
 *
 * <p>Refs: H-30.1, RF-138, VAL-028, DEC-37, ADR-0012
 */
@Component
public class PasarelaSimulada implements PasarelaDePago {

    static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");
    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int LONGITUD_SUFIJO = 6;
    private static final Pattern REFERENCIA = Pattern.compile("^SIM-[0-9]{8}-[A-Z0-9]{6}$");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.BASIC_ISO_DATE;

    private final Clock reloj;
    private final SecureRandom aleatorio = new SecureRandom();

    PasarelaSimulada(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public String iniciar(UUID cobroId) {
        Objects.requireNonNull(cobroId, "cobroId es obligatorio");
        LocalDate hoy = LocalDate.now(reloj.withZone(ZONA_DE_NEGOCIO));
        return "SIM-" + FECHA.format(hoy) + "-" + sufijoAleatorio();
    }

    /**
     * La simulación acepta el resultado elegido; solo comprueba que la referencia y el resultado
     * sean válidos. Una pasarela real sustituiría esta comprobación por la verificación del
     * <i>webhook</i>.
     */
    @Override
    public void confirmar(String referencia, ResultadoPagoSimulado resultado) {
        Objects.requireNonNull(resultado, "resultado es obligatorio");
        if (referencia == null || !REFERENCIA.matcher(referencia).matches()) {
            throw new IllegalArgumentException("La referencia simulada no tiene el formato SIM-AAAAMMDD-XXXXXX");
        }
    }

    private String sufijoAleatorio() {
        StringBuilder sufijo = new StringBuilder(LONGITUD_SUFIJO);
        for (int i = 0; i < LONGITUD_SUFIJO; i++) {
            sufijo.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        }
        return sufijo.toString();
    }
}
