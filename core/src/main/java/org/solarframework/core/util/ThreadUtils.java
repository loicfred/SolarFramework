package org.solarframework.core.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ThreadUtils {

    public static void ShutdownWithTimeout(ExecutorService E, double minutes, String threadName) {
        try {
            E.shutdown();
            if (!E.awaitTermination((long) (minutes * 60), TimeUnit.SECONDS)) {
                E.shutdownNow();
                if (threadName != null && threadName.length() > 0) {
                    System.out.println("There was an issue in " + threadName + ".");
                }
            }
        } catch (Exception e) {
            if (threadName != null && threadName.length() > 0) {
                System.out.println("There was an issue in " + threadName + ".");
            }
            E.shutdownNow();
        } finally {
            System.gc();
        }
    }
    public static void ShutdownAfterAction(ExecutorService E, double minutes, String threadName, Future<?> future) {
        E.execute(() -> {
            try {
                E.shutdown();
                future.get((long) (minutes * 60), TimeUnit.SECONDS);
            } catch (Exception e) {
                if (threadName != null && threadName.length() > 0) {
                    System.out.println("There was an issue in " + threadName + ".");
                } future.cancel(true); // Cancel the task if it exceeded the time limit
            } finally {
                E.shutdownNow();
                System.gc();
            }
        });
    }

}
