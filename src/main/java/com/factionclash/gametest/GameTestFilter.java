package com.factionclash.gametest;

import com.factionclash.FactionClash;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.lang.reflect.Field;
import java.util.List;

/**
 * A developer's filter for the GameTest server: with the system property {@code factionclash.gametestFilter} set (for
 * example {@code JAVA_TOOL_OPTIONS=-Dfactionclash.gametestFilter=herofightmobility,heroraid ./gradlew runGameTestServer}),
 * only the tests whose name (the test method's name in lower case; the class name is not part of it) contains one of the
 * comma-separated words run. Without the property it does nothing; it never runs outside a GameTest server.
 */
@EventBusSubscriber(modid = FactionClash.MODID)
public final class GameTestFilter {
    private GameTestFilter() {
    }

    @SubscribeEvent
    @SuppressWarnings("unchecked")
    public static void onAboutToStart(ServerAboutToStartEvent e) {
        String filter = System.getProperty("factionclash.gametestFilter");
        if (filter == null || filter.isBlank() || !(e.getServer() instanceof GameTestServer server)) {
            return;
        }
        String[] words = filter.toLowerCase(java.util.Locale.ROOT).split(",");
        try {
            Field f = GameTestServer.class.getDeclaredField("testFunctions");
            f.setAccessible(true);
            List<TestFunction> tests = (List<TestFunction>) f.get(server);
            int before = tests.size();
            tests.removeIf(t -> {
                String name = t.testName().toLowerCase(java.util.Locale.ROOT);
                for (String w : words) {
                    if (!w.isBlank() && name.contains(w.trim())) {
                        return false;
                    }
                }
                return true;
            });
            FactionClash.LOGGER.info("GameTest filter '{}': {} of {} tests", filter, tests.size(), before);
        } catch (ReflectiveOperationException ex) {
            FactionClash.LOGGER.warn("GameTest filter '{}' not applied: {}", filter, ex.toString());
        }
    }
}
