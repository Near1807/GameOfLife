package gamelife.system;

import gamelife.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without a token (npm start, the browser version) the shutdown endpoint does not exist. */
@AutoConfigureMockMvc
class SystemControllerTest extends IntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void shutdownIsOffWithoutAToken() throws Exception {
        mvc.perform(post("/api/system/shutdown")).andExpect(status().isNotFound());
        mvc.perform(post("/api/system/shutdown").header("X-GameLife-Token", "")).andExpect(status().isNotFound());
    }
}
