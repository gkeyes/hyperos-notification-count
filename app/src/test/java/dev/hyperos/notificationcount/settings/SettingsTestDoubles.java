package dev.hyperos.notificationcount.settings;

import android.content.SharedPreferences;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/** Deterministic IPC stand-ins, including libxposed's optimistic app-side cache. */
final class SettingsTestDoubles {
    private SettingsTestDoubles() { }

    static final class QueuedExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override public void execute(Runnable command) { tasks.addLast(command); }

        int pending() { return tasks.size(); }

        void runNext() { take(false).run(); }

        void runLast() { take(true).run(); }

        void runAll() {
            while (!tasks.isEmpty()) runNext();
        }

        private Runnable take(boolean last) {
            Runnable task = last ? tasks.pollLast() : tasks.pollFirst();
            if (task == null) throw new AssertionError("No queued task");
            return task;
        }
    }

    static final class OptimisticPreferences implements InvocationHandler {
        private final Map<String, Object> cache = new LinkedHashMap<>();
        private final Map<String, Object> persisted = new LinkedHashMap<>();
        private final Deque<Boolean> commitResults = new ArrayDeque<>();
        final List<Integer> committedMasks = new ArrayList<>();
        int intReads;
        final SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, this);

        OptimisticPreferences() { }

        OptimisticPreferences(int mask) {
            cache.put(FilterPreferences.EXCLUDED_MASK, mask);
            persisted.put(FilterPreferences.EXCLUDED_MASK, mask);
        }

        void queueCommitResults(boolean... results) {
            for (boolean result : results) commitResults.addLast(result);
        }

        int cachedMask() { return maskIn(cache); }

        int persistedMask() { return maskIn(persisted); }

        OptimisticPreferences freshConnection() {
            OptimisticPreferences fresh = new OptimisticPreferences();
            fresh.cache.putAll(persisted);
            fresh.persisted.putAll(persisted);
            return fresh;
        }

        @Override public Object invoke(Object proxy, Method method, Object[] arguments) {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, name, arguments);
            }
            if (name.equals("edit")) return editor();
            if (name.equals("getAll")) return new LinkedHashMap<>(cache);
            if (name.equals("contains")) return cache.containsKey(arguments[0]);
            // Count mask reads as load cycles, independently of the new duration setting.
            if (name.equals("getInt") && FilterPreferences.EXCLUDED_MASK.equals(arguments[0])) intReads++;
            if (name.startsWith("get") && arguments != null && arguments.length == 2) {
                return cache.getOrDefault(arguments[0], arguments[1]);
            }
            if (name.equals("registerOnSharedPreferenceChangeListener")
                    || name.equals("unregisterOnSharedPreferenceChangeListener")) return null;
            throw new AssertionError("Unexpected preferences call: " + name);
        }

        private SharedPreferences.Editor editor() {
            Map<String, Object> changes = new LinkedHashMap<>();
            boolean[] clear = {false};
            return (SharedPreferences.Editor) Proxy.newProxyInstance(
                    SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class},
                    (proxy, method, arguments) -> {
                        String name = method.getName();
                        if (method.getDeclaringClass() == Object.class) {
                            return objectMethod(proxy, name, arguments);
                        }
                        if (name.startsWith("put")) {
                            changes.put((String) arguments[0], arguments[1]);
                            return proxy;
                        }
                        if (name.equals("remove")) {
                            changes.put((String) arguments[0], null);
                            return proxy;
                        }
                        if (name.equals("clear")) {
                            clear[0] = true;
                            return proxy;
                        }
                        if (name.equals("commit")) {
                            // Match RemotePreferences.Editor: cache changes even if IPC fails.
                            merge(cache, changes, clear[0]);
                            committedMasks.add(cachedMask());
                            boolean success = commitResults.isEmpty() || commitResults.removeFirst();
                            if (success) merge(persisted, changes, clear[0]);
                            return success;
                        }
                        throw new AssertionError("Unexpected editor call: " + name);
                    });
        }

        private static int maskIn(Map<String, Object> values) {
            return (Integer) values.getOrDefault(FilterPreferences.EXCLUDED_MASK, 0);
        }

        private static void merge(Map<String, Object> target, Map<String, Object> changes,
                boolean clear) {
            if (clear) target.clear();
            for (Map.Entry<String, Object> change : changes.entrySet()) {
                if (change.getValue() == null) target.remove(change.getKey());
                else target.put(change.getKey(), change.getValue());
            }
        }

        private static Object objectMethod(Object proxy, String name, Object[] arguments) {
            return switch (name) {
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "TestRemotePreferences";
                default -> throw new AssertionError("Unexpected Object call: " + name);
            };
        }
    }
}
