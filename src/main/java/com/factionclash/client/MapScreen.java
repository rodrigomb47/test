package com.factionclash.client;

import com.factionclash.net.MapPayload;
import net.minecraft.client.Minecraft;
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

/**
 * The world map board: a top-down chart of the campaign map with the explored sectors shaded and every delivered
 * report drawn as a marker (hover for details), the player's own position, a legend and the written report list.
 */
public class MapScreen extends Screen {
    private static final int[] COLORS = {
            0x4FC3F7, // own base (overridden by the faction colour)
            0xE53935, // enemy settlement
            0x8E24AA, // monster lair
            0xFB8C00, // hostiles
            0x9E9E9E, // cave entrance
            0xFFD54F, // exposed ore
            0x26C6DA, // surveyed cave
            0x66BB6A  // village
    };
    private static final String[] NAMES = {"Our base", "Enemy settlement", "Monster lair", "Hostiles", "Cave entrance", "Exposed ore", "Surveyed cave",
            "Village"};

    private final MapPayload data;
    private final List<FormattedCharSequence> rows = new ArrayList<>();
    private int mapX, mapY, mapSize, listX, listW, listY, listH;
    private double scroll;

    public MapScreen(MapPayload data) {
        super(Component.literal(data.title()));
        this.data = data;
    }

