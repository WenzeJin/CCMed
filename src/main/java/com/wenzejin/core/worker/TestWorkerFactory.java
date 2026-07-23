package com.wenzejin.core.worker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.util.Map;

/**
 * Factory for creating {@link TestWorker} instances.
 *
 * <p>Built-in worker types are resolved by name (case-insensitive):
 * <ul>
 *   <li>{@code infuse} — {@link InfuseWorker}</li>
 *   <li>{@code infuse-old} — {@link InfuseOldWorker}</li>
 *   <li>{@code mg} — {@link MGWorker}</li>
 *   <li>{@code cmid} — {@link CMIDWorker}</li>
 *   <li>{@code standard} — {@link StandardWorker}</li>
 * </ul>
 *
 * <p>External workers are supported by specifying a fully-qualified class name
 * as the {@code workerType}. The class must implement {@link TestWorker} and
 * expose a constructor with the signature {@code (String workspace, Map&lt;String, Object&gt; arguments)}.
 * External worker jars should be placed on the classpath (e.g. via {@code java -cp}).
 */
public class TestWorkerFactory {

    private static final Logger logger = LogManager.getLogger("TestWorkerFactory");

    private TestWorkerFactory() {}

    /**
     * Creates a {@link TestWorker} by name or fully-qualified class name.
     *
     * @param workerType built-in name or fully-qualified class name
     * @param workspace  workspace root path
     * @param arguments  configuration arguments from the config file
     * @return the worker instance, or {@code null} if the type could not be resolved
     */
    public static TestWorker getTestWorker(String workerType, String workspace, Map<String, Object> arguments) {
        // 1. Try built-in names (case-insensitive)
        TestWorker builtin = resolveBuiltin(workerType, workspace, arguments);
        if (builtin != null) {
            return builtin;
        }

        // 2. Try to load as an external fully-qualified class name
        return loadExternal(workerType, workspace, arguments);
    }

    private static TestWorker resolveBuiltin(String workerType, String workspace, Map<String, Object> arguments) {
        return switch (workerType.toLowerCase()) {
            case "infuse"     -> new InfuseWorker(workspace, arguments);
            case "infuse-old" -> new InfuseOldWorker(workspace, arguments);
            case "mg"         -> new MGWorker(workspace, arguments);
            case "cmid"       -> new CMIDWorker(workspace, arguments);
            case "standard"   -> new StandardWorker(workspace, arguments);
            default           -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static TestWorker loadExternal(String className, String workspace, Map<String, Object> arguments) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!TestWorker.class.isAssignableFrom(clazz)) {
                logger.error("Class {} does not implement TestWorker", className);
                return null;
            }

            // Try (String, Map) constructor first, then fall back to (String, Map<String, Object>)
            Constructor<?> ctor = findCompatibleConstructor((Class<? extends TestWorker>) clazz);
            if (ctor == null) {
                logger.error("No compatible constructor found for {}. Expected (String, Map).", className);
                return null;
            }

            return (TestWorker) ctor.newInstance(workspace, arguments);
        } catch (ClassNotFoundException e) {
            logger.error("Worker class not found: {}. Ensure the jar is on the classpath.", className);
            return null;
        } catch (ReflectiveOperationException e) {
            logger.error("Failed to instantiate worker {}: {}", className, e.getMessage(), e);
            return null;
        }
    }

    private static Constructor<?> findCompatibleConstructor(Class<? extends TestWorker> clazz) {
        for (Constructor<?> ctor : clazz.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 2
                    && params[0] == String.class
                    && params[1].isAssignableFrom(Map.class)) {
                return ctor;
            }
        }
        return null;
    }
}
