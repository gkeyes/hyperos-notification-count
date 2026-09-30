package dev.hyperos.notificationcount.hook;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Exact members verified in the extracted HyperOS 4 SystemUI DEX. */
final class HostAccess {
    private final ClassLoader loader;

    HostAccess(ClassLoader loader) {
        this.loader = loader;
    }

    Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Fields such as notification identity can live on a superclass.
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    static Method method(Class<?> type, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    static Object call(Method method, Object receiver, Object... arguments) throws Throwable {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
