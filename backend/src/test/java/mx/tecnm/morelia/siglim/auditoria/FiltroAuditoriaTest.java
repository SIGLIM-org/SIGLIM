package mx.tecnm.morelia.siglim.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import mx.tecnm.morelia.siglim.compartido.error.ErroresTecnicos;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/** Validación de los filtros de la consulta de auditoría, sin Spring ni base de datos. */
class FiltroAuditoriaTest {

    private static final String ORDEN = "ocurridoEn,desc";
    private static final OffsetDateTime FECHA = OffsetDateTime.parse("2030-03-10T10:00:00-06:00");

    private static FiltroAuditoria pagina(int pagina, int tamano) {
        return new FiltroAuditoria(null, List.of(), List.of(), null, null, pagina, tamano, ORDEN);
    }

    private static FiltroAuditoria modulo(String modulo) {
        return new FiltroAuditoria(null, List.of(modulo), List.of(), null, null, 0, 50, ORDEN);
    }

    private static FiltroAuditoria accion(String accion) {
        return new FiltroAuditoria(null, List.of(), List.of(accion), null, null, 0, 50, ORDEN);
    }

    private static FiltroAuditoria rango(OffsetDateTime desde, OffsetDateTime hasta) {
        return new FiltroAuditoria(null, List.of(), List.of(), desde, hasta, 0, 50, ORDEN);
    }

    private static FiltroAuditoria orden(String orden) {
        return new FiltroAuditoria(null, List.of(), List.of(), null, null, 0, 50, orden);
    }

    private static void esInvalido(FiltroAuditoria filtro) {
        assertThatThrownBy(filtro::validar).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(error.codigo()).isEqualTo(ErroresTecnicos.VALIDACION);
        });
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0, 101", "-1, 50"})
    @DisplayName("RF-134: un tamaño fuera de 1 a 100 o una página negativa responde 400 VALIDACION")
    void paginaInvalida(int pagina, int tamano) {
        esInvalido(pagina(pagina, tamano));
    }

    @Test
    @DisplayName("RF-134: los límites 1 y 100 del tamaño son válidos")
    void paginaEnLimites() {
        assertThatCode(() -> pagina(0, 1).validar()).doesNotThrowAnyException();
        assertThatCode(() -> pagina(0, 100).validar()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"M-1", "m-01", "M-001", "M01"})
    @DisplayName("RF-134: un módulo sin el formato M-xx responde 400 VALIDACION")
    void moduloInvalido(String valor) {
        esInvalido(modulo(valor));
    }

    @ParameterizedTest
    @ValueSource(strings = {"aprobar_cedula", "APROBAR CEDULA", "1APROBAR", "APROBAR-CEDULA"})
    @DisplayName("RF-134: una acción con minúsculas, espacios u otro formato responde 400 VALIDACION")
    void accionInvalida(String valor) {
        esInvalido(accion(valor));
    }

    @Test
    @DisplayName("RF-134: la acción admite hasta 80 caracteres")
    void longitudDeAccion() {
        assertThatCode(() -> accion("A".repeat(80)).validar()).doesNotThrowAnyException();
        esInvalido(accion("A".repeat(81)));
    }

    @Test
    @DisplayName("RF-134: una acción bien formada se acepta aunque no exista: no hay catálogo fijo")
    void accionBienFormada() {
        assertThatCode(() -> accion("ACCION_QUE_NO_EXISTE_2").validar()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RF-134: desde igual o posterior a hasta responde 400 VALIDACION; compara el instante")
    void rangoInvalido() {
        esInvalido(rango(FECHA, FECHA));
        esInvalido(rango(FECHA.plusHours(1), FECHA));
        esInvalido(rango(OffsetDateTime.parse("2030-03-10T10:00:00-06:00"),
                OffsetDateTime.parse("2030-03-10T16:00:00Z")));
    }

    @Test
    @DisplayName("RF-134: desde y hasta son opcionales y no tienen rango máximo")
    void rangoOpcional() {
        assertThatCode(() -> rango(null, null).validar()).doesNotThrowAnyException();
        assertThatCode(() -> rango(FECHA, null).validar()).doesNotThrowAnyException();
        assertThatCode(() -> rango(null, FECHA).validar()).doesNotThrowAnyException();
        assertThatCode(() -> rango(FECHA, FECHA.plusYears(20)).validar()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"accion,asc", "id,desc", "ocurridoEn,", "ocurridoEn,abajo", "ocurridoEn,desc,accion,asc", ""})
    @DisplayName("RF-134: un orden por otro campo o mal formado responde 400 VALIDACION")
    void ordenInvalido(String valor) {
        esInvalido(orden(valor));
    }

    @Test
    @DisplayName("RF-134: el orden desempata por id en la misma dirección")
    void ordenSql() {
        assertThat(orden("ocurridoEn,desc").ordenSql()).isEqualTo("a.ocurrido_en DESC, a.id DESC");
        assertThat(orden("ocurridoEn,asc").ordenSql()).isEqualTo("a.ocurrido_en ASC, a.id ASC");
    }

    @Test
    @DisplayName("RF-134: sin dirección el orden es ascendente, igual que los demás listados")
    void ordenSinDireccion() {
        assertThatCode(() -> orden("ocurridoEn").validar()).doesNotThrowAnyException();
        assertThat(orden("ocurridoEn").ordenSql()).isEqualTo("a.ocurrido_en ASC, a.id ASC");
    }

    @Test
    @DisplayName("RF-134: la dirección del orden admite cualquier combinación de mayúsculas")
    void direccionSinImportarMayusculas() {
        assertThat(orden("ocurridoEn,DESC").ordenSql()).isEqualTo("a.ocurrido_en DESC, a.id DESC");
        assertThat(orden("ocurridoEn,Asc").ordenSql()).isEqualTo("a.ocurrido_en ASC, a.id ASC");
    }

    @Test
    @DisplayName("RF-134: el desplazamiento sale de la página y el tamaño")
    void desplazamiento() {
        assertThat(pagina(3, 50).desplazamiento()).isEqualTo(150);
    }

    @Test
    @DisplayName("RF-134: los valores separados por coma se parten y se descartan los vacíos")
    void separarValores() {
        assertThat(FiltroAuditoria.separarValores("M-08, M-06,,")).containsExactly("M-08", "M-06");
        assertThat(FiltroAuditoria.separarValores(null)).isEmpty();
        assertThat(FiltroAuditoria.separarValores(" , ")).isEmpty();
    }
}
