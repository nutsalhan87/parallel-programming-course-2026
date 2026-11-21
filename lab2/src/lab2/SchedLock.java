package lab2;

import lab2.Scheduler.Task;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    boolean locked = false;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) { 
        this.name = name;
        s.locks.put(this, null);
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        if (Thread.currentThread() instanceof Task currentTask) {
            Scheduler scheduler = currentTask.scheduler;

            currentTask.condition = () -> !this.locked;
            currentTask.enabled.set(false);
            scheduler.readyForTask.release();
            currentTask.wake.acquireUninterruptibly();
            currentTask.enabled.set(true);
            
            if (scheduler.deadlocked) {
                throw new SchedDeadLockException();
            }
            
            this.locked = true;
            scheduler.locks.put(this, currentTask);
            
            if (scheduler.logging) {
                scheduler.log.add(currentTask.getName() + " lock " + name);
            }
        }
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        if (Thread.currentThread() instanceof Task currentTask) {
            Scheduler scheduler = currentTask.scheduler;
            if (scheduler.deadlocked) {
                return;
            }

            scheduler.readyForTask.release();
            currentTask.wake.acquireUninterruptibly();

            if (scheduler.deadlocked) {
                throw new SchedDeadLockException();
            }

            this.locked = false;
            scheduler.locks.put(this, null);

            if (scheduler.logging) {
                scheduler.log.add(currentTask.getName() + " unlock " + name);
            }
        }
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public static class SchedDeadLockException extends RuntimeException {
        SchedDeadLockException() { }
    }
}
