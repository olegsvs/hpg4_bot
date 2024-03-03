package model

import kotlinx.serialization.Serializable

@Serializable
data class Players(
    val players: List<Player>
)

@Serializable
data class Avatar(
    val image: String
)

@Serializable
data class Level(
    val current: Int,
    val experience: Experience
)

@Serializable
data class Experience(
    val total: Int,
    val current: Int,
    val maximum: Int,
)

@Serializable
data class HitPoints(
    val default: Int,
    val current: Int,
    val maximum: Int,
)

@Serializable
data class PoliceInterest(
    val current: Int,
    val maximum: Int,
)

@Serializable
data class CombatPower(
    val current: Int,
    val maximum: Int,
)

@Serializable
data class Morale(
    val current: Int,
    val maximum: Int,
)

@Serializable
data class PlayerState(
    val main: MainState,
)

@Serializable
data class MainState(
    val value: String,
) {
    val mainStateFormatted: String
        get() = when (value.lowercase()) {
        "rolling game" -> "Поиск игры"
        "map interaction" -> "Взаимодействие с картой"
        "capturing" -> "Захват сектора"
        "capture completion" -> "Завершение захвата"
        else -> value
    }
}

@Serializable
data class Family(
    val members: List<FamilyMember>,
)

@Serializable
data class Inventory(
    val slots: Slots,
)

@Serializable
data class Slots(
    val rings: List<Item>?,
    val pockets: List<Item>?,
    val wheels: List<Item>?,
    val stock: List<Item>?,
    val gloves: Item?,
    val head: Item?,
    val clothes: Item?,
    val body: Item?,
    val belt: Item?,
    val shoes: Item?,
    val chain: Item?,
    val legs: Item?,
) {
    fun getListItem(items: List<Item?>?) : String {
        return if(items.isNullOrEmpty()) "Пусто"
        else {
            var res = ""
            for(item in items) {
                if(item ==null) continue
                res +=item.name + ", "
            }
            if(res.isEmpty()) {
                "Пусто"
            } else {
                res.removeSuffix(", ")
            }
        }
    }

    fun getItem(item: Item?) : String {
        return if(item == null) "Пусто"
        else {
            item.name?:"Пусто"
        }
    }
}

@Serializable
data class Item(
    val name: String?,
)

@Serializable
data class Player(
    val id: String,
    val name: String,
    val avatar: Avatar,
    val level: Level,
    val dailyIncome: String,
    val money: String,
    val hp: HitPoints,
    val effects: List<Effect> = listOf(),
    val policeInterest: PoliceInterest,
    val combatPower: CombatPower,
    val influence: Int,
    val morale: Morale,
    val congressTokens: Int,
    val states: PlayerState,
    val characteristics: Characteristic,
    val family: Family,
    val inventory: Inventory,
) {
    val experience: String
        get() = "[${level.experience.current}/${level.experience.maximum}]"

    val positiveEffects: List<Effect>
        get() = effects.filter { it.type == "positive" }
    val negativeEffects: List<Effect>
        get() = effects.filter { it.type == "negative" }
    val otherEffects: List<Effect>
        get() = effects.filter { it.type == "neutral" }

    /*fun lose(): User {
        duelLoses++
        return this
    }

    fun win(toAdd: Int  = 1): User {
        duelWins+=toAdd
        return this
    }*/
}

@Serializable
data class Effect(
    val name: String,
    val type: String,
    val description: String,
    val duration: String,
    val source: String
) {
    val typeFormatted: String
        get() = when (type.lowercase()) {
            "positive" -> "Позитивный"
            "negative" -> "Негативный"
            "neutral" -> "Нейтральный"
            else -> type
        }

    override fun toString(): String {
        return "Тип: ${typeFormatted}\nОписание: ${description}\nДлительность: ${duration}\nИсточник: $source"
    }


}

@Serializable
data class Characteristic(
    val persistence: Persistence,
    val fortune: Fortune,
    val diplomacy: Diplomacy,
    val authority: Authority,
    val practicality: Practicality,
    val organization: Organization,
)

@Serializable
data class Persistence(
    val permanent: Int,
    val effects: Int,
    val items: Int,
    val mates: Int,
    val summary: Int,
    val actual: Int,
) {
    val name: String
        get() = "Настойчивость"
}

@Serializable
data class Fortune(
    val permanent: Int,
    val effects: Int,
    val items: Int,
    val mates: Int,
    val summary: Int,
    val actual: Int,
) {
    val name: String
        get() = "Удача"
}

@Serializable
data class Diplomacy(
    val permanent: Int,
    val effects: Int,
    val items: Int,
    val mates: Int,
    val summary: Int,
    val actual: Int,
) {
    val name: String
        get() = "Дипломатия"
}

@Serializable
data class Authority(
    val permanent: Int,
    val effects: Int,
    val items: Int,
    val mates: Int,
    val summary: Int,
    val actual: Int,
) {
    val name: String
        get() = "Авторитет"
}

@Serializable
data class Practicality(
    val permanent: Int,
    val effects: Int,
    val items: Int,
    val mates: Int,
    val summary: Int,
    val actual: Int,
){
    val name: String
        get() = "Практичность"
}

@Serializable
data class Organization(
    val permanent: Int,
    val effects: Int,
    val items: Int,
    val mates: Int,
    val summary: Int,
    val actual: Int,
){
    val name: String
        get() = "Организованность"
}

@Serializable
data class Skill(
    val name: String,
    val type: String,
    val cooldown: Int,
    val lore: String,
    val description: String,
) {
    val typeFormatted: String
        get() = when (type.lowercase()) {
            "passive" -> "Пассивный"
            "active" -> "Активный"
            else -> type
        }
    override fun toString(): String {
        return "$name\n$lore\nОписание: $description\nТип: $typeFormatted\nКд: $cooldown\n"
    }
}

@Serializable
class FamilyMember(
    val data: FamilyData
)

@Serializable
class FamilyData(
    val name: String,
    val description: String?,
    val skills:  List<Skill>? = listOf(),
    val combatPower: Int,
    val tier: Int,
    val image: String?,
) {
    val skillsString: String
    get() {
        return if(skills.isNullOrEmpty()) {
            "Нету"
        } else {
            var result = "\n"
            for(skill in skills) {
                result += skill.toString() + "\n"
            }
            return result
        }
    }
    val descriptionString: String
        get() {
            return if(description.isNullOrEmpty()) {
                ""
            } else {
                description+"\n"
            }
        }
    override fun toString(): String {
        return "${descriptionString}Тир: $tier\nБоевая мощь: $combatPower\nСкиллы: ${skillsString}"
    }
}

/*
@Serializable
data class Skills(
    val skills: List<Skill> = listOf()
) {}*/
