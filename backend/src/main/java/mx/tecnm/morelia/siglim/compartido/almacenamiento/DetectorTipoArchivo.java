package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Detecta el tipo de archivo por su firma binaria (los primeros bytes), sin mirar el nombre ni el
 * {@code Content-Type} que envía el cliente: un ejecutable renombrado a {@code .pdf} se rechaza
 * (ADR-0013 punto 4).
 *
 * <p>Refs: T-04, ADR-0013, RF-108
 */
public final class DetectorTipoArchivo {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private DetectorTipoArchivo() {
    }

    /** El tipo permitido que corresponde a la firma, o vacío si no es ninguno o el arreglo es corto. */
    public static Optional<TipoArchivo> detectar(byte[] contenido) {
        if (contenido == null) {
            return Optional.empty();
        }
        if (empiezaCon(contenido, 0, JPEG)) {
            return Optional.of(TipoArchivo.JPEG);
        }
        if (empiezaCon(contenido, 0, PNG)) {
            return Optional.of(TipoArchivo.PNG);
        }
        if (empiezaCon(contenido, 0, RIFF) && empiezaCon(contenido, 8, WEBP)) {
            return Optional.of(TipoArchivo.WEBP);
        }
        if (empiezaCon(contenido, 0, PDF)) {
            return Optional.of(TipoArchivo.PDF);
        }
        return Optional.empty();
    }

    private static boolean empiezaCon(byte[] contenido, int desde, byte[] firma) {
        if (contenido.length < desde + firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (contenido[desde + i] != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
