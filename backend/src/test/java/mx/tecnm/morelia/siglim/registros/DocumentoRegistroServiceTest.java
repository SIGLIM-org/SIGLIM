package mx.tecnm.morelia.siglim.registros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mx.tecnm.morelia.siglim.auditoria.AuditoriaService;
import mx.tecnm.morelia.siglim.auditoria.EventoAuditoria;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.AlmacenamientoDocumentos;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.AlmacenamientoEnMemoriaPrueba.AlmacenamientoEnMemoria;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.ArchivoAlmacenado;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.CarpetaDocumento;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.ClaveDocumento;
import mx.tecnm.morelia.siglim.compartido.almacenamiento.EnlaceTemporal;
import mx.tecnm.morelia.siglim.compartido.config.SiglimPropiedades;
import mx.tecnm.morelia.siglim.compartido.contexto.AutenticacionSiglim;
import mx.tecnm.morelia.siglim.compartido.error.ArchivoRechazadoException;
import mx.tecnm.morelia.siglim.compartido.error.RecursoNoEncontradoException;
import mx.tecnm.morelia.siglim.compartido.error.ReglaNegocioException;
import mx.tecnm.morelia.siglim.registros.DocumentoDto.TipoDocumentoDto;
import mx.tecnm.morelia.siglim.registros.DocumentoRegistroRepository.RegistroDeDocumentos;
import mx.tecnm.morelia.siglim.registros.RequisitosDocumentos.DocumentoRegistro;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion;
import mx.tecnm.morelia.siglim.seguridad.Autorizacion.AlcanceAutorizacion;
import mx.tecnm.morelia.siglim.seguridad.ObjetivoAutorizacion;
import mx.tecnm.morelia.siglim.temporadas.EstadoTemporada;
import mx.tecnm.morelia.siglim.temporadas.TipoCategoria;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.unit.DataSize;

/** Documentos de un registro (RF-108, RF-109, DEC-91 a DEC-96), sin Spring ni BD. */
class DocumentoRegistroServiceTest {

    private static final String CREAR = "registros.documento:CREAR";
    private static final String VALIDAR = "registros.documento:VALIDAR";
    private static final String CONSULTAR_DOCUMENTO = "registros.documento:CONSULTAR";
    private static final String CONSULTAR_REGISTRO = "registros.registro:CONSULTAR";
    private static final UUID LIGA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID REGISTRO = UUID.fromString("80000000-0000-4000-8000-000000000001");
    private static final UUID PERSONA = UUID.fromString("21000000-0000-4000-8000-000000000001");
    private static final UUID CLUB = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID CATEGORIA = UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final UUID PERFIL = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID OTRO_PERFIL = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID DOCUMENTO = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final Instant AHORA = Instant.parse("2026-11-12T18:00:00Z");
    private static final LocalDate ALTA = LocalDate.parse("2026-11-11");
    private static final TipoDocumentoDto FOTOGRAFIA = RequisitosDocumentosTest.FOTOGRAFIA;
    private static final TipoDocumentoDto INE_TUTOR = RequisitosDocumentosTest.INE_TUTOR;
    private static final TipoDocumentoDto INE = new TipoDocumentoDto(3, "INE", "Credencial INE");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F'};
    private static final String CLAVE = "liga/" + LIGA + "/registros/" + REGISTRO + "/nuevo.jpg";
    private static final ArchivoAlmacenado GUARDADO = new ArchivoAlmacenado("siglim-documentos-prueba", CLAVE,
            "https://siglim-documentos-prueba.s3.us-east-2.amazonaws.com/" + CLAVE, "image/jpeg", JPEG.length,
            "a".repeat(64));
    /** Lo que nunca puede aparecer en la auditoría (DEC-95). */
    private static final List<String> SECRETOS = List.of("url", "s3Clave", "s3Bucket", "sha256", "amazonaws",
            "X-Amz", CLAVE, "a".repeat(64));

