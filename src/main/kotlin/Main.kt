import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.philippheuer.events4j.simple.SimpleEventHandler
import com.github.twitch4j.TwitchClient
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import com.google.gson.*
import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import model.*
import model.hpg.Bases
import model.hpg.Player
import model.hpg.Players
import model.hpg.Trophies
import model.telegraph.*
import model.twitch.CoolDown
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

val logger: Logger = LoggerFactory.getLogger("bot")
val dotenv = Dotenv.load()

val botAccessToken = dotenv.get("TWITCH_OAUTH_TOKEN").replace("'", "")
const val minuteInMillis = 60_000L
const val hourInMillis = 3_600_000L
const val infoRefreshRateTimeMinutes: Int = 20
const val infoRefreshRateTimeMillis: Long = infoRefreshRateTimeMinutes * minuteInMillis // 20m
const val mapRefreshRateTimeMillis: Long = 5 * minuteInMillis // 5m
const val twitchDefaultRefreshRateTokensTimeMillis: Long = 3 * hourInMillis // 3h
const val twitchCommandsCoolDownInMillis: Long = 25 * minuteInMillis // 25m

val tgBotToken = dotenv.get("TG_BOT_TOKEN").replace("'", "")
val botOAuth2Credential = OAuth2Credential("twitch", botAccessToken)

val twitchClientId = dotenv.get("TWITCH_CLIENT_ID").replace("'", "")
val twitchClientSecret = dotenv.get("TWITCH_CLIENT_SECRET").replace("'", "")
val telegraphApikey = dotenv.get("TELEGRAPH_API_KEY").replace("'", "")
val tgAdminid = dotenv.get("TG_ADMIN_ID").replace("'", "")
val hpg4ApiUrl = dotenv.get("HPG4_API_JSON_URL").replace("'", "")
val hpg4MapStaticImageUrl = dotenv.get("HPG4_MAP_STATIC_IMAGE_URL").replace("'", "")
val telegraphMapper = TelegraphMapper()
var playersExt: Players = Players(listOf())
var playersExtended: MutableList<PlayerExtended> = mutableListOf()
var trophies: Trophies = Trophies(listOf())
var bases: Bases = Bases(listOf())
var lastDateTimeUpdated = ""
var lastTimeUpdated = ""
var trophiesUrl = ""
var mapUrl = "https://telegra.ph/HPG4-Map-03-05"
var editMapUrl = "https://api.telegra.ph/editPage/HPG4-Map-03-05"
val coolDowns: MutableList<CoolDown> = mutableListOf()

data class PlayerExtended(
    val player: Player,
    val telegraphUrl: String,
    val inventoryUrl: String,
    val effectsUrl: String,
    val logGamesUrl: String,
    val logActionsUrl: String
)

val twitchClient: TwitchClient = TwitchClientBuilder.builder()
    .withEnableChat(true)
    .withChatAccount(botOAuth2Credential)
    .withEnableHelix(false)
    .withEnablePubSub(false)
    .withClientId(twitchClientId)
    .withClientSecret(twitchClientSecret)
//    .withFeignLogLevel(feign.Logger.Level.FULL)
    .withDefaultEventHandler(SimpleEventHandler::class.java)
    .build()

