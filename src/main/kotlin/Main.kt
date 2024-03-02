import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
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
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import model.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

val logger: Logger = LoggerFactory.getLogger("bot")
val dotenv = Dotenv.load()

val botAccessToken = dotenv.get("TWITCH_OAUTH_TOKEN").replace("'", "")
const val testDefaultRefreshRateTokensTimeMillis: Long = 300000L // 5m
const val twitchDefaultRefreshRateTokensTimeMillis: Long = 10800 * 1000 // 3h

val tgBotToken = dotenv.get("TG_BOT_TOKEN").replace("'", "")
val botOAuth2Credential = OAuth2Credential("twitch", botAccessToken)

val twitchClientId = dotenv.get("TWITCH_CLIENT_ID").replace("'", "")
val twitchClientSecret = dotenv.get("TWITCH_CLIENT_SECRET").replace("'", "")
val telegraphApikey = dotenv.get("TELEGRAPH_API_KEY").replace("'", "")
val tgAdminid = dotenv.get("TG_ADMIN_ID").replace("'", "")
val hpg4ApiUrl = dotenv.get("HPG4_API_JSON_URL").replace("'", "")
var playersExt: Players = Players(listOf())
var playersExtended: MutableList<PlayerExtended> = mutableListOf()
var trophies: Trophies = Trophies(listOf())
var bases: Bases = Bases(listOf())
var lastUpdated = ""
var trophiesUrl = ""

data class PlayerExtended(
    val player: Player,
    val telegraphUrl: String,
    val inventoryUrl: String
) {}

val twitchClient: TwitchClient = TwitchClientBuilder.builder()
    .withEnableChat(true)
    .withChatAccount(botOAuth2Credential)
    .withEnableHelix(false)
    .withEnablePubSub(false)
    .withClientId(twitchClientId)
    .withClientSecret(twitchClientSecret)
//    .withFeignLogLevel(feign.Logger.model.Level.NONE)
    .withDefaultEventHandler(SimpleEventHandler::class.java)
    .build()

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


val tgBot = bot {
    token = tgBotToken
//    logLevel = com.github.kotlintelegrambot.logging.LogLevel.All()
    dispatch {
        callbackQuery() {
            val chatId = callbackQuery.message?.chat?.id ?: return@callbackQuery
            val messageId = callbackQuery.message?.messageId ?: return@callbackQuery
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
                            text = "Эффекты",
                            url = "${player.telegraphUrl}#Эффекты",
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
                            text = "Сайт HPG",
                            url = "https://hpg.su/",
                        ),
                    ),
                )
                val message = bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = getPlayerInfo(callbackQuery.data) + "\nСообщение автоудалится через 5 минут",
                    replyMarkup = markup,
                )
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
                        " ${message.chat.firstName} ${message.chat.lastName ?: ""} ${message.chat.username ?: ""} ${message.chat.username ?: ""}"
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
                fetchData()
            }
        }
    }, 0L, testDefaultRefreshRateTokensTimeMillis)
    Timer().scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            refreshTokensTask()
        }
    }, twitchDefaultRefreshRateTokensTimeMillis, twitchDefaultRefreshRateTokensTimeMillis)
    twitchClient.chat.joinChannel("olegsvs")
    twitchClient.eventManager.onEvent(ChannelMessageEvent::class.java) { event ->
        if (event.message.equals("!hpg_info")) {
            GlobalScope.launch {
                twitchHpgInfoCommand(event)
            }
        }
        if (event.message.startsWith("!hpg_info ")) {
            GlobalScope.launch {
                if (event.message.removePrefix("!hpg_info ").trim().isEmpty()) {
                    twitchHpgInfoCommand(event)
                } else {
                    twitchHpgInfoCommand(
                        event,
                        event.message.removePrefix("!hpg_info ").replace("\uDB40\uDC00", "").trim()
                    )
                }
            }
        }
        if (event.message.startsWith("!hping")) {
            pingCommand(event)
        }
    }
}

