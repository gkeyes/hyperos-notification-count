package dev.hyperos.notificationcount.hook;

import io.github.libxposed.api.XposedInterface;

/** Keeps the original call outside module error handling, so it runs exactly once. */
public final class AfterHook implements XposedInterface.Hooker {
    @FunctionalInterface
    public interface Action {
        void run(XposedInterface.Chain chain) throws Throwable;
    }

    @FunctionalInterface
    public interface Failure {
        void accept(Throwable error);
    }

    private final Action action;
    private final Failure failure;

    public AfterHook(Action action, Failure failure) {
        this.action = action;
        this.failure = failure;
    }

    @Override
    public Object intercept(XposedInterface.Chain chain) throws Throwable {
        Object result = chain.proceed();
        try {
            action.run(chain);
        } catch (Throwable error) {
            failure.accept(error);
        }
        return result;
    }
}
