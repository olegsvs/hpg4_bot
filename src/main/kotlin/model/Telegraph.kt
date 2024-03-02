package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class Root(
    val ok: Boolean,
    val result: Result,
)

@Serializable
data class Result(
    val path: String,
    val url: String,
    val title: String,
    val description: String,
    val views: Long,
    @SerialName("can_edit")
    val canEdit: Boolean,
)

@Serializable
data class RootPage(
    val content: List<Content>,
    @SerialName("access_token")
    val accessToken: String,
    val title: String,
    @SerialName("return_content")
    val returnContent: Boolean,
)

@Serializable
data class Content(
    val tag: String,
    val attrs: Attrs? = null,
    val children: JsonElement? = null,
)

@Serializable
data class Attrs(
    val href: String? = null,
    val src: String? = null,
    val id: String? = null,
)