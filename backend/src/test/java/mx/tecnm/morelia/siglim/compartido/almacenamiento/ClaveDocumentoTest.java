package mx.tecnm.morelia.siglim.compartido.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Clave del objeto en S3 (ADR-0013 punto 3). */
class ClaveDocumentoTest {

    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID ENTIDAD = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DOCUMENTO = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    @DisplayName("ADR-0013: liga/{ligaId}/{carpeta}/{entidadId}/{documentoId}.{ext}, solo con identificadores")
    void formatoExacto() {
        var clave = new ClaveDocumento(LIGA, CarpetaDocumento.REGISTROS, ENTIDAD, DOCUMENTO);

        assertThat(clave.valor(TipoArchivo.PDF)).isEqualTo(
                "liga/a0000000-0000-4000-8000-000000000001/registros/11111111-1111-4111-8111-111111111111/"
                        + "22222222-2222-4222-8222-222222222222.pdf");
        assertThat(clave.valor(TipoArchivo.JPEG)).endsWith(".jpg");
    }

    @ParameterizedTest
    @CsvSource({"REGISTROS,registros", "TUTORES,tutores", "AFILIACIONES,afiliaciones", "EVIDENCIAS,evidencias",
            "COMPROBANTES,comprobantes"})
    @DisplayName("ADR-0013: cada carpeta usa su segmento en minúsculas")
    void segmentoDeCadaCarpeta(CarpetaDocumento carpeta, String segmento) {
        assertThat(new ClaveDocumento(LIGA, carpeta, ENTIDAD, DOCUMENTO).valor(TipoArchivo.PNG))
                .contains("/" + segmento + "/");
    }

    @Test
    @DisplayName("ADR-0013: todos los componentes son obligatorios")
    void rechazaNulos() {
        assertThatThrownBy(() -> new ClaveDocumento(LIGA, CarpetaDocumento.TUTORES, null, DOCUMENTO))
                .isInstanceOf(NullPointerException.class);
    }
}