val telegraphHttpClient = HttpClient(CIO) {
    expectSuccess = true
    install(Logging) {
        level = LogLevel.ALL
    }
    install(HttpTimeout)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

val httpClient = HttpClient(CIO) {
    expectSuccess = true
    install(Logging) {
        level = LogLevel.INFO
    }
    install(HttpTimeout)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

@OptIn(DelicateCoroutinesApi::class)
val tgBot = bot {
    token = tgBotToken
//    logLevel = com.github.kotlintelegrambot.logging.LogLevel.All()
    dispatch {
        callbackQuery() {
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            logger.info("tg, callbackQuery, data: ${callbackQuery.data}")
            var nick = callbackQuery.data
            if (nick == "::trophies") {
                bot.sendMessage(ChatId.fromId(chatId), "Трофеи:\n${trophiesUrl}")
                return@callbackQuery
            } else if (nick.contains("::")) nick = nick.split("::")[0]
            val player: PlayerExtended? = getPlayer(nick)
            if (player == null) {
                bot.sendMessage(ChatId.fromId(chatId), "Игрок под ником ${callbackQuery.data} не найден =(")
            } else {
                val markup = InlineKeyboardMarkup.create(
                    listOf(
                        InlineKeyboardButton.Url(
                            text = "Инфо",
                            url = player.telegraphUrl,
                        ),
                        InlineKeyboardButton.Url(
                            text = "Характеристики",
                            url = "${player.telegraphUrl}#Характеристики",
                        ),
                    ),
                    listOf(
                        InlineKeyboardButton.Url(
                            text = "Семья",
                            url = "${player.telegraphUrl}#Семья",
                        ),
                        InlineKeyboardButton.Url(
                            text = "Инвентарь",
                            url = player.inventoryUrl,
                        ),
                    ),
                    listOf(
                        InlineKeyboardButton.Url(
                            text = "База",
                            url = "${player.telegraphUrl}#База",
                        ),
                        InlineKeyboardButton.Url(
                            text = "Эффекты",
                            url = player.effectsUrl,
                        ),
                    ),
                    listOf(
                        InlineKeyboardButton.Url(
                            text = "Лог действий",
                            url = player.logActionsUrl,
                        ),
                        InlineKeyboardButton.Url(
                            text = "Лог игр",
                            url = player.logGamesUrl,
                        ),
                    ),
                )
                val message = bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = getPlayerTgInfo(callbackQuery.data) + if (isPrivateMessage(callbackQuery.message!!)) "" else "\n❎Сообщение автоудалится через 5 минут",
                    replyMarkup = markup,
                    parseMode = ParseMode.HTML,
                    disableWebPagePreview = true
                )
                if (!isPrivateMessage(callbackQuery.message!!)) {
                    GlobalScope.launch {
                        delay(5 * 60000L)
                        try {
                            bot.deleteMessage(chatId = ChatId.fromId(chatId), message.get().messageId)
                        } catch (e: Throwable) {
                            logger.error("Failed delete callback message callbackQueru: ", e)
                        }
                    }
                }
            }
        }
        command("ping") {
            logger.info(
                "tg, ping, message: ${message.text} user: ${message.from?.firstName} ${message.from?.lastName ?: ""} ${message.from?.username ?: ""} ${message.from?.username ?: ""}" +
                        " ${message.chat.firstName} ${message.chat.lastName ?: ""} ${message.chat.username ?: ""} ${message.chat.username ?: ""}"
            )
            val result = bot.sendMessage(chatId = ChatId.fromId(message.chat.id), text = "Pong!")
            result.fold({
                GlobalScope.launch {
                    delay(60000L)
                    try {
                        bot.deleteMessage(chatId = ChatId.fromId(message.chat.id), it.messageId)
                    } catch (e: Throwable) {
                        logger.error("Failed delete ping message callbackQueru: ", e)
                    }
                    try {
                        bot.deleteMessage(chatId = ChatId.fromId(message.chat.id), message.messageId)
                    } catch (e: Throwable) {
                        logger.error("Failed delete ping initial message callbackQueru: ", e)
                    }
                }
                logger.info("On ping command")
            }, {
                logger.info("On ping command, error: $it")
            })
        }
        command("start") {
            logger.info(
                "tg, start, message: ${message.text} user: ${message.from?.firstName} ${message.from?.lastName ?: ""} ${message.from?.username ?: ""} ${message.from?.username ?: ""}" +
                        " ${message.chat.firstName} ${message.chat.lastName ?: ""} ${message.chat.username ?: ""} ${message.chat.username ?: ""}"
            )
            val result = bot.sendMessage(
                chatId = ChatId.fromId(message.chat.id),
                text = "Бот для получение текущей ситуации в мире HPG4! /hpg_info"
            )
            result.fold({
                logger.info("On start command")
            }, {
                logger.info("On start command, error: $it")
            })
        }
        command("hpg_info") {
            logger.info(
                "tg, hpg_info, message: ${message.text} user: ${message.from?.firstName} ${message.from?.lastName ?: ""} ${message.from?.username ?: ""} ${message.from?.username ?: ""}" +
                        " ${message.chat.firstName} ${message.chat.lastName ?: ""} ${message.chat.username ?: ""} ${message.chat.username ?: ""}, chatId:  ${message.chat.id}"
            )
            GlobalScope.launch { tgHpgInfoCommand(message) }
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun main(args: Array<String>) {
    logger.info("Bot started")
    tgBot.startPolling()

    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            runBlocking {
                refreshMapTask()
            }
        }
    }, 0L, mapRefreshRateTimeMillis)
    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            runBlocking {
                fetchData()
            }
        }
    }, 0L, infoRefreshRateTimeMillis)
    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            refreshTokensTask()
        }
    }, twitchDefaultRefreshRateTokensTimeMillis, twitchDefaultRefreshRateTokensTimeMillis)
    twitchClient.chat.joinChannel("olegsvs")
    twitchClient.eventManager.onEvent(ChannelMessageEvent::class.java) { event ->
        if (event.message.equals("!hpg_info")) {
            GlobalScope.launch {
                twitchHpgInfoCommand(event, "!hpg_info")
            }
        }
        if (event.message.startsWith("!hpg_info ")) {
            GlobalScope.launch {
                if (event.message.removePrefix("!hpg_info ").trim().isEmpty()) {
                    twitchHpgInfoCommand(event, "!hpg_info")
                } else {
                    val nick = event.message.removePrefix("!hpg_info ").replace("\uDB40\uDC00", "").replace("@", "").trim()
                    twitchHpgInfoCommand(
                        event,
                        commandText = "!hpg_info$nick",
                        nick
                    )
                }
            }
        }
        if (event.message.equals("!hpg_games")) {
            GlobalScope.launch {
                twitchHpgGamesCommand(event, "!hpg_games")
            }
        }
        if (event.message.startsWith("!hping")) {
            pingCommand(event)
        }
        /*if (event.message.startsWith("!hpg_whisper")) {
            whisperCommand(event)
        }*/
    }
}

