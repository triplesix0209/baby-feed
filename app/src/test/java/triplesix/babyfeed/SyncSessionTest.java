package triplesix.babyfeed;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SyncSessionTest {
    @Test
    public void disconnectClosesConnectionsAndRejectsOldCommits() throws Exception {
        SyncSession sessions = new SyncSession();
        SyncSession.Ticket old = sessions.capture();
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger commits = new AtomicInteger();
        old.attach(closed::incrementAndGet);
        sessions.invalidate(() -> {});
        assertEquals(1, closed.get());
        assertThrows(SyncSession.Cancelled.class, () -> old.commit(commits::incrementAndGet));
        assertThrows(SyncSession.Cancelled.class, () -> old.attach(closed::incrementAndGet));
        assertEquals(0, commits.get());
        assertEquals(1, (int) sessions.capture().commit(commits::incrementAndGet));
    }

    @Test
    public void completedConnectionsAreNotClosedAgain() throws Exception {
        SyncSession sessions = new SyncSession();
        SyncSession.Ticket ticket = sessions.capture();
        AtomicInteger calls = new AtomicInteger();
        Runnable connection = calls::incrementAndGet;
        ticket.attach(connection);
        ticket.detach(connection);
        sessions.invalidate(() -> {});
        assertEquals(0, calls.get());
    }

    @Test
    public void queuedWorkerCannotCommitAfterFamilyChanges() throws Exception {
        SyncSession sessions = new SyncSession();
        SyncSession.Ticket worker = sessions.capture();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch networkComplete = new CountDownLatch(1);
        AtomicInteger commits = new AtomicInteger();
        try {
            Future<Boolean> result =
                    executor.submit(
                            () -> {
                                if (!networkComplete.await(5, TimeUnit.SECONDS))
                                    throw new AssertionError("timeout");
                                try {
                                    worker.commit(commits::incrementAndGet);
                                    return false;
                                } catch (SyncSession.Cancelled expected) {
                                    return true;
                                }
                            });
            sessions.invalidate(() -> {});
            networkComplete.countDown();
            assertTrue(result.get(5, TimeUnit.SECONDS));
            assertEquals(0, commits.get());
        } finally {
            networkComplete.countDown();
            executor.shutdownNow();
        }
    }
}
