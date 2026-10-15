package triplesix.babyfeed;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Invalidates pending network requests and commits when a Drive link changes. */
final class SyncSession {
    private long generation;
    private final Set<Runnable> connections = new HashSet<>();

    interface Action<T> {
        T run() throws Exception;
    }

    static final class Cancelled extends IOException {
        Cancelled() {
            super("Phiên đồng bộ đã kết thúc.");
        }
    }

    synchronized Ticket capture() {
        return new Ticket(generation);
    }

    synchronized void invalidate(Runnable updateLink) {
        generation++;
        updateLink.run();
        for (Runnable disconnect : connections) {
            disconnect.run();
        }
        connections.clear();
    }

    final class Ticket {
        private final long expectedGeneration;

        private Ticket(long expectedGeneration) {
            this.expectedGeneration = expectedGeneration;
        }

        void check() throws Cancelled {
            synchronized (SyncSession.this) {
                if (expectedGeneration != generation || Thread.currentThread().isInterrupted()) {
                    throw new Cancelled();
                }
            }
        }

        <T> T commit(Action<T> action) throws Exception {
            synchronized (SyncSession.this) {
                check();
                return action.run();
            }
        }

        void attach(Runnable disconnect) throws Cancelled {
            synchronized (SyncSession.this) {
                check();
                connections.add(disconnect);
            }
        }

        void detach(Runnable disconnect) {
            synchronized (SyncSession.this) {
                connections.remove(disconnect);
            }
        }
    }
}
