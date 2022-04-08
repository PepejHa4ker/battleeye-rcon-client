package com.pepej.battleeyercon.client

import com.pepej.battleeyercon.enum.BattleEyeCommand
import com.pepej.battleeyercon.response.BattleEyeResponseHandler
import java.net.InetSocketAddress

interface BattleEyeClient {

    suspend fun connect(host: InetSocketAddress, password: String)

    suspend fun connect(host: String, port: Int, password: String)

    suspend fun reconnect()

    suspend fun disconnect()

    fun isAutoReconnect(): Boolean

    fun setAutoReconnect(autoReconnect: Boolean)

    suspend fun sendCommand(command: String): Int

    suspend fun sendCommand(command: BattleEyeCommand, vararg params: String): Int

    fun isEmptyCommandQueueOnConnect(): Boolean

    fun setEmptyCommandQueueOnConnect(emptyCommandQueueOnConnect: Boolean)

    // handlers

    fun getAllBattleEyeClientResponseHandlers(): List<BattleEyeResponseHandler>

    fun addBattleEyeClientResponseHandler(handler: BattleEyeResponseHandler): Boolean

    fun removeBattleEyeClientResponseHandler(handler: BattleEyeResponseHandler): Boolean

    fun removeAllBattleEyeClientResponseHandlers()

    fun isConnected(): Boolean

    companion object {
        fun standard(): BattleEyeClient {
            return StandardBattleEyeClient()
        }
    }
}

data class Command(val command: String, var id: Int = -1)