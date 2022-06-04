package com.pepej.battleeyercon.models

data class Player(
    val number: Int,
    val ip: String,
    val ping: String,
    val guid: String,
    val name: String,
    val status: String,
    val lastSeen: String? = null,
    val lastSeenOn: String? = null,
    val location: String? = null,
    val comment: String? = null,
) {
    companion object : MappableEntity<Player> {
        override fun map(tokens: List<String>): Player? {
            val number = tokens[0].toInt()
            val ip = tokens[1].split(":")[0]
            val ping = tokens[2]
            val guid = tokens[3].replace("(OK)", "").replace("(?)", "")
            var name = tokens[4]
            val status: String
            return if (guid.length == 32) {
                if (name.endsWith(" (Lobby)")) {
                    name = name.replace(" (Lobby)", "")
                    status = "Lobby"

                } else {
                    status = "Ingame"
                }
                Player(number, ip, ping, guid, name, status)
            } else {
                null
            }
        }
    }

}
