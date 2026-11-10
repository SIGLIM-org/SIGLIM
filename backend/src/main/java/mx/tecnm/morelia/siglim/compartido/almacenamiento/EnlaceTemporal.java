package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import java.time.OffsetDateTime;

/**
 * URL prefirmada de lectura de vigencia corta (ADR-0013 punto 6). No se guarda ni se registra en
 * bitácoras (RNF-011); la respuesta que la lleve debe ir con {@code Cache-Control: no-store}.
 *
 * @param url      URL prefirmada de {@code GET}
 * @param expiraEn momento en que deja de servir
 */
public record EnlaceTemporal(String url, OffsetDateTime expiraEn) {
}
