package com.pepej.battleeyercon.mapper

import com.pepej.battleeyercon.models.Player
import com.pepej.battleeyercon.utils.addNotNull
import java.io.StringReader
import java.lang.reflect.Type
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

interface BattleEyeCommandResponseMapper<T> {

    val type: Type

    fun map(commandResponse: String): T?
}


object BattleEyePlayersCommandResponseMapper : BattleEyeCommandResponseMapper<List<Player>> {

    override fun map(commandResponse: String): List<Player> {
        val result = mutableListOf<Player>()
        StringReader(commandResponse).use {
            for (line in it.readLines().drop(3).dropLast(1)) {
                if (!line.startsWith("(") && line.isNotEmpty()) {
                    val items = line.replace("\\s{2,}".toRegex(), " ").split(" ", limit = 5)
                    val player = Player.map(items)
                    result.addNotNull(player)
                }
            }
        }
        return result
    }

    @OptIn(ExperimentalStdlibApi::class)
    override val type: Type = typeOf<List<Player>>().javaType

}