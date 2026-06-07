package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.List;
import java.util.Objects;

/**
 * Dify prompt message for LLM node.
 */
public final class DifyPromptMessage {
    @JSONField(name = "role")
    private final String role;
    @JSONField(name = "text")
    private final String text;
    @JSONField(name = "image_urls")
    private final List<String> imageUrls;

    public DifyPromptMessage(String role, String text, List<String> imageUrls) {
        this.role = role;
        this.text = text;
        this.imageUrls = imageUrls;
    }

    public String role() { return role; }
    public String text() { return text; }
    public List<String> imageUrls() { return imageUrls; }

    public String getRole() { return role; }
    public String getText() { return text; }
    public List<String> getImageUrls() { return imageUrls; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyPromptMessage)) return false;
        DifyPromptMessage that = (DifyPromptMessage) o;
        return Objects.equals(role, that.role)
                && Objects.equals(text, that.text)
                && Objects.equals(imageUrls, that.imageUrls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, text, imageUrls);
    }

    @Override
    public String toString() {
        return String.format("DifyPromptMessage[role=%s, text=%s, imageUrls=%s]", role, text, imageUrls);
    }
}