suspend fun fetchData() {
    try {
        val response = httpClient.get(hpg4ApiUrl).bodyAsText()
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        trophies = Gson().fromJson(response, Trophies::class.java)
        playersExt = Gson().fromJson(response, Players::class.java)
        bases = Gson().fromJson(response, Bases::class.java)
        val localLastUpdated = LocalDateTime.now().format(formatter) + " Мск"
        playersExt.players.forEachIndexed { index, player ->
            val telegraphUrl = httpClient.post("https://api.telegra.ph/editPage/HPG4-Player-${index + 1}-03-02") {
                timeout {
                    requestTimeoutMillis = 60000
                }
                contentType(ContentType.Application.Json)
                setBody(
                    RootPage(
                        mapPlayerToTelegraph(player, index, localLastUpdated),
                        telegraphApikey,
                        "Инфо ${player.name}",
                        returnContent = false
                    )
                )
            }.body<Root>().result.url
            delay(1500L)

            val inventoryUrl = httpClient.post("https://api.telegra.ph/editPage/HPG4-Player-${index + 1}-inv-03-02") {
                timeout {
                    requestTimeoutMillis = 60000
                }
                contentType(ContentType.Application.Json)
                setBody(
                    RootPage(
                        mapInventoryToTelegraph(player, localLastUpdated),
                        telegraphApikey,
                        "Инвентарь ${player.name}",
                        returnContent = false
                    )
                )
            }.body<Root>().result.url
            delay(1500L)
            playersExtended.add(PlayerExtended(player, telegraphUrl, inventoryUrl))
        }
        delay(1500L)
        trophiesUrl = httpClient.post("https://api.telegra.ph/editPage/Trofei-03-02") {
            timeout {
                requestTimeoutMillis = 60000
            }
            contentType(ContentType.Application.Json)
            setBody(
                RootPage(
                    mapTrophiesToTelegraph(localLastUpdated),
                    telegraphApikey,
                    "Трофеи",
                    returnContent = false
                )
            )
        }.body<Root>().result.url
        lastUpdated = localLastUpdated
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

fun mapInventoryToTelegraph(player: Player, localLastUpdated: String): List<Content> {
    val content: MutableList<Content> = mutableListOf()
    //Inventory
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("Обновлено: ${localLastUpdated}"))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Кольца: \n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getListItem(player.inventory.slots.rings)))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Карманы: \n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getListItem(player.inventory.slots.pockets)))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Колёса: \n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getListItem(player.inventory.slots.wheels)))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Инвентарь: \n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getListItem(player.inventory.slots.stock)))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Перчатки: \n")),
        )
    )
    /* for(item in player.inventory.slots.stock!!) {
         println(item.toString())
     }*/
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getItem(player.inventory.slots.gloves)))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Голова: \n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getItem(player.inventory.slots.head)))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Тело: \n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getItem(player.inventory.slots.body)))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Одежда: \n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getItem(player.inventory.slots.clothes)))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Пояс: \n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getItem(player.inventory.slots.belt)))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Обувь: \n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getItem(player.inventory.slots.shoes)))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Цепь: \n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getItem(player.inventory.slots.chain)))
        )
    )
    content.add(
        Content(
            tag = "b",
            children = Json.encodeToJsonElement(listOf("Ноги: \n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf(player.inventory.slots.getItem(player.inventory.slots.legs)))
        )
    )
    return content
}

fun mapTrophiesToTelegraph(localLastUpdated: String): List<Content> {
    val content: MutableList<Content> = mutableListOf()
    //Trophies
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("Обновлено: ${localLastUpdated}"))
        )
    )
    for (trophie in trophies.trophies) {
        content.add(
            Content(
                tag = "b",
                children = Json.encodeToJsonElement(listOf(trophie.name + "\n")),
            )
        )
        content.add(
            Content(
                tag = "p",
                children = Json.encodeToJsonElement(listOf(trophie.toString()))
            )
        )
        trophie.leaderboard.list.sortedByDescending { it.trophyScore.toIntOrNull() }.forEachIndexed { index, leader ->
            content.add(
                Content(
                    tag = "p",
                    children = Json.encodeToJsonElement(listOf("${index + 1}. ${leader.toString()}"))
                )
            )
        }
    }
    return content
}

