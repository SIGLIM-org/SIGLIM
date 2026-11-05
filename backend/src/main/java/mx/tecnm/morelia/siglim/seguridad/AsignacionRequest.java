package mx.tecnm.morelia.siglim.seguridad;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Una asignación de rol con su ámbito dentro de una invitación o de un alta de asignación
 * (RF-094, RN-054).
 *
 * <p>El ámbito es opcional: si se omite, el servicio usa {@code roles.ambito_predeterminado}, que
 * es el «ámbito con que se asigna normalmente el rol» (diccionario de datos). Si se indica, el
 * club es obligatorio con {@code CLUB} y debe omitirse con los demás ámbitos; el servicio vuelve a
 * validar el club contra la liga.
 *
 * <p>Refs: H-03.1, RF-094, RN-054
 */
public record AsignacionRequest(
        @NotNull(message = "El rol es obligatorio")
        UUID rolId,
        Ambito ambito,
        UUID clubId) {

    /** 400 antes del servicio: un ámbito {@code CLUB} sin club, o un club con otro ámbito. */
    @AssertTrue(message = "El club es obligatorio con ámbito CLUB y debe omitirse en los demás ámbitos")
    public boolean isClubCoherente() {
        if (ambito == null) {
            return clubId == null;
        }
        return (ambito == Ambito.CLUB) == (clubId != null);
    }
}
