package com.pepej.battleeyercon

import com.pepej.battleeyercon.client.BattleEyeClient
import com.pepej.battleeyercon.enum.BattleEyeCommand
import com.pepej.battleeyercon.enum.DisconnectType
import com.pepej.battleeyercon.mapper.mapTo
import com.pepej.battleeyercon.models.Player
import com.pepej.battleeyercon.response.BattleEyeCommandResponse
import com.pepej.battleeyercon.response.BattleEyeResponseHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.Thread.sleep


fun main(): Unit = runBlocking {
    val client = BattleEyeClient.standard()
    launch { //launch an client coroutine
        client.addBattleEyeClientResponseHandler(DemoResponseHandler(client))
        client.connect("localhost", 2305, "12345") //connect to the server
        launch {
            while (true) {
                client.sendCommand(BattleEyeCommand.Bans)
                sleep(1000)
            }
        }
    }


}

class DemoResponseHandler(override val client: BattleEyeClient) : BattleEyeResponseHandler {

    override val onConnected: () -> Unit = {
        println("Connected")
    }
    override val onDisconnected: (disconnectType: DisconnectType) -> Unit = {
        println("Disconnected with $it reason")
    }

    override val onCommandResponseReceived: (BattleEyeCommandResponse) -> Unit = { r ->
        r.mapTo<List<Player>>(client)
            ?.forEach(::println)
    }
    override val onMessageReceived: (message: String) -> Unit = {
        println("Message received $it")
    }
    override val onWrittenToLog: (message: String) -> Unit = {
        println(it)
    }
}