package com.delivery.web_bff.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WebBffOriginFilterTest {
    private final WebBffOriginFilter filter = new WebBffOriginFilter(java.util.Set.of("https://localhost:5173"));

    @Test
    void rejectsMissingOrUntrustedOriginForMutation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bff/session/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, org.mockito.Mockito.never()).doFilter(request, response);
    }

    @Test
    void acceptsConfiguredOriginAndDoesNotRequireOriginOnRead() throws Exception {
        MockHttpServletRequest write = new MockHttpServletRequest("POST", "/bff/session/logout");
        write.addHeader("Origin", "https://localhost:5173");
        MockHttpServletResponse writeResponse = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(write, writeResponse, chain);
        verify(chain).doFilter(write, writeResponse);

        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/bff/session");
        MockHttpServletResponse readResponse = new MockHttpServletResponse();
        filter.doFilter(read, readResponse, chain);
        verify(chain).doFilter(read, readResponse);
    }
}
