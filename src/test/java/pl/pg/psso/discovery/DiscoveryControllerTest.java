package pl.pg.psso.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DiscoveryControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void registerFindListAndUnregister() throws Exception {
        mvc.perform(post("/services").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Transport\"}")
                        .with(r -> { r.setRemoteAddr("172.20.147.17"); return r; }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("transport"))
                .andExpect(jsonPath("$.ip").value("172.20.147.17"));

        mvc.perform(post("/services").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"transport\",\"ip\":\"172.20.147.99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ip").value("172.20.147.99"));

        mvc.perform(get("/services/transport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ip").value("172.20.147.99"));

        mvc.perform(get("/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("transport"));

        mvc.perform(delete("/services/transport")).andExpect(status().isNoContent());
        mvc.perform(get("/services/transport"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("service not found: transport"));
        mvc.perform(delete("/services/transport")).andExpect(status().isNotFound());
    }

    @Test
    void registerWithoutNameIsRejected() throws Exception {
        mvc.perform(post("/services").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name is required"));
    }
}
