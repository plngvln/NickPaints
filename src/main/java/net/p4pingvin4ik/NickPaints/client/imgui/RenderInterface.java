package net.p4pingvin4ik.NickPaints.client.imgui;

import imgui.ImGuiIO;

/**
 * A functional interface for rendering, matching the provided example.
 */
@FunctionalInterface
public interface RenderInterface {
    void gradientNickname$render(final ImGuiIO io);
}