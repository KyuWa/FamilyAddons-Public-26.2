package org.kyowa.familyaddons.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs a task after {@code delay} client ticks.
 */
public final class Scheduler {

    private static final class Task {
        int ticks;
        final Runnable runnable;

        Task(int ticks, Runnable runnable) {
            this.ticks = ticks;
            this.runnable = runnable;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private Scheduler() {}

    public static void scheduleTask(int delay, Runnable runnable) {
        synchronized (TASKS) {
            TASKS.add(new Task(delay, runnable));
        }
    }

    /** Called once per client tick. */
    public static void tick() {
        List<Task> due = new ArrayList<>();
        synchronized (TASKS) {
            for (int i = 0; i < TASKS.size(); i++) {
                Task t = TASKS.get(i);
                t.ticks--;
                if (t.ticks <= 0) {
                    due.add(t);
                    TASKS.remove(i);
                    i--;
                }
            }
        }
        for (Task t : due) {
            try {
                t.runnable.run();
            } catch (Exception e) {
                FamilyStorage.LOGGER.error("Scheduled task failed", e);
            }
        }
    }
}