suspend fun fetchData() {
    try {
        val response = httpClient.get(hpg4ApiUrl).bodyAsText()
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        val timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm")
        trophies = Gson().fromJson(response, Trophies::class.java)
        playersExt = Gson().fromJson(response, Players::class.java)
        bases = Gson().fromJson(response, Bases::class.java)
        val localLastUpdated = LocalDateTime.now().format(formatter) + " GMT+3"
        val localLastTimeUpdated = LocalDateTime.now().format(timeOnlyFormatter)
        playersExt.players.forEachIndexed { index, player ->
            val telegraphUrl =
                telegraphHttpClient.post("https://api.telegra.ph/editPage/HPG4-Player-${index + 1}-03-02") {
                    timeout {
                        requestTimeoutMillis = 60000
                    }
                    contentType(ContentType.Application.Json)
                    setBody(
                        RootPage(
                            telegraphMapper.mapPlayerToTelegraph(player, index, bases, mapUrl, localLastUpdated),
                            telegraphApikey,
                            "Инфо ${player.name}",
                            returnContent = false
                        )
                    )
                }.body<Root>().result.url
            delay(2000L)
            val inventoryUrl =
                telegraphHttpClient.post("https://api.telegra.ph/editPage/HPG4-Player-${index + 1}-inv-03-02") {
                    timeout {
                        requestTimeoutMillis = 60000
                    }
                    contentType(ContentType.Application.Json)
                    setBody(
                        RootPage(
                            telegraphMapper.mapInventoryToTelegraph(player, localLastUpdated),
                            telegraphApikey,
                            "Инвентарь ${player.name}",
                            returnContent = false
                        )
                    )
                }.body<Root>().result.url
            delay(2000L)
            val effectsUrl =
                telegraphHttpClient.post("https://api.telegra.ph/editPage/HPG4-Player-${index + 1}-effects-03-03") {
                    timeout {
                        requestTimeoutMillis = 60000
                    }
                    contentType(ContentType.Application.Json)
                    setBody(
                        RootPage(
                            telegraphMapper.mapEffectsToTelegraph(player, localLastUpdated),
                            telegraphApikey,
                            "Эффекты ${player.name}",
                            returnContent = false
                        )
                    )
                }.body<Root>().result.url
            delay(2000L)
            val logGamesUrl =
                telegraphHttpClient.post("https://api.telegra.ph/editPage/HPG4-Player-${index + 1}-log-games-03-03") {
                    timeout {
                        requestTimeoutMillis = 60000
                    }
                    contentType(ContentType.Application.Json)
                    setBody(
                        RootPage(
                            telegraphMapper.mapGameLogsToTelegraph(player, localLastUpdated),
                            telegraphApikey,
                            "Лог игр ${player.name}",
                            returnContent = false
                        )
                    )
                }.body<Root>().result.url
            delay(2000L)
            val logActionsUrl =
                telegraphHttpClient.post("https://api.telegra.ph/editPage/HPG4-Player-${index + 1}-log-actions-03-03") {
                    timeout {
                        requestTimeoutMillis = 60000
                    }
                    contentType(ContentType.Application.Json)
                    setBody(
                        RootPage(
                            telegraphMapper.mapActionLogsToTelegraph(player, localLastUpdated),
                            telegraphApikey,
                            "Лог действий ${player.name}",
                            returnContent = false
                        )
                    )
                }.body<Root>().result.url
            delay(2000L)
            playersExtended.add(
                PlayerExtended(
                    player,
                    telegraphUrl,
                    inventoryUrl,
                    effectsUrl,
                    logGamesUrl,
                    logActionsUrl
                )
            )
        }
        delay(2000L)
        trophiesUrl = telegraphHttpClient.post("https://api.telegra.ph/editPage/Trofei-03-02") {
            timeout {
                requestTimeoutMillis = 60000
            }
            contentType(ContentType.Application.Json)
            setBody(
                RootPage(
                    telegraphMapper.mapTrophiesToTelegraph(trophies, localLastUpdated),
                    telegraphApikey,
                    "Трофеи",
                    returnContent = false
                )
            )
        }.body<Root>().result.url
        try {
            delay(2000L)
            val mapUpdateTime = File("map_update_time.txt").readText()
            val mapImageUrl = telegraphHttpClient.submitFormWithBinaryData("https://telegra.ph/upload",
                formData = formData {
                    append("description", "hpg4_map")
                    append("image", File("hpg4_map.png").readBytes(), Headers.build {
                        append(HttpHeaders.ContentType, "image/png")
                        append(HttpHeaders.ContentDisposition, "filename=\"hpg4_map.png\"")
                    })
                }) {
                timeout {
                    requestTimeoutMillis = 60000
                }
            }.body<UploadResponse>().first().src
            delay(2000L)
            telegraphHttpClient.post(editMapUrl) {
                timeout {
                    requestTimeoutMillis = 60000
                }
                contentType(ContentType.Application.Json)
                setBody(
                    RootPage(
                        telegraphMapper.mapHpgMapImageToTelegraph(mapImageUrl, mapUpdateTime),
                        telegraphApikey,
                        "Карта",
                        returnContent = false
                    )
                )
            }.body<Root>()
        } catch (e: Throwable) {
            logger.error("Failed edit map page: ", e)
        }
        lastDateTimeUpdated = localLastUpdated
        lastTimeUpdated = localLastTimeUpdated
    } catch (e: Throwable) {
        try {
            tgBot.sendMessage(
                chatId = ChatId.fromId(tgAdminid.toLong()),
                text = e.toString()
            )
        } catch (e: Throwable) {
            logger.error("Failed fetchData: ", e)
        }
        logger.error("Failed fetchData: ", e)
    }

}

