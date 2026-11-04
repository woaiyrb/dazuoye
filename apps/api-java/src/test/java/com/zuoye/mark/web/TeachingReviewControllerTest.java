package com.zuoye.mark.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zuoye.mark.service.DeepTutorClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TeachingReviewControllerTest {

    @Test
    void requiresTeacherConfirmationBeforePublishing() throws Exception {
        var controller = new TeachingReviewController(
                mock(DeepTutorClient.class),
                new ObjectMapper());

        assertThatThrownBy(() -> controller.save("1", draftWithStatus("PUBLISHED")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("教师确认");

        var reviewed = controller.save("1", draftWithStatus("REVIEWED"));
        assertThat(reviewed.status()).isEqualTo("REVIEWED");
        assertThat(reviewed.version()).isEqualTo(2);

        var published = controller.save("1", draftWithStatus("PUBLISHED"));
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.version()).isEqualTo(3);
    }

    @Test
    void generatesAnAiDraftWithoutPublishingIt() throws Exception {
        DeepTutorClient client = mock(DeepTutorClient.class);
        when(client.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {
                          "title":"有理数作业讲评",
                          "overview":"本次整体正确率需要进一步提升。",
                          "commonErrors":"符号判断错误较多。",
                          "possibleReasons":"可能对负数运算规则掌握不牢。",
                          "correctApproach":"先判断符号，再计算绝对值。",
                          "knowledgeSummary":"有理数加法与绝对值。",
                          "practiceAdvice":"完成同类基础题。",
                          "evidence":"依据匿名统计与标准答案。"
                        }
                        """);
        var controller = new TeachingReviewController(client, new ObjectMapper());

        var generated = controller.generate(
                "1",
                new TeachingReviewController.ReviewEvidence(
                        "初一数学·有理数基础练习",
                        3,
                        62,
                        "符号判断错误",
                        "教师解析"));

        assertThat(generated.status()).isEqualTo("DRAFT");
        assertThat(generated.title()).isEqualTo("有理数作业讲评");
        assertThat(generated.version()).isEqualTo(2);
    }

    private TeachingReviewController.ReviewDraft draftWithStatus(String status) {
        return new TeachingReviewController.ReviewDraft(
                "作业讲评",
                "整体情况",
                "常见错误",
                "可能原因",
                "正确思路",
                "知识点",
                "练习建议",
                "生成依据",
                status,
                0,
                null);
    }
}
