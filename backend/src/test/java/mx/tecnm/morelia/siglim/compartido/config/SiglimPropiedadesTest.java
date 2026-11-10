package mx.tecnm.morelia.siglim.compartido.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Validación de la configuración propia: una variable faltante o mal escrita debe impedir que la
 * aplicación arranque, con un mensaje que nombre la variable (RNF-019).
 *
 * <p>Prueba unitaria sin Spring: usa el validador de Jakarta directamente.
 */
class SiglimPropiedadesTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void crearValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        fabrica.close();
    }

    private static SiglimPropiedades propiedades(String urlSupabase, List<String> origenes) {
        return new SiglimPropiedades(
                new SiglimPropiedades.Supabase(urlSupabase),
                new SiglimPropiedades.Cors(origenes),
                almacenamiento("siglim-documentos-dev-abc123", null, null, DataSize.ofMegabytes(5)),
                UUID.fromString("a0000000-0000-4000-8000-000000000001"));
    }

    private static SiglimPropiedades.Almacenamiento almacenamiento(String bucket, String llave, String secreto,
            DataSize tamanoMaximo) {
        return new SiglimPropiedades.Almacenamiento(bucket, "us-east-2", llave, secreto, Duration.ofSeconds(60),
                tamanoMaximo, null);
    }

    private static Set<String> mensajes(SiglimPropiedades.Almacenamiento almacenamiento) {
        return validador.validate(almacenamiento).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private static Set<String> mensajes(SiglimPropiedades propiedades) {
        return validador.validate(propiedades).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("RNF-019: acepta una configuración completa")
    void aceptaConfiguracionCompleta() {
        var valida = propiedades("https://abcd.supabase.co", List.of("http://localhost:5173"));

        assertThat(mensajes(valida)).isEmpty();
    }

    @Test
    @DisplayName("RNF-019: rechaza SUPABASE_URL vacía y lo dice")
    void rechazaUrlSupabaseVacia() {
        var sinUrl = propiedades("", List.of("http://localhost:5173"));

        assertThat(mensajes(sinUrl)).contains("Define SUPABASE_URL en backend/.env");
    }

    @Test
    @DisplayName("ADR-0003: rechaza SUPABASE_URL con diagonal final (rompería la ruta del JWKS)")
    void rechazaUrlSupabaseConDiagonalFinal() {
        var conDiagonal = propiedades("https://abcd.supabase.co/", List.of("http://localhost:5173"));

        assertThat(mensajes(conDiagonal))
                .contains("SUPABASE_URL debe ser https://<ref>.supabase.co, sin diagonal final");
    }

    @Test
    @DisplayName("Convenciones de API §5: rechaza SIGLIM_CORS_ORIGEN vacío")
    void rechazaCorsSinOrigenes() {
        var sinOrigenes = propiedades("https://abcd.supabase.co", List.of());

        assertThat(mensajes(sinOrigenes)).contains("Define SIGLIM_CORS_ORIGEN en backend/.env");
    }

    @Test
    @DisplayName("Convenciones de API §5: CORS no admite comodín ni orígenes con ruta")
    void rechazaOrigenComodinOConRuta() {
        var comodin = propiedades("https://abcd.supabase.co", List.of("*"));
        var conRuta = propiedades("https://abcd.supabase.co", List.of("http://localhost:5173/app"));

        assertThat(mensajes(comodin)).hasSize(1);
        assertThat(mensajes(conRuta)).hasSize(1);
    }

    @Test
    @DisplayName("ADR-0013: acepta el bucket con las dos llaves o sin ninguna")
    void aceptaAlmacenamientoConOSinLlaves() {
        assertThat(mensajes(almacenamiento("siglim-documentos-dev-abc123", "AKIAPRUEBA", "secreto",
                DataSize.ofMegabytes(5)))).isEmpty();
        assertThat(mensajes(almacenamiento("siglim-documentos-dev-abc123", "", "", DataSize.ofMegabytes(5))))
                .isEmpty();
    }

    @Test
    @DisplayName("ADR-0011: rechaza la configuración sin liga activa y lo dice")
    void rechazaSinLigaActiva() {
        var valida = propiedades("https://abcd.supabase.co", List.of("http://localhost:5173"));
        var sinLiga = new SiglimPropiedades(valida.supabase(), valida.cors(), valida.almacenamiento(), null);

        assertThat(mensajes(sinLiga)).contains("Define SIGLIM_LIGA_ID en backend/.env");
    }

    @Test
    @DisplayName("RNF-019: rechaza SIGLIM_S3_BUCKET vacío y lo dice")
    void rechazaSinBucket() {
        assertThat(mensajes(almacenamiento("", null, null, DataSize.ofMegabytes(5))))
                .contains("Define SIGLIM_S3_BUCKET en backend/.env");
    }

    @Test
    @DisplayName("ADR-0013: rechaza un nombre de bucket que S3 no admite")
    void rechazaBucketInvalido() {
        assertThat(mensajes(almacenamiento("Siglim_Documentos", null, null, DataSize.ofMegabytes(5)))).hasSize(1);
    }

    @Test
    @DisplayName("RNF-019: rechaza solo una de las dos llaves de AWS")
    void rechazaUnaSolaLlave() {
        String mensaje = "Define AWS_ACCESS_KEY_ID y AWS_SECRET_ACCESS_KEY juntas, o deja las dos vacías para usar ~/.aws";

        assertThat(mensajes(almacenamiento("siglim-documentos-dev-abc123", "AKIAPRUEBA", "", DataSize.ofMegabytes(5))))
                .contains(mensaje);
        assertThat(mensajes(almacenamiento("siglim-documentos-dev-abc123", null, "secreto", DataSize.ofMegabytes(5))))
                .contains(mensaje);
    }

    @Test
    @DisplayName("ADR-0013: rechaza un tamaño máximo mayor que el CHECK de siglim.documentos (5 MB)")
    void rechazaTamanoMayorQueLaBd() {
        assertThat(mensajes(almacenamiento("siglim-documentos-dev-abc123", null, null, DataSize.ofMegabytes(6))))
                .hasSize(1);
        assertThat(mensajes(almacenamiento("siglim-documentos-dev-abc123", null, null, DataSize.ofBytes(5_242_881))))
                .hasSize(1);
    }

    @Test
    @DisplayName("ADR-0013: la vigencia del enlace va de 1 s a 15 min")
    void rechazaVigenciaFueraDeRango() {
        var cero = new SiglimPropiedades.Almacenamiento("siglim-documentos-dev-abc123", "us-east-2", null, null,
                Duration.ZERO, DataSize.ofMegabytes(5), null);
        var unaHora = new SiglimPropiedades.Almacenamiento("siglim-documentos-dev-abc123", "us-east-2", null, null,
                Duration.ofHours(1), DataSize.ofMegabytes(5), null);

        assertThat(mensajes(cero)).hasSize(1);
        assertThat(mensajes(unaHora)).hasSize(1);
    }
}
