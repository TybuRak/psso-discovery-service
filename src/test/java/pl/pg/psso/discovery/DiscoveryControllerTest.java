package pl.pg.psso.discovery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "discovery.storage-file=")
@AutoConfigureMockMvc
class DiscoveryControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ServiceRegistry registry;

    @BeforeEach
    void clean() {
        registry.findAll().forEach(e -> registry.unregister(e.name()));
    }

    private void register(String json, String remoteIp, int expectedStatus) throws Exception {
        mvc.perform(post("/services").contentType(MediaType.APPLICATION_JSON).content(json)
                        .with(r -> { r.setRemoteAddr(remoteIp); return r; }))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void registerFindListAndUnregister() throws Exception {
        mvc.perform(post("/services").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Transport\"}")
                        .with(r -> { r.setRemoteAddr("172.20.147.17"); return r; }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("transport"))
                .andExpect(jsonPath("$.ip").value("172.20.147.17"));

        register("{\"name\":\"transport\"}", "172.20.147.17", 200);

        mvc.perform(get("/services/transport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ip").value("172.20.147.17"));

        mvc.perform(delete("/services/transport")).andExpect(status().isNoContent());
        mvc.perform(get("/services/transport"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("service not found: transport"));
        mvc.perform(delete("/services/transport")).andExpect(status().isNotFound());
    }

    @Test
    void severalInstancesAreReturnedAtRandom() throws Exception {
        register("{\"name\":\"transport\"}", "172.20.147.17", 201);
        register("{\"name\":\"transport\",\"ip\":\"172.20.147.18\"}", "10.0.0.1", 201);

        mvc.perform(get("/services/transport/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ip").value("172.20.147.17"))
                .andExpect(jsonPath("$[1].ip").value("172.20.147.18"));
        mvc.perform(get("/services")).andExpect(jsonPath("$.length()").value(2));

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String body = mvc.perform(get("/services/transport")).andReturn().getResponse().getContentAsString();
            seen.add(body.contains("172.20.147.17") ? "17" : "18");
        }
        assertThat(seen).containsExactlyInAnyOrder("17", "18");

        mvc.perform(delete("/services/transport").param("ip", "172.20.147.17")).andExpect(status().isNoContent());
        mvc.perform(delete("/services/transport").param("ip", "172.20.147.17"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("instance not found: transport 172.20.147.17"));
        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/services/transport")).andExpect(jsonPath("$.ip").value("172.20.147.18"));
        }
        mvc.perform(delete("/services/transport").param("ip", "172.20.147.18")).andExpect(status().isNoContent());
        mvc.perform(get("/services/transport")).andExpect(status().isNotFound());
        mvc.perform(get("/services/transport/instances")).andExpect(status().isNotFound());
    }

    @Test
    void registerWithoutNameIsRejected() throws Exception {
        mvc.perform(post("/services").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("name is required"));
    }
}
