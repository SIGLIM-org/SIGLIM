package mx.tecnm.morelia.siglim.registros;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.ArchivoAlmacenado;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.registros.RegistroJugadorDto.ReferenciaDto;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.DocumentoRegistro;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.RequisitoDocumento;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import mx.tecnm.morelia.siglim.temporadas.TipoCategoria;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code documentos} de un registro de jugador, de su catálogo ({@code tipos_documento},
 * {@code requisitos_documento}) y del registro dueño. El equipo, la categoría y la temporada se leen
 * aquí solo para autorizar y validar: su CRUD es de M-02 y M-03.
 *
 * <p>Todo corre dentro de la transacción del servicio, sujeto a RLS: un documento o un registro que
 * el usuario no ve no existe para él ({@code documentos_leer}, {@code registros_leer}). La edad se
 * consulta con {@code es_menor}, que es {@code SECURITY DEFINER} (DEC-78).
 *
 * <p><b>Concurrencia (DEC-93).</b> Las escrituras bloquean primero el registro con
 * {@code FOR UPDATE OF r} (solo esa fila, no la temporada ni el equipo), y los {@code UPDATE} de
 * documentos son condicionales con {@code RETURNING}: si no devuelven fila, otra petición cambió el
 * documento antes.
 *
 * <p>{@link RegistroJugadorService#validar} reutiliza {@link #bloquearRegistro}, {@link #requisitos} y
 * {@link #vigentes} para calcular VAL-001 con el registro bloqueado (H-10.2, DEC-102).
 *
 * <p>Refs: H-10.1, H-10.2, RF-108, RF-109, RNF-018, DEC-92, DEC-93, DEC-94, DEC-102
 */
@Repository
class DocumentoRegistroRepository {

    private static final ZoneId ZONA_DE_NEGOCIO = ZoneId.of("America/Mexico_City");

    private static final String REGISTRO = """
            SELECT r.id, r.persona_id, r.alta_en, r.estado, e.club_id, t.estado AS temporada_estado,
                   c.id AS categoria_id, c.nombre AS categoria_nombre, c.tipo AS categoria_tipo,
                   r.equipo_id, e.estado = 'ACTIVO' AS equipo_activo, r.temporada_id, r.creado_por
              FROM registros_jugador r
              JOIN equipos e ON e.id = r.equipo_id
              JOIN clubes cl ON cl.id = e.club_id
              JOIN grupos g ON g.id = e.grupo_id
              JOIN categorias_temporada ct ON ct.id = g.categoria_temporada_id
              JOIN categorias c ON c.id = ct.categoria_id
              JOIN temporadas t ON t.id = r.temporada_id
             WHERE r.id = :id AND cl.liga_id = :ligaId
            """;

    private static final String DOCUMENTO = """
            SELECT d.id, d.registro_id, td.id AS tipo_id, td.codigo AS tipo_codigo, td.nombre AS tipo_nombre,
                   d.tipo_mime, d.tamano_bytes, d.estado_validacion, d.observaciones,
                   d.validado_por, pf.nombre_mostrado AS validado_por_nombre, d.validado_en,
                   (SELECT a.id FROM documentos a WHERE a.reemplazado_por_id = d.id
                     ORDER BY a.cargado_en LIMIT 1) AS reemplaza_a,
                   d.cargado_en, d.cargado_por, d.reemplazado_por_id, d.s3_bucket, d.s3_clave
              FROM documentos d
              JOIN tipos_documento td ON td.id = d.tipo_documento_id
              LEFT JOIN perfiles pf ON pf.id = d.validado_por
            """;

    private final JdbcClient jdbc;

    DocumentoRegistroRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** El registro de la liga con su club, persona, categoría y temporada; vacío si no existe o no se ve. */
    Optional<RegistroDeDocumentos> buscarRegistro(UUID ligaId, UUID registroId) {
        return consultarRegistro(REGISTRO, ligaId, registroId);
    }

    /**
     * Igual que {@link #buscarRegistro}, pero bloquea la fila del registro hasta el fin de la
     * transacción ({@code FOR UPDATE OF r}): toda escritura de documentos lo llama primero (DEC-93).
     */
    Optional<RegistroDeDocumentos> bloquearRegistro(UUID ligaId, UUID registroId) {
        return consultarRegistro(REGISTRO + " FOR UPDATE OF r", ligaId, registroId);
    }

    /** Si la persona es menor en esa fecha (RN-055); vacío si no existe (DEC-78). */
    Optional<Boolean> esMenor(UUID personaId, LocalDate fecha) {
        return jdbc.sql("SELECT siglim.es_menor(:persona, :fecha) AS menor")
                .param("persona", personaId)
                .param("fecha", fecha)
                .query((fila, numero) -> {
                    boolean menor = fila.getBoolean("menor");
                    return fila.wasNull() ? Optional.<Boolean>empty() : Optional.of(menor);
                })
                .single();
    }

    /** Requisitos de la liga para el tipo de categoría, por {@code tipo_documento_id} (DEC-92). */
    List<RequisitoDocumento> requisitos(UUID ligaId, TipoCategoria tipo) {
        return jdbc.sql("""
                        SELECT td.id, td.codigo, td.nombre, rd.cantidad, rd.solo_menores, rd.es_demo
                          FROM requisitos_documento rd
                          JOIN tipos_documento td ON td.id = rd.tipo_documento_id
                         WHERE rd.liga_id = :ligaId AND rd.tipo_categoria = CAST(:tipo AS siglim.tipo_categoria)
                         ORDER BY rd.tipo_documento_id
                        """)
                .param("ligaId", ligaId)
                .param("tipo", tipo.name())
                .query((fila, numero) -> new RequisitoDocumento(
                        new TipoDocumentoDto(fila.getLong("id"), fila.getString("codigo"), fila.getString("nombre")),
                        fila.getInt("cantidad"),
                        fila.getBoolean("solo_menores"),
                        fila.getBoolean("es_demo")))
                .list();
    }

    /** El tipo de documento del catálogo; vacío si no existe. */
    Optional<TipoDocumentoDto> tipoDocumento(long tipoDocumentoId) {
        return jdbc.sql("SELECT id, codigo, nombre FROM tipos_documento WHERE id = :id")
                .param("id", tipoDocumentoId)
                .query((fila, numero) -> new TipoDocumentoDto(fila.getLong("id"), fila.getString("codigo"),
                        fila.getString("nombre")))
                .optional();
    }

    /** Documentos vigentes ({@code reemplazado_por_id IS NULL}) del registro, de cualquier estado. */
    List<DocumentoRegistro> vigentes(UUID registroId) {
        return jdbc.sql(DOCUMENTO + """
                         WHERE d.registro_id = :registro AND d.reemplazado_por_id IS NULL
                         ORDER BY d.cargado_en, d.id
                        """)
                .param("registro", registroId)
                .query(DocumentoRegistroRepository::aDocumento)
                .list();
    }

    /**
     * Un documento <b>de un registro</b> de la liga; vacío si no existe, si es de otro dueño (pago,
     * afiliación, expediente o persona) o si RLS no lo muestra (DEC-91).
     */
    Optional<DocumentoRegistro> buscarDocumento(UUID ligaId, UUID documentoId) {
        return jdbc.sql(DOCUMENTO + """
                          JOIN registros_jugador r ON r.id = d.registro_id
                          JOIN equipos e ON e.id = r.equipo_id
                          JOIN clubes cl ON cl.id = e.club_id
                         WHERE d.id = :id AND cl.liga_id = :ligaId
                        """)
                .param("id", documentoId)
                .param("ligaId", ligaId)
                .query(DocumentoRegistroRepository::aDocumento)
                .optional();
    }

    /** Inserta el documento {@code PENDIENTE} (lo pone el {@code DEFAULT}) del registro, en la carpeta REGISTROS. */
    void insertar(UUID documentoId, long tipoDocumentoId, UUID registroId, ArchivoAlmacenado archivo, UUID cargadoPor) {
        jdbc.sql("""
                        INSERT INTO documentos (id, tipo_documento_id, registro_id, s3_bucket, s3_clave, url, tipo_mime,
                                                tamano_bytes, sha256, cargado_por)
                        VALUES (:id, :tipo, :registro, :s3Bucket, :s3Clave, :url, :tipoMime, :tamanoBytes, :sha256,
                                :cargadoPor)
                        """)
                .param("id", documentoId)
                .param("tipo", tipoDocumentoId)
                .param("registro", registroId)
                .param("s3Bucket", archivo.s3Bucket())
                .param("s3Clave", archivo.s3Clave())
                .param("url", archivo.url())
                .param("tipoMime", archivo.tipoMime())
                .param("tamanoBytes", archivo.tamanoBytes())
                .param("sha256", archivo.sha256())
                .param("cargadoPor", cargadoPor)
                .update();
    }

    /**
     * Marca el documento como reemplazado si sigue vigente y {@code PENDIENTE} o {@code RECHAZADO};
     * {@code false} si otra petición lo reemplazó o lo aprobó antes (DEC-93).
     */
    boolean marcarReemplazado(UUID documentoId, UUID nuevoId) {
        return jdbc.sql("""
                        UPDATE documentos SET reemplazado_por_id = :nuevo
                         WHERE id = :id AND reemplazado_por_id IS NULL
                           AND estado_validacion IN ('PENDIENTE', 'RECHAZADO')
                        RETURNING id
                        """)
                .param("id", documentoId)
                .param("nuevo", nuevoId)
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    /**
     * Guarda el dictamen si el documento sigue vigente y {@code PENDIENTE}; {@code false} si otra
     * petición lo validó o lo reemplazó antes (DEC-93, DEC-96).
     */
    boolean resolver(UUID documentoId, EstadoValidacion estado, UUID validadoPor, OffsetDateTime validadoEn,
            String observaciones) {
        return jdbc.sql("""
                        UPDATE documentos
                           SET estado_validacion = CAST(:estado AS siglim.estado_validacion),
                               validado_por = :validadoPor, validado_en = :validadoEn, observaciones = :observaciones
                         WHERE id = :id AND estado_validacion = 'PENDIENTE' AND reemplazado_por_id IS NULL
                        RETURNING id
                        """)
                .param("id", documentoId)
                .param("estado", estado.name())
                .param("validadoPor", validadoPor)
                .param("validadoEn", validadoEn)
                .param("observaciones", observaciones)
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    private Optional<RegistroDeDocumentos> consultarRegistro(String sql, UUID ligaId, UUID registroId) {
        return jdbc.sql(sql)
                .param("id", registroId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new RegistroDeDocumentos(
                        fila.getObject("id", UUID.class),
                        fila.getObject("persona_id", UUID.class),
                        fila.getObject("club_id", UUID.class),
                        fila.getObject("alta_en", LocalDate.class),
                        EstadoRegistro.valueOf(fila.getString("estado")),
                        EstadoTemporada.valueOf(fila.getString("temporada_estado")),
                        fila.getObject("categoria_id", UUID.class),
                        fila.getString("categoria_nombre"),
                        TipoCategoria.valueOf(fila.getString("categoria_tipo")),
                        fila.getObject("equipo_id", UUID.class),
                        fila.getBoolean("equipo_activo"),
                        fila.getObject("temporada_id", UUID.class),
                        fila.getObject("creado_por", UUID.class)))
                .optional();
    }

    private static DocumentoRegistro aDocumento(ResultSet fila, int numero) throws SQLException {
        UUID validadoPor = fila.getObject("validado_por", UUID.class);
        DocumentoDto dto = new DocumentoDto(
                fila.getObject("id", UUID.class),
                fila.getObject("registro_id", UUID.class),
                new TipoDocumentoDto(fila.getLong("tipo_id"), fila.getString("tipo_codigo"), fila.getString("tipo_nombre")),
                fila.getString("tipo_mime"),
                fila.getInt("tamano_bytes"),
                EstadoValidacion.valueOf(fila.getString("estado_validacion")),
                fila.getString("observaciones"),
                validadoPor == null ? null : new ReferenciaDto(validadoPor, fila.getString("validado_por_nombre")),
                enZonaDeNegocio(fila.getObject("validado_en", OffsetDateTime.class)),
                fila.getObject("reemplaza_a", UUID.class),
                enZonaDeNegocio(fila.getObject("cargado_en", OffsetDateTime.class)));
        return new DocumentoRegistro(dto,
                fila.getObject("cargado_por", UUID.class),
                fila.getObject("reemplazado_por_id", UUID.class),
                fila.getString("s3_bucket"),
                fila.getString("s3_clave"));
    }

    private static OffsetDateTime enZonaDeNegocio(OffsetDateTime momento) {
        return momento == null ? null : momento.atZoneSameInstant(ZONA_DE_NEGOCIO).toOffsetDateTime();
    }

    /**
     * El registro dueño de los documentos, con lo necesario para autorizar y validar (DEC-91 a DEC-93).
     * La validación del registro (H-10.2, DEC-97 a DEC-102) lo bloquea con {@link #bloquearRegistro} y
     * usa además el equipo y si está activo (RF-013), la temporada (auditoría) y quién lo dio de alta
     * (VAL-022).
     *
     * @param creadoPor perfil que dio de alta el registro; {@code null} si no se registró
     */
    record RegistroDeDocumentos(UUID id, UUID personaId, UUID clubId, LocalDate altaEn, EstadoRegistro estado,
                                EstadoTemporada temporadaEstado, UUID categoriaId, String categoriaNombre,
                                TipoCategoria categoriaTipo, UUID equipoId, boolean equipoActivo, UUID temporadaId,
                                UUID creadoPor) {
    }
}
