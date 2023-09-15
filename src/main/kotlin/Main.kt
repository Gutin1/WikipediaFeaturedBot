import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.jsoup.Jsoup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.TimeZone
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

lateinit var main: Main

fun main(args: Array<String>) {
    val logger = LoggerFactory.getLogger("Main")
    logger.info("Starting with arguments ${args.toList()}")
    main = Main(logger)

    val config = main.configuration

    val discord: JDA = try {
        JDABuilder.createLight(config.discordBotToken)
            .disableCache(CacheFlag.values().toList())
            .setEnableShutdownHook(false)
            .build()
    } catch (exception: Exception) {
        logger.warn("Failed to start JDA", exception)
        return
    }

    val frontPageTask = FrontPage(logger, config, discord)

    if (args[0] == "now") main.timer.schedule(frontPageTask, TimeUnit.SECONDS.toMillis(2))
    main.schedule(frontPageTask, config)

    return
}

class Main(val logger: Logger) {
    val timer = Timer()
    val parser = RssParser()
    val scope = CoroutineScope(Dispatchers.Default + Job())
    val pubDateFormatter: DateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
    val localCalender: Calendar = Calendar.getInstance()

    private val configLocation: File = File(Main::class.java.protectionDomain.codeSource.location.toURI()).parentFile
    val configuration: Config = Configuration(logger).load<Config>(configLocation, "config.json")

    fun schedule(task: TimerTask, config: Config) {
        val now: ZonedDateTime = ZonedDateTime.now().withZoneSameInstant(TimeZone.getTimeZone("CST").toZoneId())
        var time: ZonedDateTime = now
            .withHour(config.sendHour)
            .withMinute(config.sendMinute)
            .withSecond(0)

        if (time.isBefore(now) || time.isEqual(now)) {
            time = now.plusDays(1).withHour(config.sendHour)
        }

        val delay = (time.toEpochSecond() * 1000L) - System.currentTimeMillis()

        time.toEpochSecond()
        timer.schedule(task, delay, config.sendDelay)
    }
}

@Serializable
data class Config(
    val sendHour: Int,
    val sendMinute: Int,
    val sendDelay: Long,
    val rssChannel: String,
    val discordBotToken: String,
    val sendChannelId: Long,
    val mentionRole: Long?
)

class FrontPage(private val logger: Logger, private val config: Config, private val jda: JDA) : TimerTask() {
    override fun run() {
        main.scope.launch {
            val discordChannel = jda.getTextChannelById(config.sendChannelId)!! // just throw the nullpo

            val frontPage = getFrontPage()
            val embed = getEmbed(frontPage)

            logger.info("Sending message")
            sendMessage(discordChannel, embed)
        }
    }

    private suspend fun getFrontPage(): RssChannel {
        return main.parser.getRssChannel(config.rssChannel)
    }

    private fun sendMessage(discordChannel: TextChannel, messageEmbed: MessageEmbed) {
        config.mentionRole?.let { discordChannel.sendMessage("<@&${it}>").queue() }
        discordChannel.sendMessageEmbeds(messageEmbed).queue()
    }

    private fun getEmbed(featuredFeed: RssChannel): MessageEmbed {
        val builder = EmbedBuilder()
        val dailyEntry = getTodaysEntry(featuredFeed)
        builder.setTitle(dailyEntry.title)

        val dailyEntryDirectLink = dailyEntry.link!!
        val dailyEntryPage = Jsoup.connect(dailyEntryDirectLink).get()

        val image = dailyEntryPage.getElementsByTag("img").first { taggedElement ->
            taggedElement.attr("class") == "mw-file-element"
        }

        val imageLink = "https://${image.attr("src").substringAfter("//")}"
        builder.setImage(imageLink)

        val paragraph = dailyEntryPage.body().select("p")
        var textBody = paragraph.text().substringBefore("(Full article...)")
        val fullArticleLink = paragraph.select("b").select("a").attr("abs:href")
        textBody += "[(Full article...)]($fullArticleLink)"

        builder.descriptionBuilder.append(textBody)
        builder.setUrl(featuredFeed.link)

        return builder.build()
    }

    private fun getTodaysEntry(feed: RssChannel): RssItem {
        return feed.items.first {
            val date = it.pubDate ?: return@first false
            val pubDate = main.pubDateFormatter.parse(date)
            val calender = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
            calender.time = pubDate

            calender.get(Calendar.DAY_OF_YEAR) == main.localCalender.get(Calendar.DAY_OF_YEAR)
        }
    }
}