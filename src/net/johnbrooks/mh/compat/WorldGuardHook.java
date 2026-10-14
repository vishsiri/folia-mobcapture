package net.johnbrooks.mh.compat;

import net.johnbrooks.mh.Main;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WorldGuardHook {
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static volatile Bindings bindings;
    private static volatile boolean initialized;

    private WorldGuardHook() {
    }

    public static void refresh() {
        synchronized (WorldGuardHook.class) {
            bindings = null;
            initialized = false;
            FAILURE_LOGGED.set(false);
        }
    }

    public static boolean isAvailable() {
        return bindings() != null;
    }

    public static boolean canBuild(Player player, Location location) {
        return testState(player, location, false);
    }

    public static boolean canSpawnMobs(Player player, Location location) {
        return testState(player, location, true);
    }

    private static boolean testState(Player player, Location location, boolean mobSpawning) {
        Bindings current = bindings();
        if (current == null) {
            logFailure("WorldGuard is unavailable");
            return false;
        }

        try {
            Object query = current.createQuery.invoke(current.regionContainer);
            Object worldEditLocation = current.adaptLocation.invoke(null, location);
            Object localPlayer = current.wrapPlayer.invoke(current.worldGuardPlugin, player);
            Object flags = mobSpawning ? current.mobSpawningFlags : current.buildFlags;
            Object result = current.testState.invoke(query, worldEditLocation, localPlayer, flags);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            logFailure(ex.getMessage());
            return false;
        }
    }

    private static Bindings bindings() {
        Bindings current = bindings;
        if (current != null || initialized) {
            return current;
        }

        synchronized (WorldGuardHook.class) {
            if (!initialized) {
                bindings = createBindings();
                initialized = true;
            }
            return bindings;
        }
    }

    private static Bindings createBindings() {
        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Class<?> worldGuardPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Class<?> regionAssociableClass = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
            Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");

            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Method createQuery = regionContainer.getClass().getMethod("createQuery");
            Object query = createQuery.invoke(regionContainer);
            Method adaptLocation = bukkitAdapterClass.getMethod("adapt", Location.class);
            Object worldGuardPlugin = worldGuardPluginClass.getMethod("inst").invoke(null);
            if (query == null || worldGuardPlugin == null) {
                return null;
            }
            Method wrapPlayer = worldGuardPluginClass.getMethod("wrapPlayer", Player.class);
            Class<?> flagsArrayClass = Array.newInstance(stateFlagClass, 0).getClass();
            Method testState = query.getClass().getMethod(
                    "testState",
                    adaptLocation.getReturnType(),
                    regionAssociableClass,
                    flagsArrayClass
            );
            Object buildFlags = Array.newInstance(stateFlagClass, 1);
            Object mobSpawningFlags = Array.newInstance(stateFlagClass, 1);
            Array.set(buildFlags, 0, flagsClass.getField("BUILD").get(null));
            Array.set(mobSpawningFlags, 0, flagsClass.getField("MOB_SPAWNING").get(null));

            return new Bindings(
                    regionContainer,
                    worldGuardPlugin,
                    createQuery,
                    adaptLocation,
                    wrapPlayer,
                    testState,
                    buildFlags,
                    mobSpawningFlags
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static void logFailure(String detail) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            Main.logger.warning("WorldGuard protection check failed; denying MobCapture actions: " + detail);
        }
    }

    private record Bindings(
            Object regionContainer,
            Object worldGuardPlugin,
            Method createQuery,
            Method adaptLocation,
            Method wrapPlayer,
            Method testState,
            Object buildFlags,
            Object mobSpawningFlags
    ) {
    }
}