fun twitchHpgInfoCommand(event: ChannelMessageEvent, commandText: String, nick: String? = null) {
    try {
        logger.info("twitch, hpg_info, message: ${event.message} user: ${event.user.name}")
        logger.info(coolDowns.toString())
        val cd = coolDowns.firstOrNull { it.channelName == event.channel!!.name && it.commandText == commandText }
        if (cd != null) {
            val now = System.currentTimeMillis() / 1000
            val cdInSeconds = (cd.coolDownMillis / 1000)
            val diff = (now - cd.lastUsageInMillis / 1000)
            if (diff < cdInSeconds) {
                val nextRollTime = (cdInSeconds - diff)
                val nextRollMinutes = (nextRollTime % 3600) / 60
                val nextRollSeconds = (nextRollTime % 3600) % 60
                event.reply(
                    twitchClient.chat,
                    "КД \uD83D\uDD5B ${nextRollMinutes}м${nextRollSeconds}с"
                )
                return
            } else {
                coolDowns.remove(cd)
            }
        }
        coolDowns.add(
            CoolDown(
                channelName = event.channel!!.name,
                commandText = commandText,
                coolDownMillis = twitchCommandsCoolDownInMillis,
                lastUsageInMillis = System.currentTimeMillis()
            )
        )
        if (!nick.isNullOrEmpty()) {
            val infoMessage = "Upd.$lastTimeUpdated ${getPlayerTwitchInfo(nick)}${getPlayerTphUrl(nick)}"
            infoMessage.chunked(499).map {
                event.reply(twitchClient.chat, it)
            }
        } else {
            val shortSummary = playersExt.players.map {
                "@${it.name} \uD83D\uDC40 Ходы ${it.actionPoints.turns.daily}"
            }
            val infoMessage = "Upd.$lastTimeUpdated " + shortSummary.toString()
                .removeSuffix("]")
                .removePrefix("[") + " Подробнее !hpg_info nick Текущие игры !hpg_games"
            infoMessage.chunked(499).map {
                event.reply(twitchClient.chat, it)
            }
        }

    } catch (e: Throwable) {
        logger.error("Failed twitch hpg_info command: ", e)
    }
}

