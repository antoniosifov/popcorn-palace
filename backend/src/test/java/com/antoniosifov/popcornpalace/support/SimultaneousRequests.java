package com.antoniosifov.popcornpalace.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs the same action on many threads at the same moment. Every thread is
 * started and parked at a gate first; only then is the gate opened, so the
 * actions really do overlap instead of running one after another.
 */
public final class SimultaneousRequests
{
    private SimultaneousRequests()
    {
    }

    /** @return the result of every call, in no particular order */
    public static <T> List<T> run(int count, Callable<T> action) throws Exception
    {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch allReady = new CountDownLatch(count);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Future<T>> pending = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                pending.add(executor.submit(() -> {
                    allReady.countDown();
                    gate.await();
                    return action.call();
                }));
            }
            if (!allReady.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Not every thread became ready in time");
            }
            gate.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : pending) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
        finally {
            executor.shutdownNow();
        }
    }
}
