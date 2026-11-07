package mx.tecnm.morelia.siglim.tesoreria;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.ArchivoAlmacenado;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code pagos}, de su comprobante en {@code documentos} y de {@code intentos_pago}.
 *
 * <p>Las lecturas se acotan a la liga con {@code JOIN cobros}; RLS acota además al ámbito del
 * usuario ({@code cobro_visible}). El nombre de quien registró sale de {@code perfiles} con
 * {@code LEFT JOIN}: si RLS no deja verlo, queda nulo y la respuesta lo omite.
 *
 * <p>Nunca se leen {@code s3_bucket}, {@code s3_clave} ni {@code url}: no salen en la API
 * (convenciones de API §7).
 *
 * <p>Refs: H-31.1, RF-139, RF-140, ADR-0013
 */
@Repository
class PagoRepository {

    private static final String SELECCION = """
            SELECT p.id, p.cobro_id, p.medio, p.monto, p.referencia, p.pagado_en,
                   p.registrado_por, pf.nombre_mostrado AS registrado_por_nombre,
                   d.id AS documento_id, d.tipo_mime, d.tamano_bytes
              FROM pagos p
              JOIN cobros c ON c.id = p.cobro_id
              LEFT JOIN perfiles pf ON pf.id = p.registrado_por
              LEFT JOIN LATERAL (
                    SELECT doc.id, doc.tipo_mime, doc.tamano_bytes
                      FROM documentos doc
                     WHERE doc.pago_id = p.id AND doc.reemplazado_por_id IS NULL
                     ORDER BY doc.cargado_en DESC
                     LIMIT 1) d ON true
             WHERE c.liga_id = :ligaId
            """;

    private final JdbcClient jdbc;

    PagoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<PagoDto> buscar(UUID ligaId, UUID pagoId) {
        return jdbc.sql(SELECCION + " AND p.id = :id")
                .param("ligaId", ligaId)
                .param("id", pagoId)
                .query(PagoRepository::aDto)
                .optional();
    }

    List<PagoDto> listarDeCobro(UUID ligaId, UUID cobroId) {
        return jdbc.sql(SELECCION + " AND p.cobro_id = :cobroId ORDER BY p.pagado_en, p.id")
                .param("ligaId", ligaId)
                .param("cobroId", cobroId)
                .query(PagoRepository::aDto)
                .list();
    }

    List<IntentoPagoDto> listarIntentos(UUID ligaId, UUID cobroId) {
        return jdbc.sql("""
                        SELECT i.id, i.referencia, i.estado, i.iniciado_en, i.resuelto_en
                          FROM intentos_pago i
                          JOIN cobros c ON c.id = i.cobro_id
                         WHERE c.liga_id = :ligaId AND i.cobro_id = :cobroId
                         ORDER BY i.iniciado_en, i.id
                        """)
                .param("ligaId", ligaId)
                .param("cobroId", cobroId)
                .query((fila, numero) -> new IntentoPagoDto(
                        fila.getObject("id", UUID.class),
                        fila.getString("referencia"),
                        fila.getString("estado"),
                        fila.getObject("iniciado_en", OffsetDateTime.class),
                        fila.getObject("resuelto_en", OffsetDateTime.class)))
                .list();
    }

    /**
     * Inserta el pago con su id. El trigger {@code tg_pagos_validar} bloquea el cobro, vuelve a
     * validar estado (VAL-028) y monto (RF-139) y lo pasa a {@code PAGADO}.
     */
    void insertar(DatosPago datos) {
        jdbc.sql("""
                        INSERT INTO pagos (id, cobro_id, medio, monto, referencia, pagado_en, registrado_por)
                        VALUES (:id, :cobroId, CAST(:medio AS medio_pago), :monto, :referencia, :pagadoEn,
                                :registradoPor)
                        """)
                .param("id", datos.id())
                .param("cobroId", datos.cobroId())
                .param("medio", datos.medio().name())
                .param("monto", datos.monto())
                .param("referencia", datos.referencia())
                .param("pagadoEn", datos.pagadoEn())
                .param("registradoPor", datos.registradoPor())
                .update();
    }

    /** Fila de {@code documentos} del comprobante, con el tipo {@code COMPROBANTE_PAGO} de la semilla. */
    void insertarComprobante(UUID documentoId, UUID pagoId, ArchivoAlmacenado archivo, UUID cargadoPor) {
        jdbc.sql("""
                        INSERT INTO documentos (id, tipo_documento_id, pago_id, s3_bucket, s3_clave, url, tipo_mime,
                                                tamano_bytes, sha256, cargado_por)
                        VALUES (:id, (SELECT id FROM tipos_documento WHERE codigo = 'COMPROBANTE_PAGO'), :pagoId,
                                :s3Bucket, :s3Clave, :url, :tipoMime, :tamanoBytes, :sha256, :cargadoPor)
                        """)
                .param("id", documentoId)
                .param("pagoId", pagoId)
                .param("s3Bucket", archivo.s3Bucket())
                .param("s3Clave", archivo.s3Clave())
                .param("url", archivo.url())
                .param("tipoMime", archivo.tipoMime())
                .param("tamanoBytes", archivo.tamanoBytes())
                .param("sha256", archivo.sha256())
                .param("cargadoPor", cargadoPor)
                .update();
    }

    /** Valores que se escriben en un pago. */
    record DatosPago(UUID id, UUID cobroId, MedioPago medio, BigDecimal monto, String referencia,
                     OffsetDateTime pagadoEn, UUID registradoPor) {
    }

    private static PagoDto aDto(ResultSet fila, int numero) throws SQLException {
        UUID documentoId = fila.getObject("documento_id", UUID.class);
        return new PagoDto(
                fila.getObject("id", UUID.class),
                fila.getObject("cobro_id", UUID.class),
                MedioPago.valueOf(fila.getString("medio")),
                fila.getBigDecimal("monto"),
                fila.getString("referencia"),
                fila.getObject("pagado_en", OffsetDateTime.class),
                new CobroDto.ReferenciaDto(fila.getObject("registrado_por", UUID.class),
                        fila.getString("registrado_por_nombre")),
                documentoId == null ? null : new PagoDto.ComprobanteDto(documentoId, fila.getString("tipo_mime"),
                        fila.getInt("tamano_bytes")));
    }
}
