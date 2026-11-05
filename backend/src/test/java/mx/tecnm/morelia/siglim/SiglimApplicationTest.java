package mx.tecnm.morelia.siglim;

import static org.assertj.core.api.Assertions.assertThat;

import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * El esqueleto arranca con la configuración por variables de entorno y tiene la estructura de
 * paquetes de ADR-0006.
 *
 * <p>Los valores son de prueba: el pool de conexiones no abre ninguna conexión hasta la primera
 * consulta, así que no se necesita una base de datos.
 */
@SpringBootTest(properties = {
        "DB_URL=jdbc:postgresql://localhost:5432/siglim_prueba?currentSchema=siglim",
        "DB_USERNAME=prueba",
        "DB_PASSWORD=prueba",
        "SUPABASE_URL=https://proyecto-prueba.supabase.co",
        "SUPABASE_SECRET_KEY=clave-secreta-de-prueba",
        "SIGLIM_LIGA_ID=a0000000-0000-4000-8000-000000000001",
        "SIGLIM_S3_BUCKET=siglim-documentos-prueba",
        "AWS_REGION=us-east-2",
        "SIGLIM_CORS_ORIGEN=http://localhost:5173,https://siglim.ejemplo.mx"
})
class SiglimApplicationTest {

    @Autowired
    private SiglimPropiedades propiedades;

    @Test
    @DisplayName("T-02.1: el contexto carga y enlaza las variables de entorno a SiglimPropiedades")
    void cargaElContextoConLasVariablesDeEntorno() {
        assertThat(propiedades.supabase().url()).isEqualTo("https://proyecto-prueba.supabase.co");
        assertThat(propiedades.cors().origenes())
                .containsExactly("http://localhost:5173", "https://siglim.ejemplo.mx");
    }

    @ParameterizedTest(name = "mx.tecnm.morelia.siglim.{0}")
    @ValueSource(strings = {
            "compartido", "auditoria", "seguridad", "temporadas", "organizacion", "registros",
            "canchas", "programacion", "arbitraje", "tesoreria", "partidos", "disciplina",
            "competencias", "publicacion", "portal", "reportes", "notificaciones"})
    @DisplayName("ADR-0006: existe el paquete documentado de cada módulo")
    void existeElPaqueteDeCadaModulo(String modulo) throws ClassNotFoundException {
        Class<?> descriptor = Class.forName("mx.tecnm.morelia.siglim." + modulo + ".package-info");
        assertThat(descriptor.getPackage().getName()).isEqualTo("mx.tecnm.morelia.siglim." + modulo);
    }
}
