import kotlinx.serialization.Serializable

@Serializable
data class WikipediaBotConfiguration(
    val sendHour: Int,
    val sendMinute: Int,
    val sendDelay: Long,
    val rssChannel: String,
    val discordBotToken: String,
    val sendChannelId: Long,
    val mentionRole: Long?
)