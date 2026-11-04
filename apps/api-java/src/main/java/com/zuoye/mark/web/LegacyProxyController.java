package com.zuoye.mark.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyProxyController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "content-length",
            "expect",
            "host",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade");

    private final HttpClient httpClient;
    private final String legacyBaseUrl;

    public LegacyProxyController(
            @Value("${mark.legacy-api-base-url:http://127.0.0.1:8000}") String legacyBaseUrl) {
        this.legacyBaseUrl = legacyBaseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) throws Exception {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        URI target = URI.create(
                legacyBaseUrl + path + (query == null || query.isBlank() ? "" : "?" + query));

        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMinutes(15))
                .method(
                        request.getMethod(),
                        body == null || body.length == 0
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofByteArray(body));

        copyRequestHeaders(request, builder);
        HttpResponse<byte[]> upstream = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofByteArray());

        HttpHeaders headers = new HttpHeaders();
        copyResponseHeaders(upstream, headers);
        return ResponseEntity.status(upstream.statusCode())
                .headers(headers)
                .body(upstream.body());
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpRequest.Builder builder) {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            names.add(headerNames.nextElement());
        }
        for (String name : names) {
            if (!isForwardable(name)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values != null && values.hasMoreElements()) {
                builder.header(name, values.nextElement());
            }
        }
        if (request.getRemoteAddr() != null) {
            builder.header("X-Forwarded-For", request.getRemoteAddr());
        }
        builder.header("X-Forwarded-Proto", request.getScheme());
    }

    private void copyResponseHeaders(HttpResponse<byte[]> upstream, HttpHeaders headers) {
        upstream.headers().map().forEach((name, values) -> {
            if (isForwardable(name)) {
                headers.put(name, values);
            }
        });
        List<String> cookies = upstream.headers().allValues("set-cookie");
        if (!cookies.isEmpty()) {
            headers.put(HttpHeaders.SET_COOKIE, cookies);
        }
    }

    private boolean isForwardable(String name) {
        return !HOP_BY_HOP_HEADERS.contains(name.toLowerCase(Locale.ROOT));
    }
}
