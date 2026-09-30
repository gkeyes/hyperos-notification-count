package dev.hyperos.notificationcount.hook;

/** Main-thread registration progress; a failed step remains retryable without duplicating earlier work. */
final class ListenerRegistration {
    @FunctionalInterface interface Step { void run() throws Throwable; }
    private boolean collectionRegistered;
    private boolean beforeRenderRegistered;

    void ensure(Step collection, Step beforeRender) throws Throwable {
        if (!collectionRegistered) {
            collection.run();
            collectionRegistered = true;
        }
        if (!beforeRenderRegistered) {
            beforeRender.run();
            beforeRenderRegistered = true;
        }
    }
}
