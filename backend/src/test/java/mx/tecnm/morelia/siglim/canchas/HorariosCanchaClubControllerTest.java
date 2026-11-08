package mx.tecnm.morelia.siglim.canchas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(HorariosCanchaClubController.class)
@Import({ HorariosCanchaClubController.class, HorariosCanchaClubService.class })
class HorariosCanchaClubControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private HorariosCanchaClubService service;

    @Nested
    @DisplayName("GET /canchas/{id}/horarios-club")
    class Get {

        @Test
        @WithMockUser(roles = "ADMINISTRADOR_GENERAL")
        @DisplayName("RF-119: devuelve lista vacía cuando no hay horarios")
        void listaVacia() throws Exception {
            UUID canchaId = UUID.randomUUID();
            doReturn(List.of()).when(service).listar(eq(canchaId), any());
            mvc.perform(
                    mvc.perform(javaRequest()).andExpect(javaResult())
                    mvc.perform(javaRequest())
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$").isArray())
                    .andExpect(MockMvcResultMatchers.jsonPath("$").isEmpty());
        }

        @Test
        @WithMockUser(roles = "SECRETARIO")
        @DisplayName("RF-119: rechaza con 403 cuando falta el permiso")
        void rechazaSinPermiso() throws Exception {
            UUID canchaId = UUID.randomUUID();
            mvc.perform(
                    mvc.getRequestBuilder("/api/v1/canchas/{id}/horarios-club", canchaId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(javaTest().status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /canchas/{id}/horarios-club")
    class Put {

        @Test
        @WithMockUser(roles = "ADMINISTRADOR_GENERAL")
        @DisplayName("RF-119: reemplaza horarios y responde 200 con la lista resultante")
        void reemplaza() throws Exception {
            UUID canchaId = UUID.randomUUID();
            when(service.reemplazar(eq(canchaId), any(), any())).thenReturn(List.of());
            mvc.perform(
                    mvc.putRequestBuilder("/api/v1/canchas/{id}/horarios-club", canchaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"horarios\":[]}"))
                    .andExpect(javaTest().status().isOk());
        }

        @Test
        @WithMockUser(roles = "ADMINISTRADOR_GENERAL")
        @DisplayName("RF-119: rechaza con 403 cuando falta el permiso en PUT")
        void rechazaSinPermiso() throws Exception {
            UUID canchaId = UUID.randomUUID();
            mvc.perform(
                    mvc.putRequestBuilder("/api/v1/canchas/{id}/horarios-club", canchaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"horarios\":[]}"))
                    .andExpect(javaTest().status().isForbidden());
        }
    }

    private static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.MockMvcRequestBuilders javaTest() {
        throw new UnsupportedOperationException("placeholder");
    }
}
