package mx.tecnm.morelia.siglim.tesoreria;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL de {@code tarifas}. La tabla no tiene {@code liga_id}: la liga se acota con la temporada. La
 * temporada y la categoría se leen con {@code JOIN} de solo lectura, sin depender del repositorio de
 * otro módulo (ADR-0006).
 *
 * <p>Refs: B-29.1, B-29.2, RF-135, RF-003
 */
@Repository
class TarifaRepository {

    private static final String SELECCION = """
            SELECT ta.id, ta.temporada_id, t.nombre AS temporada_nombre, ta.categoria_id,
                   c.nombre AS categoria_nombre, ta.concepto, ta.monto
              FROM tarifas ta
              JOIN temporadas t ON t.id = ta.temporada_id
              JOIN categorias c ON c.id = ta.categoria_id
            """;

    private final JdbcClient jdbc;

    TarifaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Tarifas de la temporada en el orden del catálogo de categorías y después por concepto. */
    List<TarifaDto> listar(UUID ligaId, UUID temporadaId) {
        return jdbc.sql(SELECCION + """
                         WHERE ta.temporada_id = :temporadaId AND t.liga_id = :ligaId
                         ORDER BY c.orden, c.nombre, ta.concepto
                        """)
                .param("temporadaId", temporadaId)
                .param("ligaId", ligaId)
                .query(TarifaRepository::aDto)
                .list();
    }

    Optional<TarifaDto> buscar(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE ta.id = :id AND t.liga_id = :ligaId")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(TarifaRepository::aDto)
                .optional();
    }

    /** Lee la tarifa con candado para serializar dos ediciones simultáneas del mismo monto. */
    Optional<TarifaDto> bloquear(UUID ligaId, UUID id) {
        return jdbc.sql(SELECCION + " WHERE ta.id = :id AND t.liga_id = :ligaId FOR UPDATE OF ta")
                .param("id", id)
                .param("ligaId", ligaId)
                .query(TarifaRepository::aDto)
                .optional();
    }

    Optional<TemporadaTarifas> buscarTemporada(UUID ligaId, UUID temporadaId) {
        return temporada(ligaId, temporadaId, "");
    }

    /**
     * Lee la temporada con {@code FOR SHARE}: un cierre simultáneo espera a que se guarde la tarifa, y
     * la tarifa no se guarda sobre una temporada que se está cerrando (RF-003).
     */
    Optional<TemporadaTarifas> bloquearTemporada(UUID ligaId, UUID temporadaId) {
        return temporada(ligaId, temporadaId, " FOR SHARE");
    }

    private Optional<TemporadaTarifas> temporada(UUID ligaId, UUID temporadaId, String candado) {
        return jdbc.sql("SELECT id, nombre, estado FROM temporadas WHERE id = :id AND liga_id = :ligaId" + candado)
                .param("id", temporadaId)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new TemporadaTarifas(fila.getObject("id", UUID.class),
                        fila.getString("nombre"), EstadoTemporada.valueOf(fila.getString("estado"))))
                .optional();
    }

    /** Temporadas de la liga, de la más reciente a la más antigua (contexto de F-33, B-29.2). */
    List<TemporadaTarifas> temporadasDeLiga(UUID ligaId) {
        return jdbc.sql("""
                        SELECT id, nombre, estado FROM temporadas
                         WHERE liga_id = :ligaId
                         ORDER BY fecha_inicio DESC, nombre
                        """)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new TemporadaTarifas(fila.getObject("id", UUID.class),
                        fila.getString("nombre"), EstadoTemporada.valueOf(fila.getString("estado"))))
                .list();
    }

    /** Categorías asignadas a las temporadas de la liga, en el orden del catálogo. */
    List<CategoriaDeTemporada> categoriasDeTemporadas(UUID ligaId) {
        return jdbc.sql("""
                        SELECT ct.temporada_id, c.id, c.nombre
                          FROM categorias_temporada ct
                          JOIN categorias c ON c.id = ct.categoria_id AND c.liga_id = :ligaId
                          JOIN temporadas t ON t.id = ct.temporada_id AND t.liga_id = :ligaId
                         ORDER BY c.orden, c.nombre
                        """)
                .param("ligaId", ligaId)
                .query((fila, numero) -> new CategoriaDeTemporada(fila.getObject("temporada_id", UUID.class),
                        new TarifaDto.ReferenciaDto(fila.getObject("id", UUID.class), fila.getString("nombre"))))
                .list();
    }

    /** Verdadero si la categoría está asignada a la temporada ({@code categorias_temporada}). */
    boolean categoriaEnTemporada(UUID temporadaId, UUID categoriaId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                          SELECT 1 FROM categorias_temporada
                           WHERE temporada_id = :temporadaId AND categoria_id = :categoriaId)
                        """)
                .param("temporadaId", temporadaId)
                .param("categoriaId", categoriaId)
                .query(Boolean.class)
                .single();
    }

    boolean existe(UUID temporadaId, UUID categoriaId, ConceptoTarifa concepto) {
        return jdbc.sql("""
                        SELECT EXISTS (
                          SELECT 1 FROM tarifas
                           WHERE temporada_id = :temporadaId AND categoria_id = :categoriaId
                             AND concepto = CAST(:concepto AS concepto_tarifa))
                        """)
                .param("temporadaId", temporadaId)
                .param("categoriaId", categoriaId)
                .param("concepto", concepto.name())
                .query(Boolean.class)
                .single();
    }

    UUID insertar(UUID temporadaId, UUID categoriaId, ConceptoTarifa concepto, BigDecimal monto) {
        return jdbc.sql("""
                        INSERT INTO tarifas (temporada_id, categoria_id, concepto, monto)
                        VALUES (:temporadaId, :categoriaId, CAST(:concepto AS concepto_tarifa), :monto)
                        RETURNING id
                        """)
                .param("temporadaId", temporadaId)
                .param("categoriaId", categoriaId)
                .param("concepto", concepto.name())
                .param("monto", monto)
                .query(UUID.class)
                .single();
    }

    void actualizarMonto(UUID id, BigDecimal monto) {
        jdbc.sql("UPDATE tarifas SET monto = :monto WHERE id = :id")
                .param("id", id)
                .param("monto", monto)
                .update();
    }

    private static TarifaDto aDto(ResultSet fila, int numero) throws SQLException {
        return new TarifaDto(
                fila.getObject("id", UUID.class),
                new TarifaDto.ReferenciaDto(fila.getObject("temporada_id", UUID.class),
                        fila.getString("temporada_nombre")),
                new TarifaDto.ReferenciaDto(fila.getObject("categoria_id", UUID.class),
                        fila.getString("categoria_nombre")),
                ConceptoTarifa.valueOf(fila.getString("concepto")),
                fila.getBigDecimal("monto"),
                TarifaService.TARIFAS_DEMO);
    }

    /** Categoría asignada a una temporada. */
    record CategoriaDeTemporada(UUID temporadaId, TarifaDto.ReferenciaDto categoria) {
    }

    /** Temporada a la que pertenece la tarifa, con su estado para la regla de solo lectura (RF-003). */
    record TemporadaTarifas(UUID id, String nombre, EstadoTemporada estado) {
    }
}
