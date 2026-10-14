package net.johnbrooks.mh.compat;

import net.johnbrooks.mh.Main;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TownyHook {
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static volatile Bindings bindings;
    private static volatile boolean initialized;

    private TownyHook() {
    }

    public static void refresh() {
        synchronized (TownyHook.class) {
            bindings = null;
            initialized = false;
            FAILURE_LOGGED.set(false);
        }
    }

    public static boolean isAvailable() {
        return bindings() != null;
    }

    public static boolean canUse(Player player, Location location) {
        Bindings current = bindings();
        if (current == null) {
            logFailure("Towny is unavailable");
            return false;
        }

        try {
            Object town = current.getTown.invoke(current.townyApi, location);
            if (town == null) {
                return true;
            }
            Object result = current.hasResident.invoke(town, player.getName());
            return result instanceof Boolean && (Boolean) result;
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

        synchronized (TownyHook.class) {
            if (!initialized) {
                bindings = createBindings();
                initialized = true;
            }
            return bindings;
        }
    }

    private static Bindings createBindings() {
        try {
            Class<?> townyApiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Class<?> townClass = Class.forName("com.palmergames.bukkit.towny.object.Town");
            Object townyApi = townyApiClass.getMethod("getInstance").invoke(null);
            if (townyApi == null) {
                return null;
            }
            Method getTown = townyApiClass.getMethod("getTown", Location.class);
            Method hasResident = townClass.getMethod("hasResident", String.class);
            return new Bindings(townyApi, getTown, hasResident);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static void logFailure(String detail) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            Main.logger.warning("Towny protection check failed; denying MobCapture actions: " + detail);
        }
    }

    private record Bindings(Object townyApi, Method getTown, Method hasResident) {
    }
}
