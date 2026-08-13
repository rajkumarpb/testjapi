package javapi.request;

import java.util.ArrayList;
import java.util.List;

public final class BackgroundTasks {

    private static final BackgroundTasks EMPTY = new BackgroundTasks(List.of());

    private final List<Runnable> tasks;

    private BackgroundTasks(List<Runnable> tasks) {
        this.tasks = tasks;
    }

    public static BackgroundTasks empty() {
        return EMPTY;
    }

    public static BackgroundTasks of(Runnable... tasks) {
        return new BackgroundTasks(List.of(tasks));
    }

    public BackgroundTasks add(Runnable task) {
        List<Runnable> next = new ArrayList<>(tasks);
        next.add(task);
        return new BackgroundTasks(List.copyOf(next));
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }

    public void run() {
        for (Runnable task : tasks) {
            try {
                task.run();
            } catch (Throwable ignored) {
            }
        }
    }
}
