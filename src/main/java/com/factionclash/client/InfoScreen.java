package com.factionclash.client;

import com.factionclash.net.InfoPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** A scrollable panel of coloured text: the reporting station's base overview and NPC inspection. */
public class InfoScreen extends Screen {
    private final InfoPayload data;
    private final List<FormattedCharSequence> rows = new ArrayList<>();
    private final List<Integer> rowColors = new ArrayList<>();
    private int left, top, panelW, panelH;
    private double scroll;

    public InfoScreen(InfoPayload data) {
        super(Component.literal(data.title()));
        this.data = data;
    }

    @Override
    protected void init() {
        panelW = Math.min(440, width - 24);
        panelH = height - 64;
        left = (width - panelW) / 2;
        top = 30;
        rows.clear();
        rowColors.clear();
        for (int i = 0; i < data.lines().size(); i++) {
            int color = i < data.colors().size() ? data.colors().get(i) : 0xFFFFFF;
            for (FormattedCharSequence s : font.split(FormattedText.of(data.lines().get(i)), panelW - 16)) {
                rows.add(s);
                rowColors.add(color);
            }
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private int contentHeight() {
        return rows.size() * (font.lineHeight + 2);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - scrollY * 18, 0, Math.max(0, contentHeight() - (panelH - 12)));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFF000000 | data.titleColor());
        g.fill(left, top, left + panelW, top + panelH, 0xC0101018);
        g.renderOutline(left, top, panelW, panelH, 0xFF606070);
        g.enableScissor(left + 1, top + 1, left + panelW - 1, top + panelH - 1);
        int y = top + 6 - (int) scroll;
        for (int i = 0; i < rows.size(); i++) {
            if (y > top - font.lineHeight && y < top + panelH) {
                g.drawString(font, rows.get(i), left + 8, y, 0xFF000000 | rowColors.get(i));
            }
            y += font.lineHeight + 2;
        }
        g.disableScissor();
        if (contentHeight() > panelH - 12) {
            int barH = Math.max(12, (panelH - 4) * (panelH - 12) / contentHeight());
            int barY = top + 2 + (int) ((panelH - 4 - barH) * scroll / Math.max(1, contentHeight() - (panelH - 12)));
            g.fill(left + panelW - 5, barY, left + panelW - 2, barY + barH, 0xFF9090A0);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
