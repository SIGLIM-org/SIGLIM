package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Detección del tipo por firma binaria (ADR-0013 punto 4). Sin Spring ni red. */
class DetectorTipoArchivoTest {

    @Test
    @DisplayName("RF-108: reconoce JPEG, PNG, WebP y PDF por su firma")
    void aceptaLosCuatroTipos() {
        assertThat(DetectorTipoArchivo.detectar(ArchivosPrueba.JPEG)).contains(TipoArchivo.JPEG);
        assertThat(DetectorTipoArchivo.detectar(ArchivosPrueba.PNG)).contains(TipoArchivo.PNG);
        assertThat(DetectorTipoArchivo.detectar(ArchivosPrueba.WEBP)).contains(TipoArchivo.WEBP);
        assertThat(DetectorTipoArchivo.detectar(ArchivosPrueba.PDF)).contains(TipoArchivo.PDF);
    }

    @Test
    @DisplayName("RNF-018: rechaza un ejecutable renombrado a .pdf (cabecera MZ)")
    void rechazaEjecutable() {
        assertThat(DetectorTipoArchivo.detectar(ArchivosPrueba.EJECUTABLE)).isEmpty();
    }

    @Test
    @DisplayName("RNF-018: rechaza un ZIP (PK 03 04)")
    void rechazaZip() {
        assertThat(DetectorTipoArchivo.detectar(new byte[] {'P', 'K', 0x03, 0x04, 0x14, 0x00})).isEmpty();
    }

    @Test
    @DisplayName("RNF-018: rechaza un RIFF que no es WebP (p. ej. WAV)")
    void rechazaRiffSinWebp() {
        byte[] wav = {'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};

        assertThat(DetectorTipoArchivo.detectar(wav)).isEmpty();
    }

    @Test
    @DisplayName("RF-108: tolera arreglos vacíos y más cortos que la firma")
    void toleraArreglosCortos() {
        assertThat(DetectorTipoArchivo.detectar(new byte[0])).isEmpty();
        assertThat(DetectorTipoArchivo.detectar(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(DetectorTipoArchivo.detectar(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E'})).isEmpty();
        assertThat(DetectorTipoArchivo.detectar(null)).isEmpty();
    }
}
