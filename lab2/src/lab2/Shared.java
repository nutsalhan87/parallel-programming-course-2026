package lab2;

import lab2.SchedLock.SchedDeadLockException;
import lab2.Scheduler.Task;

/* Этап 1: общая переменная. Перед каждым get/set — точка переключения (пауза планировщика),
 * после — строка в лог: чтение "<имя> -> <значение>", запись "<имя> = <значение>".
 * getAcquire/setRelease для планировщика ведут себя так же, как get/set. */
public final class Shared<T> {
    private final String name;
    private T value;

    public Shared(String name, T value) { this.name = name; this.value = value; }

    /** Чтение с паузой перед ним. */
    public T get() {
        if (Thread.currentThread() instanceof Task currentTask) {
            Scheduler scheduler = currentTask.scheduler;
            scheduler.readyForTask.release();
            currentTask.wake.acquireUninterruptibly();
            if (scheduler.deadlocked) {
                throw new SchedDeadLockException();
            }
            if (scheduler.logging) {
                scheduler.log.add(currentTask.getName() + " " + name + " -> " + value);
            }
        }
        return value;
    }

    /** Запись с паузой перед ней. */
    public void set(T v) {
        if (Thread.currentThread() instanceof Task currentTask) {
            Scheduler scheduler = currentTask.scheduler;
            scheduler.readyForTask.release();
            currentTask.wake.acquireUninterruptibly();
            if (scheduler.deadlocked) {
                throw new SchedDeadLockException();
            }
            if (scheduler.logging) {
                scheduler.log.add(currentTask.getName() + " " + name + " = " + v);
            }
        }
        value = v;
    }

    /** То же, что get(): планировщик не моделирует порядки памяти. */
    public T getAcquire() {
        return get();
    }

    /** То же, что set(v). */
    public void setRelease(T v) {
        set(v);
    }

    /** Чтение без паузы и без лога: для проверок после прогона и условий await. */
    public T peek() { return value; }
}