fun mapPlayerToTelegraph(player: Player, index: Int, localLastUpdated: String): List<Content> {
    val content: MutableList<Content> = mutableListOf()
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("Обновлено: ${localLastUpdated}"))
        )
    )
    content.add(
        Content(
            tag = "a",
            attrs = Attrs(href = "https://hpg.su"),
            children = Json.encodeToJsonElement(listOf("Сайт HPG\n")),
        )
    )
    content.add(
        Content(
            tag = "a",
            attrs = Attrs(href = "https://telegra.ph/HPG4-Player-${index + 1}-inv-03-02"),
            children = Json.encodeToJsonElement(listOf("Инвентарь\n")),
        )
    )
    content.add(
        Content(
            tag = "a",
            attrs = Attrs(href = "https://telegra.ph/Trofei-03-02"),
            children = Json.encodeToJsonElement(listOf("Трофеи\n")),
        )
    )
    content.add(
        Content(
            tag = "br",
        )
    )
    //Effects
    content.add(
        Content(
            tag = "h4",
            attrs = Attrs("Эффекты"),
            children = Json.encodeToJsonElement(
                listOf(
                    Content(
                        tag = "a",
                        attrs = Attrs(href = "#Эффекты"),
                        children = Json.encodeToJsonElement(listOf("Эффекты")),
                    )
                )
            ),
        )
    )
    for (effect in player.effects) {
        content.add(
            Content(
                tag = "b",
                children = Json.encodeToJsonElement(listOf(effect.name + "\n")),
            )
        )
        content.add(
            Content(
                tag = "p",
                children = Json.encodeToJsonElement(listOf(effect.toString()))
            )
        )
    }
    //Characteristics
    content.add(
        Content(
            tag = "h4",
            attrs = Attrs("Характеристики"),
            children = Json.encodeToJsonElement(
                listOf(
                    Content(
                        tag = "a",
                        attrs = Attrs(href = "#Характеристики"),
                        children = Json.encodeToJsonElement(listOf("Характеристики")),
                    )
                )
            ),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${player.characteristics.authority.name}: ${player.characteristics.authority.actual}\n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${player.characteristics.diplomacy.name}: ${player.characteristics.diplomacy.actual}\n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${player.characteristics.persistence.name}: ${player.characteristics.persistence.actual}\n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${player.characteristics.fortune.name}: ${player.characteristics.fortune.actual}\n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${player.characteristics.practicality.name}: ${player.characteristics.practicality.actual}\n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${player.characteristics.organization.name}: ${player.characteristics.organization.actual}\n")),
        )
    )
    //Base
    content.add(
        Content(
            tag = "h4",
            attrs = Attrs("База"),
            children = Json.encodeToJsonElement(
                listOf(
                    Content(
                        tag = "a",
                        attrs = Attrs(href = "#База"),
                        children = Json.encodeToJsonElement(listOf("База")),
                    )
                )
            ),
        )
    )
    val base = bases.map.filter { it.sector.type == "BASE" }
        .filter { it.sector.data.dynamicData?.controlledBy.equals(player.id) }
        .first().sector.data.dynamicData!!.structures
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${base.arsenal.name}, уровень: ${base.arsenal.level}\n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${base.familyClub.name}, уровень: ${base.familyClub.level}\n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${base.garage.name}, уровень: ${base.garage.level}\n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${base.stock.name}, уровень: ${base.stock.level}\n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${base.pub.name}, уровень: ${base.pub.level}\n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${base.headquarter.name}, уровень: ${base.headquarter.level}\n")),
        )
    )
    content.add(
        Content(
            tag = "p",
            children = Json.encodeToJsonElement(listOf("${base.gamblingClub.name}, уровень: ${base.gamblingClub.level}\n")),
        )
    )
    //Family
    content.add(
        Content(
            tag = "h4",
            attrs = Attrs("Семья"),
            children = Json.encodeToJsonElement(
                listOf(
                    Content(
                        tag = "a",
                        attrs = Attrs(href = "#Семья"),
                        children = Json.encodeToJsonElement(listOf("Семья")),
                    )
                )
            ),
        )
    )
    for (member in player.family.members) {
        content.add(
            Content(
                tag = "p",
                children = Json.encodeToJsonElement(
                    listOf(
                        Content(
                            tag = "strong",
                            children = Json.encodeToJsonElement(listOf(member.data.name + "\n")),
                        )
                    )
                ),
            )
        )
        if (member.data.image != null)
            content.add(
                Content(
                    tag = "p",
                    children = Json.encodeToJsonElement(
                        listOf(
                            Content(
                                tag = "img",
                                attrs = Attrs(src = "https://hpg.su/assets/${member.data.image.replace(" ", "%20")}"),
                            )
                        )
                    )
                )
            )
        content.add(
            Content(
                tag = "p",
                children = Json.encodeToJsonElement(listOf(member.data.toString()))
            )
        )
    }
    return content
}

