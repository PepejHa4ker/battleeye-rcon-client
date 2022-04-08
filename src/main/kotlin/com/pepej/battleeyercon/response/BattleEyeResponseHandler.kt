package com.pepej.battleeyercon.response

import com.pepej.battleeyercon.enum.DisconnectType


interface BattleEyeResponseHandler {

    val onConnected: () -> Unit

    val onDisconnected: (disconnectType: DisconnectType) -> Unit

    val onCommandResponseReceived: (commandResponse: String, id: Int) -> Unit

    val onMessageReceived: (message: String) -> Unit

    val onWrittenToLog: (message: String) -> Unit
}