fun twitchHpgGamesCommand(event: ChannelMessageEvent, commandText: String) {
    try {
        logger.info("twitch, hpg_games, message: ${event.message} user: ${event.user.name}")
        logger.info(coolDowns.toString())
        val cd = coolDowns.firstOrNull { it.channelName == event.channel!!.name && it.commandText == commandText }
        if (cd != null) {
            val now = System.currentTimeMillis() / 1000
            val cdInSeconds = (cd.coolDownMillis / 1000)
            val diff = (now - cd.lastUsageInMillis / 1000)
            if (diff < cdInSeconds) {
                val nextRollTime = (cdInSeconds - diff)
                val nextRollMinutes = (nextRollTime % 3600) / 60
                val nextRollSeconds = (nextRollTime % 3600) % 60
                event.reply(
                    twitchClient.chat,
                    "КД \uD83D\uDD5B ${nextRollMinutes}м${nextRollSeconds}с"
                )
                return
            } else {
                coolDowns.remove(cd)
            }
        }
        coolDowns.add(
            CoolDown(
                channelName = event.channel!!.name,
                commandText = commandText,
                coolDownMillis = twitchCommandsCoolDownInMillis,
                lastUsageInMillis = System.currentTimeMillis()
            )
        )
        val shortSummary = playersExt.players.map {
            "@${it.name} \uD83C\uDFAE${it.currentGameTwitch}"
        }
        val infoMessage = "Upd.$lastTimeUpdated " + shortSummary.toString()
            .removeSuffix("]")
            .removePrefix("[") + " Подробнее !hpg_info nick"
        infoMessage.chunked(499).map {
            event.reply(twitchClient.chat, it)
        }
    } catch (e: Throwable) {
        logger.error("Failed twitch hpg_info command: ", e)
    }
}

