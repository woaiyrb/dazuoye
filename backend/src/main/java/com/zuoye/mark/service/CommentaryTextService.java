package com.zuoye.mark.service;

import org.springframework.stereotype.Service;

@Service
public class CommentaryTextService {

    public String toReadable(String content) {
        if (content == null || content.isBlank()) return content;
        String text = content
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("$$", "")
                .replace("$", "")
                .replace("\\(", "")
                .replace("\\)", "")
                .replace("\\[", "")
                .replace("\\]", "")
                .replace("\\left", "")
                .replace("\\right", "")
                .replace("\\cdot", "×")
                .replace("\\times", "×")
                .replace("\\div", "÷")
                .replace("\\approx", "≈")
                .replace("\\neq", "≠")
                .replace("\\leq", "≤")
                .replace("\\le", "≤")
                .replace("\\geq", "≥")
                .replace("\\ge", "≥")
                .replace("\\pm", "±")
                .replace("\\Delta", "Δ")
                .replace("\\qquad", "    ")
                .replace("\\quad", "  ")
                .replace("\\,", " ")
                .replace("\\;", " ");
        text = text.replaceAll(
                "\\\\[dt]?frac\\{([^{}]+)\\}\\{([^{}]+)\\}",
                "($1)/($2)");
        text = text.replaceAll(
                "\\\\sqrt\\{([^{}]+)\\}",
                "√($1)");
        text = text.replaceAll("\\\\[a-zA-Z]+", "");
        text = text.replace("\\", "");
        text = text.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");
        text = text.replaceAll("(?m)^\\s*[-*]\\s+", "• ");
        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        text = text.replace("**", "");
        text = text.replace("__", "");
        text = text.replace("`", "");
        text = text.replaceAll("\\^\\{?2\\}?", "²");
        text = text.replaceAll("\\^\\{?3\\}?", "³");
        text = text.replaceAll("[ \\t]+\\n", "\n");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }
}