suspend fun twitchHpgInfoCommand(event: ChannelMessageEvent, nick: String? = null) {
    try {
        logger.info("twitch, hpg_info, message: ${event.message} user: ${event.user.name}")
        if (nick != null && nick.isNotEmpty()) {
            event.reply(twitchClient.chat, getPlayerInfo(nick) + getPlayerTphUrl(nick))

        } else {
            val shortSummary = playersExt.players.map {
                "${it.name} Ур.${it.level.current}${it.experience}"
            }
            event.reply(
                twitchClient.chat,
                "\uD83D\uDD54 Обновлено: ${lastUpdated} \uD83D\uDD04 каждые 5мин " + shortSummary.toString()
                    .removeSuffix("]")
                    .removePrefix("[") + " Выберите стримера и введите команду !hpg_info nick"
            )

        }
    } catch (e: Throwable) {
        logger.error("Failed twitch hpg_info command: ", e)
    }
}

suspend fun tgHpgInfoCommand(initialMessage: Message) {
    try {
        val inlineKeyboardMarkup = InlineKeyboardMarkup.create(
            playersExt.players.subList(0, 2).map {
                InlineKeyboardButton.CallbackData(
                    text = "${it.name} Ур.${it.level.current}",
                    callbackData = it.name
                )
            },
            playersExt.players.subList(2, 4).map {
                InlineKeyboardButton.CallbackData(
                    text = "${it.name} Ур.${it.level.current}",
                    callbackData = it.name
                )
            },
            playersExt.players.subList(4, 6).map {
                InlineKeyboardButton.CallbackData(
                    text = "${it.name} Ур.${it.level.current}",
                    callbackData = it.name
                )
            },
            playersExt.players.subList(6, 8).map {
                InlineKeyboardButton.CallbackData(
                    text = "${it.name} Ур.${it.level.current}",
                    callbackData = it.name
                )
            },
            listOf(
                InlineKeyboardButton.Url(
                    text = "Трофеи",
                    url = trophiesUrl,
                ),
            ),
        )
        val shortSummary = playersExt.players.map {
            "\uD83D\uDC49 ${it.name} \uD83D\uDC40 Ур.${it.level.current}${it.experience} ℹ: ${it.states.main.mainStateFormatted}\n".replace(
                " , ",
                ""
            )
        }
        val message = tgBot.sendMessage(
            chatId = ChatId.fromId(initialMessage.chat.id),
            replyMarkup = inlineKeyboardMarkup,
            text = "\uD83D\uDD54 Обновлено: ${lastUpdated} \uD83D\uDD04 каждые 5 минут\n" + "${
                shortSummary.toString().removeSuffix("]").removePrefix("[").replace(", ", "")
            }Сообщение автоудалится через 5 минут\nВыберите стримера для получения сводки\uD83D\uDC47:"
        )
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
    } catch (e: Throwable) {
        logger.error("Failed tgHpgInfoCommand: ", e)
    }
}

suspend fun getPlayerInfo(nick: String): String {
    val player = playersExtended.firstOrNull { it.player.name.lowercase().trim().equals(nick.lowercase().trim()) }
        ?: return "Игрок под ником $nick не найден Sadge"
    return """${player.player.name} Ур.${player.player.level.current}${player.player.experience} Статус: ${player.player.states.main.mainStateFormatted}
Доход в день:💰${player.player.dailyIncome} Всего:💰${player.player.money}
Интерес полиции:👮${player.player.policeInterest.current}/${player.player.policeInterest.maximum}
Мораль:🔱${player.player.morale.current}/${player.player.morale.maximum}
Эффектов:😊${player.player.positiveEffects.size}😐${player.player.negativeEffects.size}😤${player.player.otherEffects.size}
Параметры:❤${player.player.hp.current}/${player.player.hp.maximum}💪${player.player.combatPower.current}/${player.player.combatPower.maximum}
        """.trimIndent()
}

suspend fun getPlayerTphUrl(nick: String): String {
    val player = playersExtended.firstOrNull { it.player.name.lowercase().trim().equals(nick.lowercase().trim()) }
        ?: return ""
    return " Инфо: " + player.telegraphUrl
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

private fun pingCommand(event: ChannelMessageEvent) {
//    if (!commands.isEnabled("hping")) return
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