@OptIn(DelicateCoroutinesApi::class)
suspend fun tgHpgInfoCommand(initialMessage: Message) {
    try {
        val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
            playersExt.players.subList(0, 2).map {
                InlineKeyboardButton.CallbackData(
                    text = it.name,
                    callbackData = it.name
                )
            },
            playersExt.players.subList(2, 4).map {
                InlineKeyboardButton.CallbackData(
                    text = it.name,
                    callbackData = it.name
                )
            },
            playersExt.players.subList(4, 6).map {
                InlineKeyboardButton.CallbackData(
                    text = it.name,
                    callbackData = it.name
                )
            },
            playersExt.players.subList(6, 8).map {
                InlineKeyboardButton.CallbackData(
                    text = it.name,
                    callbackData = it.name
                )
            },
            listOf(
                InlineKeyboardButton.Url(
                    text = "Трофеи",
                    url = trophiesUrl,
                ),
                InlineKeyboardButton.Url(
                    text = "Карта",
                    url = mapUrl,
                ),
                InlineKeyboardButton.Url(
                    text = "Сайт HPG",
                    url = "https://hpg.su/",
                ),
            ),
        )
        val shortSummary = playersExt.players.map {
            ("\uD83D\uDC49 <a href=\"https://www.twitch.tv/${it.name}\"><b>${it.name} \uD83D\uDC7E</b></a> \uD83D\uDC40 Ур. <b>" +
                    "${it.level.current}${it.experience}</b> \uD83E\uDEF1 Ходы день <b>${it.actionPoints.turns.daily.current}/" +
                    "${it.actionPoints.turns.daily.maximum}</b>\n\uD83C\uDFAEИгра ${it.currentGameTg}\n").replace(
                " , ", ""
            )
        }
        val message = tgBot.sendMessage(
            chatId = ChatId.fromId(initialMessage.chat.id),
            replyMarkup = inlineKeyboardMarkup,
            disableWebPagePreview = true,
            parseMode = ParseMode.HTML,
            text = "⏰Обновлено <b>${lastDateTimeUpdated}</b> ⏳каждые <b>${infoRefreshRateTimeMinutes}</b> минут\n" + "${
                shortSummary.toString().removeSuffix("]").removePrefix("[").replace(", ", "")
            }${if (isPrivateMessage(initialMessage)) "" else "❎Сообщение автоудалится через <b>5</b> минут\n"}✅Выберите стримера для получения сводки\uD83D\uDC47"
        )
        if (!isPrivateMessage(initialMessage)) {
            GlobalScope.launch {
                delay(5 * 60000L)
                try {
                    tgBot.deleteMessage(chatId = ChatId.fromId(initialMessage.chat.id), message.get().messageId)
                } catch (e: Throwable) {
                    logger.error("Failed delete message tgHpgInfoCommand", e)
                }
                try {
                    tgBot.deleteMessage(chatId = ChatId.fromId(initialMessage.chat.id), initialMessage.messageId)
                } catch (e: Throwable) {
                    logger.error("Failed delete initialMessage tgHpgInfoCommand", e)
                }
            }
        }
    } catch (e: Throwable) {
        logger.error("Failed tgHpgInfoCommand: ", e)
    }
}

private fun isPrivateMessage(message: Message): Boolean {
    return !message.chat.id.toString().startsWith("-100")
}

fun getPlayerTgInfo(nick: String): String {
    val playerExt = playersExtended.firstOrNull { it.player.name.lowercase().trim().equals(nick.lowercase().trim()) }
        ?: return "Игрок под ником <b>$nick</b> не найден Sadge"
    return """👉<a href="https://www.twitch.tv/${playerExt.player.name}"><b>${playerExt.player.name} 👾</b></a> Уровень <b>${playerExt.player.level.current}${playerExt.player.experience}</b>
🎮Текущая игра ${playerExt.player.currentGameTg}
⭐Ходы день <b>${playerExt.player.actionPoints.turns.daily.current}/${playerExt.player.actionPoints.turns.daily.maximum}</b>, неделя <b>${playerExt.player.actionPoints.turns.weekly.current}/${playerExt.player.actionPoints.turns.weekly.maximum}</b>
⭐Очки движения <b>${playerExt.player.actionPoints.movement.current}/${playerExt.player.actionPoints.movement.maximum}</b>
⭐Очки разведки <b>${playerExt.player.actionPoints.exploring.current}/${playerExt.player.actionPoints.exploring.maximum}</b>
💰Доход в день <b>${DecimalFormat("# ##0.00").format(playerExt.player.dailyIncome)}</b> На руках💰<b>${
        DecimalFormat("# ##0.00").format(
            playerExt.player.money
        )
    }</b>
🗣Жетоны съезда <b>${playerExt.player.congressTokens}</b>
👮Интерес полиции <b>${playerExt.player.policeInterest.current}/${playerExt.player.policeInterest.maximum}</b>
🔱Мораль семьи <b>${playerExt.player.morale.current}/${playerExt.player.morale.maximum}</b>
❔Эффектов 😊<b>${playerExt.player.positiveEffects.size}</b>😐<b>${playerExt.player.negativeEffects.size}</b>😤<b>${playerExt.player.otherEffects.size}</b>
❤HP <b>${playerExt.player.hp.current}/${playerExt.player.hp.maximum}</b>
💪Боевая мощь <b>${playerExt.player.combatPower.current}/${playerExt.player.combatPower.maximum}</b>
        """.trimIndent()
}

