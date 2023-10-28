import org.slf4j.Logger
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class Scheduler(val logger: Logger) {
    private val thread: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    fun schedule(delay: Long, repeatInterval: Long, unit: TimeUnit, task: Runnable): ScheduledFuture<*> {
        logger.info("Executing next at ${Date(repeatInterval + System.currentTimeMillis() + delay)}")

        return thread.scheduleAtFixedRate(task, delay, repeatInterval, unit)
    }

    fun runImmediate(task: Runnable): CompletableFuture<Void> {
        logger.info("Executing task immediate $task")
        return CompletableFuture.runAsync(task, thread)
    }
}