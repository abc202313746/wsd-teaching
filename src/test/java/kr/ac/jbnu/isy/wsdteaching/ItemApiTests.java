package kr.ac.jbnu.isy.wsdteaching;

import com.jayway.jsonpath.JsonPath;
import kr.ac.jbnu.isy.wsdteaching.api.error.GlobalExceptionHandler;
import kr.ac.jbnu.isy.wsdteaching.api.v1.ItemController;
import kr.ac.jbnu.isy.wsdteaching.config.DemoErrorInterceptor;
import kr.ac.jbnu.isy.wsdteaching.service.ItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class ItemApiTests {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void allEightEndpointsShareTheStoreAndUseTheResponseEnvelope() throws Exception {
        long first = create("lifecycle-first", 1000);
        MvcResult headerCreated = mvc.perform(post("/api/v3/items/with-header")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-USER-ID", "student01")
                        .header("Authorization", "Bearer demo-token")
                        .content("{\"name\":\"lifecycle-header\",\"price\":2000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.userId").value("student01"))
                .andExpect(jsonPath("$.data.authorizationProvided").value(true))
                .andReturn();
        long second = idFrom(headerCreated, "$.data.item.id");
        long third = create("lifecycle-third", 3000);

        mvc.perform(get("/api/v1/items").param("keyword", "lifecycle-").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(get("/api/v1/items/{id}", second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("lifecycle-header"));
        mvc.perform(put("/api/v1/items/{id}", first).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"lifecycle-updated\",\"price\":1500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("lifecycle-updated"));
        mvc.perform(put("/api/v1/items/{id}/price", first).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":2800}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("lifecycle-updated"))
                .andExpect(jsonPath("$.data.price").value(2800));
        mvc.perform(get("/api/v1/items/{id}", first))
                .andExpect(jsonPath("$.data.price").value(2800));
        mvc.perform(delete("/api/v1/items/{id}", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.deletedCount").value(1));
        mvc.perform(get("/api/v1/items/{id}", first))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("ITEM_NOT_FOUND"));
        mvc.perform(delete("/api/v1/items").param("ids", second + "," + third))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(2));
        mvc.perform(get("/api/v1/items").param("keyword", "lifecycle-"))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void invalidInputAndFrameworkErrorsUseTheSameErrorEnvelope() throws Exception {
        for (String invalidBody : new String[]{
                "{\"name\":\" \",\"price\":100}",
                "{\"name\":\"bad\",\"price\":-1}",
                "{\"name\":\"bad\"}", "{broken"
        }) {
            mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON).content(invalidBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value("error"))
                    .andExpect(jsonPath("$.data.message").isString());
        }
        mvc.perform(get("/api/v1/items/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/items").param("size", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"));
        mvc.perform(post("/api/v1/items/123"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value("error"));
        mvc.perform(delete("/api/v1/items"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    void failedBatchDeletionDoesNotDeleteExistingItems() throws Exception {
        long existing = create("atomic-delete", 100);
        mvc.perform(delete("/api/v1/items").param("ids", existing + ",9223372036854775807"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/items/{id}", existing))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/items").param("ids", existing + "," + existing))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/items/{id}", existing).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":500}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items/{id}", existing))
                .andExpect(jsonPath("$.data.price").value(100));
        mvc.perform(delete("/api/v1/items/{id}", existing)).andExpect(status().isOk());
    }

    @Test
    void demoErrorsAreLoggedAndDoNotCreateItems(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/v1/items")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/items").header("X-Demo-Error", "500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"blocked-demo\",\"price\":100}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.code").value("DEMO_INTERNAL_ERROR"));
        mvc.perform(get("/api/v1/items").header("X-Demo-Error", "503"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.data.code").value("DEMO_SERVICE_UNAVAILABLE"));
        mvc.perform(get("/api/v1/items").param("keyword", "blocked-demo"))
                .andExpect(jsonPath("$.data.total").value(0));
        assertAll(
                () -> assertTrue(output.getOut().contains("LoggingInterceptor")),
                () -> assertTrue(output.getOut().contains("status=200")),
                () -> assertTrue(output.getOut().contains("status=500")),
                () -> assertTrue(output.getOut().contains("status=503"))
        );
    }

    @Test
    void unexpectedServerErrorsAreConvertedToTheErrorEnvelope() throws Exception {
        ItemService brokenService = mock(ItemService.class);
        when(brokenService.get(1L)).thenThrow(new IllegalStateException("private internal detail"));
        MockMvc isolated = MockMvcBuilders.standaloneSetup(new ItemController(brokenService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        isolated.perform(get("/api/v1/items/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.data.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void demoErrorsCanBeDisabled() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Demo-Error", "500");
        assertTrue(new DemoErrorInterceptor(false).preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    private long create(String name, int price) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"price\":" + price + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(header().exists("Location"))
                .andReturn();
        return idFrom(result, "$.data.id");
    }

    private long idFrom(MvcResult result, String path) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), path);
        return id.longValue();
    }
}
