package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    private int nextIdx = 0;
    private int last = -1;
    private final List<Task> tasks = new ArrayList<>();
    private final StringBuilder trace = new StringBuilder();
    private final AtomicInteger tasksUndone = new AtomicInteger(0);
    final Semaphore readyForTask = new Semaphore(0);

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        var idx = nextIdx++;
        String name = new String(new char[]{(char)('A' + idx)}); // Буду считать, что их меньше 27
        tasks.add(new Task(body, name, this));
        tasksUndone.incrementAndGet();
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        for (Task task : tasks) {
            task.start();
        }
        readyForTask.acquireUninterruptibly(tasks.size());
        readyForTask.release();
        while (tasksUndone.get() > 0) {
            readyForTask.acquireUninterruptibly();
            var enabled = new ArrayList<Integer>();
            for (int i = 0; i < tasks.size(); ++i) {
                if (tasks.get(i).enabled.get()) {
                    enabled.add(i);
                }
            }
            last = strategy.pick(enabled, last);
            if (last != -1) {
                var task = tasks.get(last);
                trace.append(task.getName());
                task.wake.release();
            } else {
                readyForTask.release();
            }
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        if (Thread.currentThread() instanceof Task currentTask) {
            Scheduler scheduler = currentTask.currentScheduler;
            if (scheduler.logging) {
                scheduler.log.add(currentTask.getName() + " window");
            }
        }
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        if (Thread.currentThread() instanceof Task currentTask) {
            Scheduler scheduler = currentTask.currentScheduler;

            currentTask.enabled.set(false);
            scheduler.readyForTask.release();
            while (!cond.getAsBoolean()) {
                Thread.yield();
            }
            currentTask.enabled.set(true);
            currentTask.wake.acquireUninterruptibly();

            return;
        }

        while (!cond.getAsBoolean()) {
            Thread.yield();
        }
    }

    public static class Task extends Thread {        
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final Semaphore wake = new Semaphore(0);
        final Scheduler currentScheduler;
        private final Runnable body;

        public Task(Runnable body, String name, Scheduler scheduler) {
            super(name);
            this.body = body;
            this.currentScheduler = scheduler;
        }

        @Override 
        public void run() {
            try {
                body.run();
            } finally {
                enabled.set(false);
                currentScheduler.tasksUndone.decrementAndGet();
                currentScheduler.readyForTask.release();
            }
        }
    }
}