    private final DocumentoRegistroRepository repositorio = mock(DocumentoRegistroRepository.class);
    private final AlmacenamientoDocumentos almacenamiento = mock(AlmacenamientoDocumentos.class);
    private final Autorizacion autorizacion = mock(Autorizacion.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final Map<UUID, DocumentoRegistro> documentos = new HashMap<>();

    private DocumentoRegistroService servicio = servicio(almacenamiento);

    @BeforeEach
    void preparar() {
        Jwt token = Jwt.withTokenValue("token-de-prueba").header("alg", "ES256")
                .subject(UUID.randomUUID().toString()).issuedAt(AHORA).expiresAt(AHORA.plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new AutenticacionSiglim(token, PERFIL));
        given(autorizacion.alcance(any())).willReturn(new AlcanceAutorizacion(false, Set.of(CLUB), false));
        RegistroDeDocumentos pendiente = registro(EstadoRegistro.PENDIENTE_VALIDACION, EstadoTemporada.ACTIVA);
        given(repositorio.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.of(pendiente));
        given(repositorio.bloquearRegistro(LIGA, REGISTRO)).willReturn(Optional.of(pendiente));
        given(repositorio.requisitos(LIGA, TipoCategoria.INFANTIL)).willReturn(RequisitosDocumentosTest.INFANTIL);
        given(repositorio.esMenor(PERSONA, ALTA)).willReturn(Optional.of(true));
        given(repositorio.vigentes(REGISTRO)).willReturn(List.of());
        for (TipoDocumentoDto tipo : List.of(FOTOGRAFIA, INE_TUTOR, INE, RequisitosDocumentosTest.CONSTANCIA,
                RequisitosDocumentosTest.IDENTIFICACION)) {
            given(repositorio.tipoDocumento(tipo.id())).willReturn(Optional.of(tipo));
        }
        given(repositorio.tipoDocumento(99)).willReturn(Optional.empty());
        given(almacenamiento.guardar(any(), any())).willReturn(GUARDADO);
        // Un documento conocido se devuelve tal cual; uno recién insertado, como PENDIENTE.
        given(repositorio.buscarDocumento(eq(LIGA), any())).willAnswer(llamada -> {
            UUID id = llamada.getArgument(1);
            return Optional.ofNullable(documentos.getOrDefault(id, null))
                    .or(() -> Optional.of(documento(id, FOTOGRAFIA, EstadoValidacion.PENDIENTE, PERFIL, null)));
        });
        given(repositorio.marcarReemplazado(any(), any())).willReturn(true);
        given(repositorio.resolver(any(), any(), any(), any(), any())).willReturn(true);
    }

    @AfterEach
    void cerrarSesion() {
        SecurityContextHolder.clearContext();
    }

    // ── Carga (RF-108, DEC-92) ──────────────────────────────────────────────────────────────────

    @Nested
    class Cargar {

        @Test
        @DisplayName("RF-108 · RN-055: la Directiva carga el INE del tutor de un menor; queda PENDIENTE en REGISTROS y se audita")
        void cargaIneDelTutor() {
            DocumentoDto cargado = servicio.cargar(REGISTRO, INE_TUTOR.id(), JPEG);

            assertThat(cargado.estadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
            verify(autorizacion).alcance(CREAR);
            verify(autorizacion).exigir(CREAR, ObjetivoAutorizacion.deClubes(CLUB));
            verify(repositorio).bloquearRegistro(LIGA, REGISTRO);
            ArgumentCaptor<ClaveDocumento> clave = ArgumentCaptor.forClass(ClaveDocumento.class);
            verify(almacenamiento).guardar(clave.capture(), eq(JPEG));
            assertThat(clave.getValue().ligaId()).isEqualTo(LIGA);
            assertThat(clave.getValue().carpeta()).isEqualTo(CarpetaDocumento.REGISTROS);
            assertThat(clave.getValue().entidadId()).isEqualTo(REGISTRO);
            UUID id = clave.getValue().documentoId();
            verify(repositorio).insertar(id, INE_TUTOR.id(), REGISTRO, GUARDADO, PERFIL);
            EventoAuditoria evento = auditado();
            assertThat(evento.modulo()).isEqualTo("M-04");
            assertThat(evento.accion()).isEqualTo("CARGAR_DOCUMENTO");
            assertThat(evento.entidad()).isEqualTo("documentos");
            assertThat(evento.entidadId()).isEqualTo(id.toString());
            assertThat(evento.motivo()).isNull();
            assertThat(evento.detalle()).isEqualTo(Map.of("registroId", REGISTRO.toString(), "tipoDocumento",
                    "INE_TUTOR", "tipoMime", "image/jpeg", "tamanoBytes", JPEG.length));
        }

        @Test
        @DisplayName("VAL-015: sin el permiso en ningún ámbito responde 403 sin leer el registro")
        void sinPermiso() {
            given(autorizacion.alcance(CREAR)).willThrow(Autorizacion.sinPermiso());

            esperar(() -> servicio.cargar(REGISTRO, FOTOGRAFIA.id(), JPEG), HttpStatus.FORBIDDEN, "VAL-015");

            verifyNoInteractions(repositorio, almacenamiento, auditoria);
        }

        @Test
        @DisplayName("DEC-91: sin archivo o sin tipoDocumentoId responde 400 VALIDACION después del permiso")
        void faltanDatos() {
            esperar(() -> servicio.cargar(REGISTRO, FOTOGRAFIA.id(), null), HttpStatus.BAD_REQUEST, "VALIDACION");
            esperar(() -> servicio.cargar(REGISTRO, null, JPEG), HttpStatus.BAD_REQUEST, "VALIDACION");

            verify(autorizacion, times(2)).alcance(CREAR);
            verifyNoInteractions(repositorio, almacenamiento);
        }

        @Test
        @DisplayName("VAL-024: un registro inexistente responde 404 sin exigir el permiso sobre un club")
        void registroInexistente() {
            given(repositorio.buscarRegistro(LIGA, REGISTRO)).willReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.cargar(REGISTRO, FOTOGRAFIA.id(), JPEG))
                    .isInstanceOf(RecursoNoEncontradoException.class);

            verify(autorizacion, never()).exigir(any(), any());
        }

        @Test
        @DisplayName("VAL-024: un registro de otro club responde 404 igual que uno inexistente, sin bloquearlo ni subir")
        void fueraDeAmbito() {
            willThrow(Autorizacion.fueraDeAmbito(CREAR))
                    .given(autorizacion).exigir(CREAR, ObjetivoAutorizacion.deClubes(CLUB));

            assertThatThrownBy(() -> servicio.cargar(REGISTRO, FOTOGRAFIA.id(), JPEG))
                    .isInstanceOf(RecursoNoEncontradoException.class);

            verify(repositorio, never()).bloquearRegistro(any(), any());
            verifyNoInteractions(almacenamiento, auditoria);
        }

        @Test
        @DisplayName("RF-003: la temporada CERRADA responde 409 aunque el registro ya no esté pendiente")
        void temporadaCerrada() {
            given(repositorio.bloquearRegistro(LIGA, REGISTRO))
                    .willReturn(Optional.of(registro(EstadoRegistro.ACTIVO, EstadoTemporada.CERRADA)));

            esperar(() -> servicio.cargar(REGISTRO, FOTOGRAFIA.id(), JPEG), HttpStatus.CONFLICT, "RF-003");
            verifyNoInteractions(almacenamiento);
        }

        @Test
        @DisplayName("RF-017 · DEC-93: un registro que no está PENDIENTE_VALIDACION responde 409 (el estado leído con el bloqueo)")
        void registroNoPendiente() {
            given(repositorio.bloquearRegistro(LIGA, REGISTRO))
                    .willReturn(Optional.of(registro(EstadoRegistro.ACTIVO, EstadoTemporada.ACTIVA)));

            esperar(() -> servicio.cargar(REGISTRO, FOTOGRAFIA.id(), JPEG), HttpStatus.CONFLICT, "RF-017");
            verifyNoInteractions(almacenamiento);
        }

        @Test
        @DisplayName("DEC-92: un tipoDocumentoId inexistente responde 422 REFERENCIA_INVALIDA")
        void tipoInexistente() {
            esperar(() -> servicio.cargar(REGISTRO, 99L, JPEG), HttpStatus.UNPROCESSABLE_CONTENT, "REFERENCIA_INVALIDA");
            verifyNoInteractions(almacenamiento);
        }

        @Test
        @DisplayName("RF-108 · DEC-92: el INE del tutor de un adulto responde 422 RF-108 con un mensaje que lo explica")
        void ineDelTutorDeUnAdulto() {
            given(repositorio.esMenor(PERSONA, ALTA)).willReturn(Optional.of(false));

            assertThatThrownBy(() -> servicio.cargar(REGISTRO, INE_TUTOR.id(), JPEG))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                        assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                        assertThat(error.codigo()).isEqualTo("RF-108");
                        assertThat(error.getMessage()).contains("no era menor de edad");
                    });
            verify(repositorio).esMenor(PERSONA, ALTA);
            verifyNoInteractions(almacenamiento);
        }

