package com.pepej.battleeyercon.mapper

import com.pepej.battleeyercon.models.Player
import java.io.StringReader

interface BattleEyeCommandResponseMapper<T> {

    fun map(commandResponse: String): T?
}


object BattleEyePlayersCommandResponseMapper : BattleEyeCommandResponseMapper<List<Player>> {
    override fun map(commandResponse: String): List<Player> {
        val result = mutableListOf<Player>()
        StringReader(commandResponse).use {
            for (line in it.readLines().drop(3).dropLast(1)) {
                if (!line.startsWith("(") && line.isNotEmpty()) {
                    val items = line.replace("\\s{2,}".toRegex(), " ").split(" ", limit = 5)
                     val number = items[0].toInt()
                        val ip = items[1].split(":")[0]
                        val ping = items[2]
                        val guid = items[3].replace("(OK)", "").replace("(?)", "")
                        var name = items[4]
                        var status: String
                        if (guid.length == 32) {
                            if (name.endsWith(" (Lobby)")) {
                                name = name.replace(" (Lobby)", "")
                                status = "Lobby"

                            } else {
                                status = "Ingame"
                            }
                            result.add(Player(number, ip, ping, guid, name, status))
                        } else {
                            return emptyList()
                        }

                }
            }
        }
        return result
    }

}