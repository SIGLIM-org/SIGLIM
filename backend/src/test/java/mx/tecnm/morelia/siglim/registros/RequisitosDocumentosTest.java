package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.Avance;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.DocumentoRegistro;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.RequisitoDocumento;
import mx.tecnm.morelia.siglim.registros.RequisitosRegistroDto.RequisitoDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cálculo de requisitos de documentos de un registro (RF-108, DEC-92, DEC-96), sin Spring ni BD. */
class RequisitosDocumentosTest {

    static final TipoDocumentoDto CONSTANCIA = new TipoDocumentoDto(1, "CONSTANCIA_ESTUDIOS", "Constancia de estudios");
    static final TipoDocumentoDto IDENTIFICACION = new TipoDocumentoDto(2, "IDENTIFICACION", "Identificación");
    static final TipoDocumentoDto FOTOGRAFIA = new TipoDocumentoDto(5, "FOTOGRAFIA", "Fotografía");
    static final TipoDocumentoDto INE_TUTOR = new TipoDocumentoDto(6, "INE_TUTOR", "Credencial INE del tutor");
    /** Los requisitos de INFANTIL de la semilla 12.6. */
    static final List<RequisitoDocumento> INFANTIL = List.of(
            new RequisitoDocumento(CONSTANCIA, 1, false, false),
            new RequisitoDocumento(IDENTIFICACION, 1, false, false),
            new RequisitoDocumento(FOTOGRAFIA, 2, false, true),
            new RequisitoDocumento(INE_TUTOR, 1, true, false));
    private static final UUID REGISTRO = UUID.randomUUID();
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-11-12T12:00:00-06:00");

    @Test
    @DisplayName("RN-055 · DEC-92: el INE del tutor solo se exige a un menor")
    void soloMenores() {
        assertThat(RequisitosDocumentos.aplicables(INFANTIL, true)).hasSize(4);
        assertThat(RequisitosDocumentos.aplicables(INFANTIL, false)).extracting(r -> r.tipoDocumento().codigo())
                .containsExactly("CONSTANCIA_ESTUDIOS", "IDENTIFICACION", "FOTOGRAFIA");
        assertThat(RequisitosDocumentos.exigido(INFANTIL, false, INE_TUTOR.id())).isNull();
        assertThat(RequisitosDocumentos.exigido(INFANTIL, true, INE_TUTOR.id()).cantidad()).isEqualTo(1);
        assertThat(RequisitosDocumentos.exigido(INFANTIL, true, 4)).as("tipo de otra categoría").isNull();
    }

    @Test
    @DisplayName("RF-108 · DEC-96: sin documentos faltan todos, no está completo ni listo para validar")
    void sinDocumentos() {
        Avance avance = RequisitosDocumentos.calcular(INFANTIL, true, List.of());

        assertThat(avance.requisitos()).extracting(RequisitoDto::faltan).containsExactly(1, 1, 2, 1);
        assertThat(avance.requisitos().get(2).esDemo()).isTrue();
        assertThat(avance.completo()).isFalse();
        assertThat(avance.listoParaValidar()).isFalse();
    }

    @Test
    @DisplayName("DEC-96: un rechazado se muestra pero no cuenta como cargado")
    void rechazadoNoCuenta() {
        DocumentoRegistro rechazada = documento(FOTOGRAFIA, EstadoValidacion.RECHAZADO, null, 0);
        DocumentoRegistro pendiente = documento(FOTOGRAFIA, EstadoValidacion.PENDIENTE, null, 1);

        RequisitoDto fotografia = RequisitosDocumentos.calcular(INFANTIL, false, List.of(pendiente, rechazada))
                .requisitos().get(2);

        assertThat(fotografia.cargados()).isEqualTo(1);
        assertThat(fotografia.faltan()).isEqualTo(1);
        assertThat(fotografia.documentos()).as("por fecha de carga")
                .containsExactly(rechazada.dto(), pendiente.dto());
        assertThat(RequisitosDocumentos.vigentesDelTipo(List.of(pendiente, rechazada), FOTOGRAFIA.id()))
                .as("el rechazado ocupa su lugar en el cupo").isEqualTo(2);
    }

    @Test
    @DisplayName("DEC-93 · DEC-96: un documento reemplazado no cuenta ni se lista")
    void reemplazadoNoCuenta() {
        DocumentoRegistro reemplazada = documento(FOTOGRAFIA, EstadoValidacion.RECHAZADO, UUID.randomUUID(), 0);
        DocumentoRegistro vigente = documento(FOTOGRAFIA, EstadoValidacion.PENDIENTE, null, 1);

        RequisitoDto fotografia = RequisitosDocumentos.calcular(INFANTIL, false, List.of(reemplazada, vigente))
                .requisitos().get(2);

        assertThat(fotografia.documentos()).containsExactly(vigente.dto());
        assertThat(fotografia.cargados()).isEqualTo(1);
        assertThat(RequisitosDocumentos.vigentesDelTipo(List.of(reemplazada, vigente), FOTOGRAFIA.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("DEC-96: completo con todo cargado; listo para validar solo con todo aprobado")
    void completoYListo() {
        List<DocumentoRegistro> cargados = List.of(
                documento(CONSTANCIA, EstadoValidacion.APROBADO, null, 0),
                documento(IDENTIFICACION, EstadoValidacion.APROBADO, null, 1),
                documento(FOTOGRAFIA, EstadoValidacion.APROBADO, null, 2),
                documento(FOTOGRAFIA, EstadoValidacion.PENDIENTE, null, 3));

        Avance adulto = RequisitosDocumentos.calcular(INFANTIL, false, cargados);
        assertThat(adulto.completo()).isTrue();
        assertThat(adulto.listoParaValidar()).isFalse();
        assertThat(adulto.requisitos().get(2).aprobados()).isEqualTo(1);

        Avance menor = RequisitosDocumentos.calcular(INFANTIL, true, cargados);
        assertThat(menor.completo()).as("al menor le falta el INE del tutor").isFalse();

        List<DocumentoRegistro> aprobados = List.of(
                documento(CONSTANCIA, EstadoValidacion.APROBADO, null, 0),
                documento(IDENTIFICACION, EstadoValidacion.APROBADO, null, 1),
                documento(FOTOGRAFIA, EstadoValidacion.APROBADO, null, 2),
                documento(FOTOGRAFIA, EstadoValidacion.APROBADO, null, 3),
                documento(INE_TUTOR, EstadoValidacion.APROBADO, null, 4));
        Avance listo = RequisitosDocumentos.calcular(INFANTIL, true, aprobados);
        assertThat(listo.completo()).isTrue();
        assertThat(listo.listoParaValidar()).isTrue();
        assertThat(listo.requisitos()).extracting(RequisitoDto::faltan).containsOnly(0);
    }

    static DocumentoRegistro documento(TipoDocumentoDto tipo, EstadoValidacion estado, UUID reemplazadoPor, int minuto) {
        DocumentoDto dto = new DocumentoDto(UUID.randomUUID(), REGISTRO, tipo, "image/jpeg", 1024, estado, null, null,
                null, null, AHORA.plusMinutes(minuto));
        return new DocumentoRegistro(dto, UUID.randomUUID(), reemplazadoPor, "siglim-documentos-prueba",
                "liga/x/registros/" + REGISTRO + "/" + dto.id() + ".jpg");
    }
}
