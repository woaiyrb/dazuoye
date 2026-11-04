package com.zuoye.mark.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void reportsJavaRuntimeStatus() {
        var result = new HealthController().health();

        assertThat(result.get("status")).isEqualTo("ok");
        assertThat(result.get("service")).isEqualTo("mark-api-java");
        assertThat(result.get("runtime").toString()).startsWith("Java ");
    }
}
