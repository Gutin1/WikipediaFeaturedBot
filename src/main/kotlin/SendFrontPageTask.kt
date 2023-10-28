import com.prof18.rssparser.RssParser
import com.prof18.rssparser.model.RssChannel
import com.prof18.rssparser.model.RssItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.jsoup.Jsoup
import org.slf4j.Logger
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

class SendFrontPageTask(private val logger: Logger, private val config: WikipediaBotConfiguration, private val jda: JDA) : Runnable {
    private val parser = RssParser()
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val localCalender: Calendar get() = Calendar.getInstance()
    private val pubDateFormatter: DateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")

    fun getChannel() = jda.getTextChannelById(config.sendChannelId)

    override fun run() = runBlocking {
        val discordChannel = getChannel()!! // just throw the nullpo
        val frontPage = getFrontPage()
        val embed = getEmbed(frontPage)

        logger.info("Sending message")

        sendMessage(discordChannel, embed)
    }

    private suspend fun getFrontPage(): RssChannel {
        return parser.getRssChannel(config.rssChannel)
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
            val pubDate = pubDateFormatter.parse(date)
            val calender = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
            calender.time = pubDate

            calender.get(Calendar.DAY_OF_YEAR) == localCalender.get(Calendar.DAY_OF_YEAR)
        }
    }
}