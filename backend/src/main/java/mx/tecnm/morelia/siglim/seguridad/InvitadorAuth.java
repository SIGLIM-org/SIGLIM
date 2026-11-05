package mx.tecnm.morelia.siglim.seguridad;

import java.util.UUID;

/**
 * Alta de cuentas en Supabase Auth por invitación (ADR-0003 punto 5, RF-094).
 *
 * <p>El registro público está deshabilitado: la única forma de crear una cuenta es esta llamada a
 * la API de administración de Supabase Auth, que envía al correo el enlace para establecer la
 * contraseña. La clave secreta vive solo en {@code backend/.env} y nunca llega al frontend.
 *
 * <p>La interfaz permite sustituir el adaptador HTTP en las pruebas del servicio: la regla de
 * negocio (crear el perfil y sus asignaciones) no depende de la red.
 *
 * <p>Refs: H-03.1, RF-094, ADR-0003
 */
public interface InvitadorAuth {

    /**
     * Invita por correo a una cuenta nueva de Supabase Auth.
     *
     * @param correo correo ya normalizado en minúsculas
     * @return {@code auth.users.id} de la cuenta creada, para enlazarla desde {@code perfiles}
     * @throws mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException 409 {@code DUPLICADO}
     *                         si el correo ya tiene cuenta
     * @throws mx.tecnm.morelia.siglim.compartido.error.ServicioNoDisponibleException 503 si
     *                         Supabase Auth no responde o rechaza la petición por configuración
     */
    UUID invitar(String correo);

    /**
     * Elimina la cuenta recién invitada cuando la transacción de SIGLIM falla después de crearla,
     * para no dejar cuentas de Auth sin perfil. Es una compensación de mejor esfuerzo: si falla,
     * solo se registra en la bitácora.
     *
     * @param usuarioAuthId {@code auth.users.id} devuelto por {@link #invitar(String)}
     */
    void eliminar(UUID usuarioAuthId);
}
