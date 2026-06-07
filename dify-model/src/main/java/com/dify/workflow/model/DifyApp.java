package com.dify.workflow.model;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * Dify application definition.
 */
public final class DifyApp {
    @JSONField(name = "name")
    private final String name;
    @JSONField(name = "mode")
    private final String mode;
    @JSONField(name = "icon")
    private final String icon;
    @JSONField(name = "icon_background")
    private final String iconBackground;
    @JSONField(name = "description")
    private final String description;
    @JSONField(name = "use_icon_as_answer_icon")
    private final Boolean useIconAsAnswerIcon;

    public DifyApp(String name, String mode, String icon, String iconBackground,
                   String description, Boolean useIconAsAnswerIcon) {
        this.name = name;
        this.mode = mode;
        this.icon = icon;
        this.iconBackground = iconBackground;
        this.description = description;
        this.useIconAsAnswerIcon = useIconAsAnswerIcon != null ? useIconAsAnswerIcon : false;
    }

    public String name() { return name; }
    public String mode() { return mode; }
    public String icon() { return icon; }
    public String iconBackground() { return iconBackground; }
    public String description() { return description; }
    public Boolean useIconAsAnswerIcon() { return useIconAsAnswerIcon; }

    public String getName() { return name; }
    public String getMode() { return mode; }
    public String getIcon() { return icon; }
    public String getIconBackground() { return iconBackground; }
    public String getDescription() { return description; }
    public Boolean getUseIconAsAnswerIcon() { return useIconAsAnswerIcon; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DifyApp)) return false;
        DifyApp that = (DifyApp) o;
        return Objects.equals(name, that.name)
                && Objects.equals(mode, that.mode)
                && Objects.equals(icon, that.icon)
                && Objects.equals(iconBackground, that.iconBackground)
                && Objects.equals(description, that.description)
                && Objects.equals(useIconAsAnswerIcon, that.useIconAsAnswerIcon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, mode, icon, iconBackground, description, useIconAsAnswerIcon);
    }

    @Override
    public String toString() {
        return String.format("DifyApp[name=%s, mode=%s, icon=%s, iconBackground=%s, description=%s, useIconAsAnswerIcon=%s]",
                name, mode, icon, iconBackground, description, useIconAsAnswerIcon);
    }
}
