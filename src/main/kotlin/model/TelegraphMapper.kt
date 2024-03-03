package model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

class TelegraphMapper {
    fun mapEffectsToTelegraph(player: Player, localLastUpdated: String): List<Content> {
        val content: MutableList<Content> = mutableListOf()
        content.add(
            Content(
                tag = "pre",
                children = Json.encodeToJsonElement(listOf("Обновлено: $localLastUpdated"))
            )
        )
        //Effects
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
        return content
    }

    fun mapInventoryToTelegraph(player: Player, localLastUpdated: String): List<Content> {
        val content: MutableList<Content> = mutableListOf()
        //Inventory
        content.add(
            Content(
                tag = "pre",
                children = Json.encodeToJsonElement(listOf("Обновлено: $localLastUpdated"))
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
                children = Json.encodeToJsonElement(listOf("Колесо приколов: \n")),
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
                children = Json.encodeToJsonElement(listOf("Туловище: \n")),
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
                children = Json.encodeToJsonElement(listOf("Ремень: \n")),
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
                children = Json.encodeToJsonElement(listOf("Цепочка: \n")),
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

    fun mapTrophiesToTelegraph(trophies: Trophies, localLastUpdated: String): List<Content> {
        val content: MutableList<Content> = mutableListOf()
        //Trophies
        content.add(
            Content(
                tag = "pre",
                children = Json.encodeToJsonElement(listOf("Обновлено: $localLastUpdated"))
            )
        )
        for (trophy in trophies.trophies) {
            content.add(
                Content(
                    tag = "b",
                    children = Json.encodeToJsonElement(listOf(trophy.name + "\n")),
                )
            )
            content.add(
                Content(
                    tag = "p",
                    children = Json.encodeToJsonElement(listOf(trophy.toString()))
                )
            )
            trophy.leaderboard.list.sortedByDescending { it.trophyScore.toIntOrNull() }
                .forEachIndexed { index, leader ->
                    content.add(
                        Content(
                            tag = "p",
                            children = Json.encodeToJsonElement(listOf("${index + 1}. $leader"))
                        )
                    )
                }
        }
        return content
    }

    fun mapPlayerToTelegraph(player: Player, index: Int, bases: Bases, localLastUpdated: String): List<Content> {
        val content: MutableList<Content> = mutableListOf()
        content.add(
            Content(
                tag = "pre",
                children = Json.encodeToJsonElement(listOf("Обновлено: $localLastUpdated"))
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
                attrs = Attrs(href = "https://telegra.ph/HPG4-Player-${index + 1}-effects-03-03"),
                children = Json.encodeToJsonElement(listOf("Эффекты\n")),
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
            .first { it.sector.data.dynamicData?.controlledBy.equals(player.id) }.sector.data.dynamicData!!.structures
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
                                    attrs = Attrs(
                                        src = "https://hpg.su/assets/${
                                            member.data.image.replace(
                                                " ",
                                                "%20"
                                            )
                                        }"
                                    ),
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
}