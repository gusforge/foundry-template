package io.github.gusforge.foundry.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.gusforge.foundry.exception.BizException;
import io.github.gusforge.foundry.exception.ErrorCode;
import io.github.gusforge.foundry.model.DemoVO;
import io.github.gusforge.foundry.service.DemoService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DemoController.class)
class DemoControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean DemoService service;

    @Test
    void should_wrap_success_in_data() throws Exception {
        when(service.getById(1L)).thenReturn(new DemoVO(1L, "a", 0, LocalDateTime.now()));

        mvc.perform(get("/api/v1/demos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("a"));
    }

    @Test
    void should_map_biz_exception_to_http_status_and_error_body() throws Exception {
        when(service.getById(1L)).thenThrow(new BizException(ErrorCode.DEMO_NOT_FOUND));

        mvc.perform(get("/api/v1/demos/1").header("X-Trace-Id", "abc123"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Trace-Id", "abc123"))
                .andExpect(jsonPath("$.code").value(300001))
                .andExpect(jsonPath("$.traceId").value("abc123"));
    }

    @Test
    void should_return_400_with_field_detail_on_invalid_body() throws Exception {
        mvc.perform(
                        post("/api/v1/demos")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(100001))
                .andExpect(jsonPath("$.message").value(containsString("name")));
    }

    @Test
    void should_hide_internal_details_on_unexpected_error() throws Exception {
        when(service.getById(2L))
                .thenThrow(new IllegalStateException("jdbc:mysql://10.0.0.5 secret"));

        mvc.perform(get("/api/v1/demos/2"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value(not(containsString("10.0.0.5"))));
    }

    /** 回归：兜底 handler 曾把框架的 404 吞成 500。 */
    @Test
    void should_keep_404_for_unknown_route_not_turn_into_500() throws Exception {
        mvc.perform(get("/no/such/route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(100002));
    }

    @Test
    void should_keep_405_for_wrong_method() throws Exception {
        mvc.perform(delete("/api/v1/demos/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(100001));
    }
}
