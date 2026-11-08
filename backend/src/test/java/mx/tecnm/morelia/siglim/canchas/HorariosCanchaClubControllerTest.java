package mx.tecnm.morelia.siglim.canchas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;


@WebMvcTest(HorariosCanchaClubController.class)
class HorariosCanchaClubControllerTest {

    @Autowired
    private MockMvc mvc;

    @Nested
    @DisplayName("GET /canchas/{id}/horarios-club")
    class Get {

        @Test
        @WithMockUser(roles = "ADMINISTRADOR_GENERAL")
        @DisplayName("RF-119: devuelve lista vacía cuando no hay horarios")
        void listaVacia() throws Exception {
            UUID canchaId = UUID.randomUUID();
            mvc.perform(
                        get("/api/v1/canchas/{id}/horarios-club", canchaId)
                            .accept(MediaType.APPLICATION_JSON))
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
                        get("/api/v1/canchas/{id}/horarios-club", canchaId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(MockMvcResultMatchers.status().isForbidden());
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
            mvc.perform(
                        put("/api/v1/canchas/{id}/horarios-club", canchaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"horarios\":[]}"))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.jsonPath("$").isArray())
                    .andExpect(MockMvcResultMatchers.jsonPath("$").isEmpty());
        }

        @Test
        @WithMockUser(roles = "ADMINISTRADOR_GENERAL")
        @DisplayName("RF-119: rechaza con 403 cuando falta el permiso en PUT")
        void rechazaSinPermiso() throws Exception {
            UUID canchaId = UUID.randomUUID();
            mvc.perform(
                        put("/api/v1/canchas/{id}/horarios-club", canchaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"horarios\":[]}"))
                    .andExpect(MockMvcResultMatchers.status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "ADMINISTRADOR_GENERAL")
        @DisplayName("RF-119: rechaza con 422 cuando el cuerpo no indica categorías")
        void rechazaCuerpoInvalido() throws Exception {
            UUID canchaId = UUID.randomUUID();
            mvc.perform(
                        put("/api/v1/canchas/{id}/horarios-club", canchaId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(MockMvcResultMatchers.status().isBadRequest());
        }
    }
}
