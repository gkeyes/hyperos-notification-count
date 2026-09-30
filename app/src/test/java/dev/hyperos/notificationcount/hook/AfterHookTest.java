package dev.hyperos.notificationcount.hook;

import org.junit.Test;
import java.lang.reflect.Executable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.libxposed.api.XposedInterface;

import static org.junit.Assert.*;

public class AfterHookTest {
    @Test public void moduleFailureKeepsOriginalResultAndRunsOriginalOnce() throws Throwable {
        FakeChain chain = new FakeChain();
        AtomicInteger failures = new AtomicInteger();
        Object result = new AfterHook(c -> { throw new IllegalArgumentException("module"); },
                error -> failures.incrementAndGet()).intercept(chain);
        assertSame(chain.result, result);
        assertEquals(1, chain.calls);
        assertEquals(1, failures.get());
    }

    @Test public void originalFailurePropagatesWithoutModuleActionOrSecondProceed() throws Throwable {
        FakeChain chain = new FakeChain();
        chain.error = new IllegalStateException("original");
        AtomicInteger callbacks = new AtomicInteger();
        try {
            new AfterHook(c -> callbacks.incrementAndGet(), error -> callbacks.incrementAndGet()).intercept(chain);
            fail("Original exception must propagate");
        } catch (IllegalStateException error) {
            assertSame(chain.error, error);
        }
        assertEquals(1, chain.calls);
        assertEquals(0, callbacks.get());
    }

    @Test public void moduleRunsAfterOriginalAndPreservesResult() throws Throwable {
        FakeChain chain = new FakeChain();
        Object result = new AfterHook(c -> assertEquals(1, chain.calls), error -> fail()).intercept(chain);
        assertSame(chain.result, result);
        assertEquals(1, chain.calls);
    }

    private static final class FakeChain implements XposedInterface.Chain {
        final Object result = new Object();
        int calls;
        Throwable error;
        @Override public Executable getExecutable() { return null; }
        @Override public Object getThisObject() { return null; }
        @Override public List<Object> getArgs() { return List.of(); }
        @Override public Object getArg(int index) { return getArgs().get(index); }
        @Override public Object proceed() throws Throwable {
            calls++;
            if (error != null) throw error;
            return result;
        }
        @Override public Object proceed(Object[] args) throws Throwable { return proceed(); }
        @Override public Object proceedWith(Object receiver) throws Throwable { return proceed(); }
        @Override public Object proceedWith(Object receiver, Object[] args) throws Throwable { return proceed(); }
    }
}