    @Override
    protected void init() {
        mapSize = Math.max(80, Math.min(height - 70, (width - 30) * 3 / 5));
        mapX = 12;
        mapY = 28;
        listX = mapX + mapSize + 12;
        listW = Math.max(60, width - listX - 12);
        listY = mapY + NAMES.length * 11 + 10;
        listH = mapY + mapSize - listY;
        fitView();
        rows.clear();
        for (String l : data.lines()) {
            rows.addAll(font.split(FormattedText.of(l), listW - 8));
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    private int viewCx, viewCz, viewR;

    /** Zooms to what the faction knows (bases, reports, explored sectors), within the campaign map. */
    private void fitView() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (MapPayload.Marker m : data.markers()) {
            minX = Math.min(minX, m.x());
            maxX = Math.max(maxX, m.x());
            minZ = Math.min(minZ, m.z());
            maxZ = Math.max(maxZ, m.z());
        }
        for (long key : data.explored()) {
            int x0 = (int) (key >> 32) * 32, z0 = (int) key * 32;
            minX = Math.min(minX, x0);
            maxX = Math.max(maxX, x0 + 32);
            minZ = Math.min(minZ, z0);
            maxZ = Math.max(maxZ, z0 + 32);
        }
        int full = Math.max(16, data.frame().get(2));
        if (minX == Integer.MAX_VALUE) {
            viewCx = data.frame().get(0);
            viewCz = data.frame().get(1);
            viewR = full;
            return;
        }
        viewCx = (minX + maxX) / 2;
        viewCz = (minZ + maxZ) / 2;
        viewR = Mth.clamp(Math.max(maxX - minX, maxZ - minZ) / 2 + 24, 64, full);
    }

    private int cx() {
        return viewCx;
    }

    private int cz() {
        return viewCz;
    }

    private int radius() {
        return viewR;
    }

    private int sx(double x) {
        return mapX + (int) ((x - (cx() - radius())) * mapSize / (2.0 * radius()));
    }

    private int sy(double z) {
        return mapY + (int) ((z - (cz() - radius())) * mapSize / (2.0 * radius()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int content = rows.size() * (font.lineHeight + 1);
        scroll = Mth.clamp(scroll - scrollY * 18, 0, Math.max(0, content - listH + 6));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFF000000 | data.titleColor());
        // the chart
        g.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xE01A2418);
        for (long key : data.explored()) {
            int x0 = (int) (key >> 32) * 32, z0 = (int) key * 32;
            g.fill(Math.max(mapX, sx(x0)), Math.max(mapY, sy(z0)), Math.min(mapX + mapSize, sx(x0 + 32)),
                    Math.min(mapY + mapSize, sy(z0 + 32)), 0x5044A048);
        }
        g.renderOutline(mapX - 1, mapY - 1, mapSize + 2, mapSize + 2, 0xFF808070);
        MapPayload.Marker hovered = null;
        for (MapPayload.Marker m : data.markers()) {
            int kind = m.kind() & 15;
            boolean stale = (m.kind() & MapPayload.STALE) != 0;
            int color = kind == MapPayload.OWN_BASE ? data.titleColor() : COLORS[Math.min(kind, COLORS.length - 1)];
            int px = sx(m.x()), py = sy(m.z());
            if (px < mapX || py < mapY || px >= mapX + mapSize || py >= mapY + mapSize) {
                continue;
            }
            int r = kind == MapPayload.OWN_BASE || kind == MapPayload.ENEMY_BASE || kind == MapPayload.LAIR ? 3 : 2;
            int argb = (stale ? 0x80000000 : 0xFF000000) | color;
            if (kind == MapPayload.ENEMY_BASE) {
                cross(g, px, py, r + 1, argb); // shape, not just colour: some factions' colours are red too
            } else {
                g.fill(px - r, py - r, px + r + 1, py + r + 1, argb);
            }
            if (kind == MapPayload.OWN_BASE) {
                g.renderOutline(px - r - 1, py - r - 1, 2 * r + 3, 2 * r + 3, 0xFFFFFFFF);
            }
            if (Math.abs(mouseX - px) <= r + 1 && Math.abs(mouseY - py) <= r + 1) {
                hovered = m;
            }
        }
        var player = Minecraft.getInstance().player;
        if (player != null) {
            int px = sx(player.getX()), py = sy(player.getZ());
            if (px >= mapX && py >= mapY && px < mapX + mapSize && py < mapY + mapSize) {
                g.fill(px - 1, py - 4, px + 2, py + 5, 0xFFFFFFFF);
                g.fill(px - 4, py - 1, px + 5, py + 2, 0xFFFFFFFF);
            }
        }
        g.drawString(font, "N", mapX + mapSize / 2 - 2, mapY + 3, 0xFFDDDDCC);
        g.drawString(font, (2 * radius()) + " blocks across", mapX + 3, mapY + mapSize - 11, 0xFFBBBBAA);
        // legend
        for (int i = 0; i < NAMES.length; i++) {
            int y = mapY + i * 11;
            int color = i == MapPayload.OWN_BASE ? data.titleColor() : COLORS[i];
            if (i == MapPayload.ENEMY_BASE) {
                cross(g, listX + 3, y + 5, 3, 0xFF000000 | color);
            } else {
                g.fill(listX, y + 2, listX + 6, y + 8, 0xFF000000 | color);
            }
            if (i == MapPayload.OWN_BASE) {
                g.renderOutline(listX - 1, y + 1, 8, 8, 0xFFFFFFFF);
            }
            g.drawString(font, NAMES[i], listX + 10, y + 1, 0xFFE0E0E0);
        }
        // written reports
        g.fill(listX, listY, listX + listW, listY + listH, 0xC0101018);
        g.enableScissor(listX + 1, listY + 1, listX + listW - 1, listY + listH - 1);
        int y = listY + 4 - (int) scroll;
        for (FormattedCharSequence row : rows) {
            if (y > listY - font.lineHeight && y < listY + listH) {
                g.drawString(font, row, listX + 4, y, 0xFFD8D8D8);
            }
            y += font.lineHeight + 1;
        }
        g.disableScissor();
        if (hovered != null) {
            g.renderTooltip(font, Component.literal(hovered.label()), mouseX, mouseY);
        }
    }

    private static void cross(GuiGraphics g, int x, int y, int r, int argb) {
        for (int i = -r; i <= r; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, argb);
            g.fill(x + i, y - i, x + i + 1, y - i + 1, argb);
            g.fill(x + i + 1, y + i, x + i + 2, y + i + 1, argb);
            g.fill(x + i + 1, y - i, x + i + 2, y - i + 1, argb);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