        @Test
        @DisplayName("RF-108 · DEC-92: un tipo de otra categoría (INE en INFANTIL) responde 422 RF-108")
        void tipoDeOtraCategoria() {
            esperar(() -> servicio.cargar(REGISTRO, INE.id(), JPEG), HttpStatus.UNPROCESSABLE_CONTENT, "RF-108");
            verifyNoInteractions(almacenamiento);
        }

        @Test
        @DisplayName("DEC-92: con el cupo lleno (un rechazado también ocupa lugar) responde 409 DUPLICADO y pide reemplazar")
        void cupoLleno() {
            given(repositorio.vigentes(REGISTRO)).willReturn(List.of(
                    documento(UUID.randomUUID(), FOTOGRAFIA, EstadoValidacion.PENDIENTE, PERFIL, null),
                    documento(UUID.randomUUID(), FOTOGRAFIA, EstadoValidacion.RECHAZADO, PERFIL, null)));

            assertThatThrownBy(() -> servicio.cargar(REGISTRO, FOTOGRAFIA.id(), JPEG))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                        assertThat(error.estado()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(error.codigo()).isEqualTo("DUPLICADO");
                        assertThat(error.getMessage()).contains("reemplázalo");
                    });
            verifyNoInteractions(almacenamiento);
        }

        @Test
        @DisplayName("DEC-92: con un lugar libre del cupo sí se carga la segunda fotografía")
        void cupoConLugar() {
            given(repositorio.vigentes(REGISTRO)).willReturn(List.of(
                    documento(UUID.randomUUID(), FOTOGRAFIA, EstadoValidacion.APROBADO, OTRO_PERFIL, null)));

            servicio.cargar(REGISTRO, FOTOGRAFIA.id(), JPEG);

            verify(repositorio).insertar(any(), eq(FOTOGRAFIA.id()), eq(REGISTRO), eq(GUARDADO), eq(PERFIL));
        }

        @Test
        @DisplayName("RF-108: 413 y 415 del almacenamiento llegan sin insertar ni auditar")
        void archivoRechazado() {
            given(almacenamiento.guardar(any(), any()))
                    .willThrow(ArchivoRechazadoException.demasiadoGrande(DataSize.ofMegabytes(5)))
                    .willThrow(ArchivoRechazadoException.tipoNoPermitido());

            assertThatThrownBy(() -> servicio.cargar(REGISTRO, FOTOGRAFIA.id(), JPEG))
                    .isInstanceOfSatisfying(ArchivoRechazadoException.class,
                            error -> assertThat(error.estado()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE));
            assertThatThrownBy(() -> servicio.cargar(REGISTRO, FOTOGRAFIA.id(), JPEG))
                    .isInstanceOfSatisfying(ArchivoRechazadoException.class,
                            error -> assertThat(error.estado()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));

            verify(repositorio, never()).insertar(any(), anyLong(), any(), any(), any());
            verifyNoInteractions(auditoria);
        }
    }

    // ── Reemplazo (DEC-93) ──────────────────────────────────────────────────────────────────────

    @Nested
    class Reemplazar {

        @Test
        @DisplayName("RF-109 · DEC-93: reemplaza un RECHAZADO con el mismo tipo y registro, marca el anterior y audita")
        void reemplazaRechazado() {
            conocido(EstadoValidacion.RECHAZADO, OTRO_PERFIL, null);

            DocumentoDto nuevo = servicio.reemplazar(DOCUMENTO, JPEG);

            verify(autorizacion).alcance(CREAR);
            verify(autorizacion).exigir(CREAR, ObjetivoAutorizacion.deClubes(CLUB));
            verify(repositorio).bloquearRegistro(LIGA, REGISTRO);
            ArgumentCaptor<ClaveDocumento> clave = ArgumentCaptor.forClass(ClaveDocumento.class);
            verify(almacenamiento).guardar(clave.capture(), eq(JPEG));
            UUID id = clave.getValue().documentoId();
            assertThat(clave.getValue()).isEqualTo(new ClaveDocumento(LIGA, CarpetaDocumento.REGISTROS, REGISTRO, id));
            assertThat(nuevo.id()).isEqualTo(id);
            verify(repositorio).insertar(id, FOTOGRAFIA.id(), REGISTRO, GUARDADO, PERFIL);
            verify(repositorio).marcarReemplazado(DOCUMENTO, id);
            EventoAuditoria evento = auditado();
            assertThat(evento.accion()).isEqualTo("REEMPLAZAR_DOCUMENTO");
            assertThat(evento.entidadId()).isEqualTo(id.toString());
            assertThat(evento.detalle()).isEqualTo(Map.of("registroId", REGISTRO.toString(), "tipoDocumento",
                    "FOTOGRAFIA", "tipoMime", "image/jpeg", "tamanoBytes", JPEG.length,
                    "documentoAnteriorId", DOCUMENTO.toString()));
        }

        @Test
        @DisplayName("DEC-93: un PENDIENTE también se reemplaza")
        void reemplazaPendiente() {
            conocido(EstadoValidacion.PENDIENTE, PERFIL, null);

            servicio.reemplazar(DOCUMENTO, JPEG);

            verify(repositorio).marcarReemplazado(eq(DOCUMENTO), any());
        }

        @Test
        @DisplayName("RF-109: un documento APROBADO o ya reemplazado responde 409 sin subir el archivo")
        void aprobadoOReemplazado() {
            conocido(EstadoValidacion.APROBADO, OTRO_PERFIL, null);
            esperar(() -> servicio.reemplazar(DOCUMENTO, JPEG), HttpStatus.CONFLICT, "RF-109");

            conocido(EstadoValidacion.RECHAZADO, OTRO_PERFIL, UUID.randomUUID());
            esperar(() -> servicio.reemplazar(DOCUMENTO, JPEG), HttpStatus.CONFLICT, "RF-109");

            verifyNoInteractions(almacenamiento, auditoria);
        }

        @Test
        @DisplayName("RF-109 · DEC-93: si otra petición lo reemplazó o aprobó antes (UPDATE sin fila) responde 409 sin auditar")
        void carrera() {
            conocido(EstadoValidacion.RECHAZADO, OTRO_PERFIL, null);
            given(repositorio.marcarReemplazado(any(), any())).willReturn(false);

            esperar(() -> servicio.reemplazar(DOCUMENTO, JPEG), HttpStatus.CONFLICT, "RF-109");

            verifyNoInteractions(auditoria);
        }

        @Test
        @DisplayName("DEC-91: 403 sin permiso, 400 sin archivo, 404 inexistente y 404 fuera de ámbito, en ese orden")
        void ordenDeErrores() {
            given(autorizacion.alcance(CREAR)).willThrow(Autorizacion.sinPermiso())
                    .willReturn(new AlcanceAutorizacion(false, Set.of(CLUB), false));
            esperar(() -> servicio.reemplazar(DOCUMENTO, null), HttpStatus.FORBIDDEN, "VAL-015");
            esperar(() -> servicio.reemplazar(DOCUMENTO, null), HttpStatus.BAD_REQUEST, "VALIDACION");
            verifyNoInteractions(repositorio);

            given(repositorio.buscarDocumento(LIGA, DOCUMENTO)).willReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.reemplazar(DOCUMENTO, JPEG)).isInstanceOf(RecursoNoEncontradoException.class);
            verify(autorizacion, never()).exigir(any(), any());

            conocido(EstadoValidacion.RECHAZADO, OTRO_PERFIL, null);
            willThrow(Autorizacion.fueraDeAmbito(CREAR))
                    .given(autorizacion).exigir(CREAR, ObjetivoAutorizacion.deClubes(CLUB));
            assertThatThrownBy(() -> servicio.reemplazar(DOCUMENTO, JPEG)).isInstanceOf(RecursoNoEncontradoException.class);
            verifyNoInteractions(almacenamiento, auditoria);
        }

        @Test
        @DisplayName("RF-017: el reemplazo exige el registro PENDIENTE_VALIDACION")
        void registroNoPendiente() {
            conocido(EstadoValidacion.RECHAZADO, OTRO_PERFIL, null);
            given(repositorio.bloquearRegistro(LIGA, REGISTRO))
                    .willReturn(Optional.of(registro(EstadoRegistro.RECHAZADO, EstadoTemporada.ACTIVA)));

            esperar(() -> servicio.reemplazar(DOCUMENTO, JPEG), HttpStatus.CONFLICT, "RF-017");
        }

        @Test
        @DisplayName("RF-108: un 415 del almacenamiento llega sin insertar, marcar ni auditar")
        void archivoRechazado() {
            conocido(EstadoValidacion.RECHAZADO, OTRO_PERFIL, null);
            given(almacenamiento.guardar(any(), any())).willThrow(ArchivoRechazadoException.tipoNoPermitido());

            assertThatThrownBy(() -> servicio.reemplazar(DOCUMENTO, JPEG)).isInstanceOf(ArchivoRechazadoException.class);

            verify(repositorio, never()).insertar(any(), anyLong(), any(), any(), any());
            verify(repositorio, never()).marcarReemplazado(any(), any());
            verifyNoInteractions(auditoria);
        }
    }

    // ── Requisitos (DEC-92, DEC-96) ─────────────────────────────────────────────────────────────

    @Nested
    class Requisitos {

        @Test
        @DisplayName("RF-108 · DEC-91: los requisitos se autorizan con el club y la persona del registro")
        void requisitosDeUnMenor() {
            DocumentoRegistro foto = documento(UUID.randomUUID(), FOTOGRAFIA, EstadoValidacion.RECHAZADO, PERFIL, null);
            given(repositorio.vigentes(REGISTRO)).willReturn(List.of(foto));

            RequisitosRegistroDto requisitos = servicio.requisitos(REGISTRO);

            verify(autorizacion).alcance(CONSULTAR_REGISTRO);
            verify(autorizacion).exigir(CONSULTAR_REGISTRO,
                    ObjetivoAutorizacion.deClubesYPersonas(List.of(CLUB), List.of(PERSONA)));
            verify(repositorio, never()).bloquearRegistro(any(), any());
            assertThat(requisitos.registroId()).isEqualTo(REGISTRO);
            assertThat(requisitos.estadoRegistro()).isEqualTo(EstadoRegistro.PENDIENTE_VALIDACION);
            assertThat(requisitos.categoria()).isEqualTo(new RequisitosRegistroDto.CategoriaDto(CATEGORIA, "Infantil",
                    TipoCategoria.INFANTIL));
            assertThat(requisitos.esMenor()).isTrue();
            assertThat(requisitos.requisitos()).extracting(r -> r.tipoDocumento().codigo())
                    .containsExactly("CONSTANCIA_ESTUDIOS", "IDENTIFICACION", "FOTOGRAFIA", "INE_TUTOR");
            assertThat(requisitos.requisitos().get(2).documentos()).containsExactly(foto.dto());
            assertThat(requisitos.requisitos().get(2).faltan()).isEqualTo(2);
            assertThat(requisitos.completo()).isFalse();
            assertThat(requisitos.listoParaValidar()).isFalse();
            verifyNoInteractions(auditoria, almacenamiento);
        }

        @Test
        @DisplayName("VAL-015 · VAL-024: 403 sin permiso, 404 inexistente y 404 fuera de ámbito")
        void ordenDeErrores() {
            given(autorizacion.alcance(CONSULTAR_REGISTRO)).willThrow(Autorizacion.sinPermiso())
                    .willReturn(new AlcanceAutorizacion(false, Set.of(), true));
            esperar(() -> servicio.requisitos(REGISTRO), HttpStatus.FORBIDDEN, "VAL-015");
            verifyNoInteractions(repositorio);

            UUID otro = UUID.randomUUID();
            given(repositorio.buscarRegistro(LIGA, otro)).willReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.requisitos(otro)).isInstanceOf(RecursoNoEncontradoException.class);

            willThrow(Autorizacion.fueraDeAmbito(CONSULTAR_REGISTRO)).given(autorizacion).exigir(any(), any());
            assertThatThrownBy(() -> servicio.requisitos(REGISTRO)).isInstanceOf(RecursoNoEncontradoException.class);
            verify(repositorio, never()).vigentes(any());
        }
    }

    // ── Enlace (DEC-95) ─────────────────────────────────────────────────────────────────────────

    @Nested
    class Enlace {

        @Test
        @DisplayName("RNF-018 · DEC-95: con un reloj fijo, expiraEn es el reloj más 60 s; se audita sin la URL")
        void enlaceDeSesentaSegundos() {
            AlmacenamientoEnMemoria memoria = new AlmacenamientoEnMemoria(Clock.fixed(AHORA, ZoneOffset.UTC));
            servicio = servicio(memoria);
            conocido(EstadoValidacion.PENDIENTE, PERFIL, UUID.randomUUID());

            EnlaceTemporal enlace = servicio.enlace(DOCUMENTO);

            assertThat(enlace.expiraEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA.plusSeconds(60), ZoneOffset.UTC));
            assertThat(enlace.url()).contains(documentos.get(DOCUMENTO).s3Clave(), "X-Amz-Expires=60");
            verify(autorizacion).alcance(CONSULTAR_DOCUMENTO);
            verify(autorizacion).exigir(CONSULTAR_DOCUMENTO,
                    ObjetivoAutorizacion.deClubesYPersonas(List.of(CLUB), List.of(PERSONA)));
            EventoAuditoria evento = auditado();
            assertThat(evento.accion()).isEqualTo("CONSULTAR_DOCUMENTO");
            assertThat(evento.entidadId()).isEqualTo(DOCUMENTO.toString());
            assertThat(evento.detalle()).isEqualTo(Map.of("registroId", REGISTRO.toString(), "tipoDocumento",
                    "FOTOGRAFIA"));
            assertThat(evento.toString()).doesNotContain(enlace.url(), "X-Amz");
        }

        @Test
        @DisplayName("VAL-015 · VAL-024: 403 sin permiso, 404 inexistente y 404 fuera de ámbito, sin firmar ni auditar")
        void ordenDeErrores() {
            given(autorizacion.alcance(CONSULTAR_DOCUMENTO)).willThrow(Autorizacion.sinPermiso())
                    .willReturn(new AlcanceAutorizacion(false, Set.of(), true));
            esperar(() -> servicio.enlace(DOCUMENTO), HttpStatus.FORBIDDEN, "VAL-015");

            given(repositorio.buscarDocumento(LIGA, DOCUMENTO)).willReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.enlace(DOCUMENTO)).isInstanceOf(RecursoNoEncontradoException.class);

            conocido(EstadoValidacion.PENDIENTE, PERFIL, null);
            willThrow(Autorizacion.fueraDeAmbito(CONSULTAR_DOCUMENTO)).given(autorizacion).exigir(any(), any());
            assertThatThrownBy(() -> servicio.enlace(DOCUMENTO)).isInstanceOf(RecursoNoEncontradoException.class);

            verifyNoInteractions(almacenamiento, auditoria);
        }
    }

    // ── Validación de un documento (DEC-96) ─────────────────────────────────────────────────────

    @Nested
    class Validar {

        @Test
        @DisplayName("RF-109: el Secretario aprueba un PENDIENTE que cargó otro; guarda quién y cuándo, y audita")
        void aprueba() {
            conocido(EstadoValidacion.PENDIENTE, OTRO_PERFIL, null);

            servicio.validar(DOCUMENTO, new ValidarDocumentoRequest(DecisionDocumento.APROBADO, null));

            verify(autorizacion).alcance(VALIDAR);
            verify(autorizacion).exigir(VALIDAR, ObjetivoAutorizacion.deClubes(CLUB));
            verify(repositorio).bloquearRegistro(LIGA, REGISTRO);
            verify(repositorio).resolver(DOCUMENTO, EstadoValidacion.APROBADO, PERFIL,
                    OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC), null);
            EventoAuditoria evento = auditado();
            assertThat(evento.accion()).isEqualTo("VALIDAR_DOCUMENTO");
            assertThat(evento.motivo()).isNull();
            assertThat(evento.detalle()).isEqualTo(Map.of("registroId", REGISTRO.toString(), "tipoDocumento",
                    "FOTOGRAFIA", "decision", "APROBADO"));
        }

        @Test
        @DisplayName("RF-109 · DEC-95: al rechazar guarda las observaciones sin espacios de los extremos y las usa como motivo")
        void rechaza() {
            conocido(EstadoValidacion.PENDIENTE, OTRO_PERFIL, null);

            servicio.validar(DOCUMENTO, new ValidarDocumentoRequest(DecisionDocumento.RECHAZADO,
                    "  La fotografía está borrosa  "));

            verify(repositorio).resolver(DOCUMENTO, EstadoValidacion.RECHAZADO, PERFIL,
                    OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC), "La fotografía está borrosa");
            EventoAuditoria evento = auditado();
            assertThat(evento.motivo()).isEqualTo("La fotografía está borrosa");
            assertThat(evento.detalle()).containsEntry("decision", "RECHAZADO");
        }

        @Test
        @DisplayName("DEC-96: sin decision 400 VALIDACION; rechazo sin observaciones o con menos de 10 caracteres 400 RF-109; más de 500, 400 VALIDACION")
        void cuerpoInvalido() {
            esperar(() -> servicio.validar(DOCUMENTO, null), HttpStatus.BAD_REQUEST, "VALIDACION");
            esperar(() -> servicio.validar(DOCUMENTO, new ValidarDocumentoRequest(null, "Observación válida")),
                    HttpStatus.BAD_REQUEST, "VALIDACION");
            esperar(() -> servicio.validar(DOCUMENTO, new ValidarDocumentoRequest(DecisionDocumento.RECHAZADO, null)),
                    HttpStatus.BAD_REQUEST, "RF-109");
            esperar(() -> servicio.validar(DOCUMENTO, new ValidarDocumentoRequest(DecisionDocumento.RECHAZADO,
                    "   Borrosa   ")), HttpStatus.BAD_REQUEST, "RF-109");
            esperar(() -> servicio.validar(DOCUMENTO, new ValidarDocumentoRequest(DecisionDocumento.APROBADO,
                    "x".repeat(501))), HttpStatus.BAD_REQUEST, "VALIDACION");

            verifyNoInteractions(repositorio, auditoria);
        }

        @Test
        @DisplayName("DEC-96: 10 caracteres exactos bastan para rechazar")
        void diezCaracteres() {
            conocido(EstadoValidacion.PENDIENTE, OTRO_PERFIL, null);

            servicio.validar(DOCUMENTO, new ValidarDocumentoRequest(DecisionDocumento.RECHAZADO, " 1234567890 "));

            verify(repositorio).resolver(eq(DOCUMENTO), eq(EstadoValidacion.RECHAZADO), eq(PERFIL), any(), eq("1234567890"));
        }

        @Test
        @DisplayName("VAL-015 · VAL-024: 403 sin permiso, 404 inexistente y 404 fuera de ámbito")
        void ordenDeErrores() {
            ValidarDocumentoRequest aprobar = new ValidarDocumentoRequest(DecisionDocumento.APROBADO, null);
            given(autorizacion.alcance(VALIDAR)).willThrow(Autorizacion.sinPermiso())
                    .willReturn(new AlcanceAutorizacion(true, Set.of(), false));
            esperar(() -> servicio.validar(DOCUMENTO, null), HttpStatus.FORBIDDEN, "VAL-015");

            given(repositorio.buscarDocumento(LIGA, DOCUMENTO)).willReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.validar(DOCUMENTO, aprobar)).isInstanceOf(RecursoNoEncontradoException.class);

            conocido(EstadoValidacion.PENDIENTE, OTRO_PERFIL, null);
            willThrow(Autorizacion.fueraDeAmbito(VALIDAR))
                    .given(autorizacion).exigir(VALIDAR, ObjetivoAutorizacion.deClubes(CLUB));
            assertThatThrownBy(() -> servicio.validar(DOCUMENTO, aprobar)).isInstanceOf(RecursoNoEncontradoException.class);

            verify(repositorio, never()).resolver(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("VAL-022 [PROPUESTA]: quien cargó el documento no lo valida (403)")
        void separacionDeFunciones() {
            conocido(EstadoValidacion.PENDIENTE, PERFIL, null);

            esperar(() -> servicio.validar(DOCUMENTO, new ValidarDocumentoRequest(DecisionDocumento.APROBADO, null)),
                    HttpStatus.FORBIDDEN, "VAL-022");

            verify(repositorio, never()).resolver(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("RF-109: un documento que no está PENDIENTE o ya fue reemplazado responde 409")
        void noPendiente() {
            ValidarDocumentoRequest aprobar = new ValidarDocumentoRequest(DecisionDocumento.APROBADO, null);
            conocido(EstadoValidacion.APROBADO, OTRO_PERFIL, null);
            esperar(() -> servicio.validar(DOCUMENTO, aprobar), HttpStatus.CONFLICT, "RF-109");

            conocido(EstadoValidacion.PENDIENTE, OTRO_PERFIL, UUID.randomUUID());
            esperar(() -> servicio.validar(DOCUMENTO, aprobar), HttpStatus.CONFLICT, "RF-109");

            verify(repositorio, never()).resolver(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("RF-109 · DEC-93: si otra petición lo resolvió antes (UPDATE sin fila) responde 409 sin auditar")
        void carrera() {
            conocido(EstadoValidacion.PENDIENTE, OTRO_PERFIL, null);
            given(repositorio.resolver(any(), any(), any(), any(), any())).willReturn(false);

            esperar(() -> servicio.validar(DOCUMENTO, new ValidarDocumentoRequest(DecisionDocumento.APROBADO, null)),
                    HttpStatus.CONFLICT, "RF-109");

            verifyNoInteractions(auditoria);
        }

        @Test
        @DisplayName("RF-017 · RF-003: validar exige el registro pendiente en una temporada abierta")
        void registroNoEditable() {
            ValidarDocumentoRequest aprobar = new ValidarDocumentoRequest(DecisionDocumento.APROBADO, null);
            conocido(EstadoValidacion.PENDIENTE, OTRO_PERFIL, null);
            given(repositorio.bloquearRegistro(LIGA, REGISTRO))
                    .willReturn(Optional.of(registro(EstadoRegistro.ACTIVO, EstadoTemporada.ACTIVA)))
                    .willReturn(Optional.of(registro(EstadoRegistro.PENDIENTE_VALIDACION, EstadoTemporada.CERRADA)));

            esperar(() -> servicio.validar(DOCUMENTO, aprobar), HttpStatus.CONFLICT, "RF-017");
            esperar(() -> servicio.validar(DOCUMENTO, aprobar), HttpStatus.CONFLICT, "RF-003");
        }
    }

    @Test
    @DisplayName("DEC-95 · RNF-011: la auditoría de las cuatro acciones no lleva URL, bucket, clave, SHA-256 ni nombre del archivo")
    void auditoriaSinDatosDelArchivo() {
        AlmacenamientoEnMemoria memoria = new AlmacenamientoEnMemoria(Clock.fixed(AHORA, ZoneOffset.UTC));
        servicio = servicio(memoria);
        given(repositorio.esMenor(PERSONA, ALTA)).willReturn(Optional.of(false));
        DocumentoDto cargado = servicio.cargar(REGISTRO, FOTOGRAFIA.id(), JPEG);
        conocido(EstadoValidacion.RECHAZADO, OTRO_PERFIL, null);
        servicio.reemplazar(DOCUMENTO, JPEG);
        servicio.enlace(DOCUMENTO);
        conocido(EstadoValidacion.PENDIENTE, OTRO_PERFIL, null);
        servicio.validar(DOCUMENTO, new ValidarDocumentoRequest(DecisionDocumento.RECHAZADO, "La foto está borrosa"));

        ArgumentCaptor<EventoAuditoria> eventos = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria, times(4)).registrar(eventos.capture());
        assertThat(eventos.getAllValues()).extracting(EventoAuditoria::accion)
                .containsExactly("CARGAR_DOCUMENTO", "REEMPLAZAR_DOCUMENTO", "CONSULTAR_DOCUMENTO", "VALIDAR_DOCUMENTO");
        List<String> claves = new ArrayList<>(memoria.objetos().keySet());
        assertThat(claves).hasSize(2);
        for (EventoAuditoria evento : eventos.getAllValues()) {
            String texto = evento.detalle() + " " + evento.motivo();
            assertThat(texto).doesNotContain(SECRETOS.toArray(String[]::new));
            assertThat(texto).doesNotContain(claves.toArray(String[]::new));
        }
        assertThat(cargado.id()).isNotNull();
    }

    // ── Apoyo ──────────────────────────────────────────────────────────────────────────────────

    private DocumentoRegistroService servicio(AlmacenamientoDocumentos conAlmacenamiento) {
        return new DocumentoRegistroService(repositorio, conAlmacenamiento, autorizacion, auditoria,
                Clock.fixed(AHORA, ZoneOffset.UTC), new SiglimPropiedades(null, null, null, LIGA));
    }

    private static RegistroDeDocumentos registro(EstadoRegistro estado, EstadoTemporada temporada) {
        return new RegistroDeDocumentos(REGISTRO, PERSONA, CLUB, ALTA, estado, temporada, CATEGORIA, "Infantil",
                TipoCategoria.INFANTIL);
    }

    /** Deja {@link #DOCUMENTO} (una fotografía del registro) con ese estado, autor y reemplazo. */
    private void conocido(EstadoValidacion estado, UUID cargadoPor, UUID reemplazadoPor) {
        documentos.put(DOCUMENTO, documento(DOCUMENTO, FOTOGRAFIA, estado, cargadoPor, reemplazadoPor));
        given(repositorio.buscarDocumento(LIGA, DOCUMENTO)).willReturn(Optional.of(documentos.get(DOCUMENTO)));
    }

    private static DocumentoRegistro documento(UUID id, TipoDocumentoDto tipo, EstadoValidacion estado, UUID cargadoPor,
            UUID reemplazadoPor) {
        DocumentoDto dto = new DocumentoDto(id, REGISTRO, tipo, "image/jpeg", JPEG.length, estado, null, null, null,
                null, OffsetDateTime.parse("2026-11-12T12:00:00-06:00"));
        return new DocumentoRegistro(dto, cargadoPor, reemplazadoPor, "siglim-documentos-prueba",
                "liga/" + LIGA + "/registros/" + REGISTRO + "/" + id + ".jpg");
    }

    private EventoAuditoria auditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }

    private static void esperar(ThrowingCallable llamada, HttpStatus estado, String codigo) {
        assertThatThrownBy(llamada).isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
            assertThat(error.estado()).isEqualTo(estado);
            assertThat(error.codigo()).isEqualTo(codigo);
        });
    }
}
