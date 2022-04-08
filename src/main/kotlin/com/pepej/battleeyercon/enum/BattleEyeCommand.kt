package com.pepej.battleeyercon.enum

enum class BattleEyeCommand(val commandString: String) {
    /**
     * Reload server config file loaded by config option.
     */
    Init("#init"),

    /**
     * Restart mission.
     */
    Restart("#restart"),

    /**
     * Start over and reassign roles.
     */
    Reassign("#reassign"),

    /**
     * Shuts down the server.
     */
    Shutdown("#shutdown"),

    /**
     * Locks the server, prevents new clients from joining.
     */
    Lock("#lock"),

    /**
     * Unlocks the server, allows new clients to join.
     */
    Unlock("#unlock"),

    /**
     * <tt>#mission [filename]</tt>
     *
     *
     * Select mission with known name.
     *
     */
    Mission("#mission"),

    /**
     * Returns a list of the available missions on the server.
     */
    Missions("missions"),

    /**
     * Displays a list of the players on the server including BE GUIDs and
     * pings.
     */
    Players("players"),

    /**
     * <tt>Say [player#] [text]</tt>
     *
     *
     * Say something to player #. specially -1 equals all players on server
     * (e.g. 'Say -1 Hello World').
     *
     */
    Say("say"),

    /**
     * <tt>kick [player#]</tt>
     *
     *
     * Kicks a player. His # can be found in the player list using the 'players'
     * command.
     *
     */
    Kick("kick"),

    /**
     * <tt>RConPassword [password]</tt>
     *
     *
     * Changes the RCon password.
     *
     */
    RConPassword("RConPassword"),

    /**
     * <tt>MaxPing [ping]</tt>
     *
     *
     * Changes the MaxPing value. If a player has a higher ping, he will be
     * kicked from the server.
     *
     */
    MaxPing("MaxPing"),

    /**
     * Loads the scripts.txt file w/o need to restart server.
     */
    LoadScripts("loadScripts"),

    /**
     * (Re)load createvehicle.txt, remoteexec.txt and publicvariable.txt
     */
    LoadEvents("loadEvents"),

    /**
     * (Re)load the BE ban list from bans.txt.
     */
    LoadBans("loadBans"),

    /**
     * Show a list of all BE server bans.
     */
    Bans("bans"),

    /**
     * <tt>ban [player #] [time in minutes] [reason]</tt>
     *
     *
     * Ban a player's BE GUID from the server. If time is not specified or 0,
     * the ban will be permanent; if reason is not specified the player will be
     * kicked with "Banned".
     *
     */
    Ban("ban"),

    /**
     * <tt>addBan [GUID] [time in minutes] [reason]</tt>
     *
     *
     * Same as "ban", but allows to ban a player that is not currently on the
     * server.
     *
     */
    AddBan("addBan"),

    /**
     * <tt>removeBan [ban #]</tt>
     *
     *
     * Remove ban (get the ban # from the bans command).
     *
     */
    RemoveBan("removeBan"),

    /**
     * Removes expired bans from bans file.
     */
    WriteBans("writeBans");

}