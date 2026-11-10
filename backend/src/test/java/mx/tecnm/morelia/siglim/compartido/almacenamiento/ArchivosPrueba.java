package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import java.nio.charset.StandardCharsets;

/** Contenidos mínimos con la firma binaria de cada tipo, para las pruebas. */
final class ArchivosPrueba {

    static final byte[] PDF = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n"
            .getBytes(StandardCharsets.US_ASCII);
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    /** Ejecutable de Windows (cabecera MZ) que alguien renombró a {@code .pdf}. */
    static final byte[] EJECUTABLE = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00};

    private ArchivosPrueba() {
    }
}