fun getPlayerTwitchInfo(nick: String): String {
    val playerExt = playersExtended.firstOrNull { it.player.name.lowercase().trim().equals(nick.lowercase().trim()) }
        ?: return "Игрок под ником $nick не найден Sadge"
    return """👉@${playerExt.player.name} Ур.${playerExt.player.level.current}${playerExt.player.experience}
🎮${playerExt.player.currentGameTwitch}
⭐${playerExt.player.actionPoints.turns} ${playerExt.player.actionPoints.movement.toTwitchString()} ${playerExt.player.actionPoints.exploring.toTwitchString()}
Доход ${DecimalFormat("# ##0").format(playerExt.player.dailyIncome)}
На руках ${DecimalFormat("# ##0").format(playerExt.player.money)}
Жетоны съезда ${playerExt.player.congressTokens}
Интерес полиции ${playerExt.player.policeInterest.current}/${playerExt.player.policeInterest.maximum}
Мораль семьи ${playerExt.player.morale.current}/${playerExt.player.morale.maximum}
Эффектов 😊${playerExt.player.positiveEffects.size}😐${playerExt.player.negativeEffects.size}😤${playerExt.player.otherEffects.size}
HP ${playerExt.player.hp.current}/${playerExt.player.hp.maximum}
Боевая мощь ${playerExt.player.combatPower.current}/${playerExt.player.combatPower.maximum}
        """.trimIndent()
}

fun getPlayerTphUrl(nick: String): String {
    val player = playersExtended.firstOrNull { it.player.name.lowercase().trim().equals(nick.lowercase().trim()) }
        ?: return ""
    return " Инфо ${player.telegraphUrl}"
}

fun getPlayer(nick: String): PlayerExtended? {
    return playersExtended.firstOrNull { it.player.name.lowercase().trim().equals(nick.lowercase().trim()) }
}

fun refreshTokensTask() {
    logger.info("refreshTokensTask start")
    val processBuilder = ProcessBuilder()
    processBuilder.command("bash", "-c", "cd /home/bot/hpg4_bot/ && . jrestart.sh")
    try {
        processBuilder.start()
        logger.info("refreshTokensTask process called")
    } catch (e: Throwable) {
        logger.error("Failed call restart script:", e)
    }
}

fun refreshMapTask() {
    logger.info("refreshMapTask start")
    val processBuilder = ProcessBuilder()
    processBuilder.command("bash", "-c", "cd /home/bot/hpg4_bot/ && . refresh_map.sh")
    try {
        processBuilder.start()
        logger.info("refreshMapTask process called")
    } catch (e: Throwable) {
        logger.error("Failed call refresh map script:", e)
    }
}

private fun pingCommand(event: ChannelMessageEvent) {
    logger.info("pingCommand")
    try {
        logger.info("twitch, ping, message: ${event.message} user: ${event.user.name}")
        event.reply(
            twitchClient.chat,
            "Starege pong"
        )
    } catch (e: Throwable) {
        logger.error("Failed pingCommand: ", e)
    }
}

private fun whisperCommand(event: ChannelMessageEvent) {
    logger.info("whisperCommand")
    try {
        logger.info("twitch, whisper, message: ${event.message} user: ${event.user.name}")
        twitchClient.helix.sendWhisper(
            botOAuth2Credential.accessToken,
            "1045167616",
            event.user.id,
            "Test"
        ).execute()
    } catch (e: Throwable) {
        logger.error("Failed whisper: ", e)
    }
}