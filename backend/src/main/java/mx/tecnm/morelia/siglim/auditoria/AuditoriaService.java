package mx.tecnm.morelia.siglim.auditoria;

/**
 * Registro de la auditoría inalterable: usuario, fecha, módulo, acción, entidad y motivo de cada
 * operación crítica (RF-093, VAL-016, RNF-015). Es el único punto del backend que escribe en
 * {@code siglim.auditoria}.
 *
 * <ul>
 *   <li><b>Transacción obligatoria.</b> Se llama dentro de la transacción del servicio que hace la
 *       operación de negocio, para que ambas se confirmen o se reviertan juntas. Sin transacción
 *       activa lanza {@link org.springframework.transaction.IllegalTransactionStateException}.</li>
 *   <li><b>Sin operación sin rastro (RNF-015).</b> Los errores del registro no se capturan: si la
 *       auditoría falla, se revierte toda la operación.</li>
 *   <li><b>Usuario y fecha.</b> El perfil sale de la sesión ({@code UsuarioActual}) y la fecha del
 *       {@code DEFAULT now()} de la tabla; quien llama no los envía.</li>
 *   <li><b>Motivo.</b> Si el evento no trae motivo, se guarda el que se fijó con
 *       {@code MotivoOperacion} en la misma transacción ({@code siglim.motivo_actual()}); si tampoco
 *       hay, queda vacío.</li>
 *   <li><b>Datos personales.</b> {@code detalle} nunca debe incluir CURP, nombres, correos,
 *       teléfonos, domicilios ni otros datos que identifiquen a una persona (comentario de la
 *       tabla, RNF-012): usa identificadores, estados y valores técnicos. No hay filtrado
 *       automático; la responsabilidad es de quien llama.</li>
 * </ul>
 *
 * <p>Refs: H-02.2, RF-093, RNF-015, VAL-016
 */
public interface AuditoriaService {

    /** Registra el evento en la transacción en curso. */
    void registrar(EventoAuditoria evento);
}
