import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

lateinit var wikipediaFeaturedBot: WikipediaFeaturedBot

class WikipediaFeaturedBot {
    private val logger: Logger = LoggerFactory.getLogger("WikipediaFeaturedBot")

    private val configLocation: File = File(WikipediaFeaturedBot::class.java.protectionDomain.codeSource.location.toURI()).parentFile
    private lateinit var configuration: WikipediaBotConfiguration

    private val scheduler = Scheduler(logger)

    fun start(arguments: Array<String>) {
        logger.info("Starting with arguments ${arguments.toList()}")

        configuration = ConfigurationLoader(logger).load<WikipediaBotConfiguration>(configLocation, "config.json")

        val jda: JDA = try {
            JDABuilder.createLight(configuration.discordBotToken)
                .disableCache(CacheFlag.values().toList())
                .setEnableShutdownHook(false)
                .build()
                .awaitReady()
        } catch (exception: Exception) {
            logger.warn("Failed to start JDA", exception)
            exception.printStackTrace()
            return
        }

        val frontPageTask = SendFrontPageTask(logger, configuration, jda)

        logger.info("Connected to channel [${frontPageTask.getChannel()!!.name}]")

        if (arguments.getOrNull(0) == "immediate") {
            logger.info("Executing message immediately and shutting down")
            wikipediaFeaturedBot.scheduler.runImmediate(frontPageTask).thenAccept { exitProcess(1) }
        }

        wikipediaFeaturedBot.scheduleMessages(frontPageTask, configuration)
    }

    private fun scheduleMessages(task: Runnable, config: WikipediaBotConfiguration) {
        val now: ZonedDateTime = ZonedDateTime.now().withZoneSameInstant(TimeZone.getTimeZone("CST").toZoneId())
        var time: ZonedDateTime = now
            .withHour(config.sendHour)
            .withMinute(config.sendMinute)
            .withSecond(0)

        if (time.isBefore(now) || time.isEqual(now)) {
            time = now
                .plusDays(1)
                .withHour(config.sendHour)
                .withMinute(config.sendMinute)
                .withSecond(0)
        }

        val delay = (time.toEpochSecond() * 1000L) - System.currentTimeMillis()

        logger.info("Executing first message at ${Date(delay + System.currentTimeMillis())}")
        scheduler.schedule(delay, config.sendDelay, TimeUnit.MILLISECONDS, task)
    }
}