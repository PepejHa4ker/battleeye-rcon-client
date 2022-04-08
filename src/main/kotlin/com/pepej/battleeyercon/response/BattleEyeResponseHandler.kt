package com.pepej.battleeyercon.response

import com.pepej.battleeyercon.client.BattleEyeClient
import com.pepej.battleeyercon.enum.DisconnectType
import com.pepej.battleeyercon.mapper.MappableObject


interface BattleEyeResponseHandler {

    val client: BattleEyeClient

    val onConnected: () -> Unit

    val onDisconnected: (disconnectType: DisconnectType) -> Unit

    val onCommandResponseReceived: (response: BattleEyeCommandResponse) -> Unit

    val onMessageReceived: (message: String) -> Unit

    val onWrittenToLog: (message: String) -> Unit
}


data class BattleEyeCommandResponse(override val content: String, val id: Int): MappableObject