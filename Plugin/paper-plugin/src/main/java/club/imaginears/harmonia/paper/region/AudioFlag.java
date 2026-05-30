package club.imaginears.harmonia.paper.region;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import java.util.logging.Logger;

/**
 * The custom WorldGuard {@code harmonia-audio} flag. A region's string value for this flag
 * is the track ID played while a player stands inside it — this is how audio is "connected
 * to a region" (set in-game with {@code /rg flag <region> harmonia-audio <trackId>}).
 *
 * <p>Custom flags must be registered <em>before</em> WorldGuard loads its regions, which it
 * does in its own {@code onEnable}. Register this from the plugin's {@code onLoad()} — never
 * {@code onEnable()} — or WorldGuard rejects it.
 */
public final class AudioFlag {
    public static final String NAME = "harmonia-audio";

    private static StringFlag flag;

    private AudioFlag() {}

    /** Registers the flag, recovering the existing instance on {@code /reload}. Call from {@code onLoad()}. */
    public static void register(Logger logger) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StringFlag created = new StringFlag(NAME);
            registry.register(created);
            flag = created;
        } catch (FlagConflictException e) {
            // Already registered (typically after /reload) — reuse the existing flag if compatible.
            Flag<?> existing = registry.get(NAME);
            if (existing instanceof StringFlag sf) {
                flag = sf;
            } else {
                logger.warning("WorldGuard flag '" + NAME + "' exists with an unexpected type; audio regions disabled.");
            }
        }
    }

    /** The registered flag, or {@code null} if WorldGuard is absent or registration failed. */
    public static StringFlag flag() {
        return flag;
    }
}
