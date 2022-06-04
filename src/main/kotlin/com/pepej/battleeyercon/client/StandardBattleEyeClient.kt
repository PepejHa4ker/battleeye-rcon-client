package com.pepej.battleeyercon.client

import com.pepej.battleeyercon.enum.BattleEyeCommand
import com.pepej.battleeyercon.enum.BattleEyePacketType
import com.pepej.battleeyercon.enum.DisconnectType
import com.pepej.battleeyercon.mapper.BattleEyeCommandResponseMapper
import com.pepej.battleeyercon.mapper.BattleEyePlayersCommandResponseMapper
import com.pepej.battleeyercon.response.BattleEyeCommandResponse
import com.pepej.battleeyercon.response.BattleEyeResponseHandler
import kotlinx.coroutines.*
import java.io.IOException
import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.DatagramChannel
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.CRC32


class StandardBattleEyeClient : BattleEyeClient {

    private val MONITOR_INTERVAL: Long = 1000
    private val TIMEOUT_DELAY: Long = 6000
    private val KEEP_ALIVE_DELAY: Long = 30000
    private val RECONNECT_DELAY: Long = 2000
    private val TIMEOUT_TRIES = 3

    private val CRC = CRC32()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var datagramChannel: DatagramChannel? = null
    private var sendBuffer: ByteBuffer? = null
    private var receiveBuffer: ByteBuffer? = null
    private lateinit var host: InetSocketAddress

    private val connected: AtomicBoolean = AtomicBoolean(false)
    private val autoReconnect: AtomicBoolean = AtomicBoolean(true)

    private var password: String = ""
    private var sequenceNumber = 0

    private var lastSent: AtomicLong? = null
    private var lastReceived: AtomicLong? = null
    private var lastSentTimeoutFirst: AtomicLong? = null
    private var lastSentTimeoutCounter: AtomicInteger? = null

    private var receiveDataJob: Job? = null
    private var monitorJob: Job? = null

    private val commandQueue: Queue<Command> = ConcurrentLinkedQueue()
    private val emptyCommandQueueOnConnect: AtomicBoolean = AtomicBoolean(true)

    private val battleEyeClientResponseHandlerList: MutableList<BattleEyeResponseHandler> = ArrayList()
    private val mappers: MutableList<BattleEyeCommandResponseMapper<*>> = mutableListOf()



    override suspend fun connect(host: InetSocketAddress, password: String) {
        if (isConnected()) {
            return
        }
        this.host = host

        connectInternal(password)
    }

    override suspend fun connect(host: String, port: Int, password: String) {
        connect(InetSocketAddress(host, port), password)
    }


    override suspend fun reconnect() {
        disconnect()
        connect(host, password)
    }

    override suspend fun disconnect() {
        if (isConnected()) {
            this.disconnectInternal(DisconnectType.Manual)
        }
    }

    private suspend fun disconnectInternal(type: DisconnectType) {
        fireWrittenToLogHandler("Disconnecting from: $host ")
        connected.set(false)

        if (monitorJob != null) {
            monitorJob?.cancel()
            monitorJob = null
        }

        if (this.receiveDataJob != null) {
            this.receiveDataJob?.cancel()
            this.receiveDataJob = null
        }

        if (this.datagramChannel != null) {
            withContext(Dispatchers.IO) {
                datagramChannel?.disconnect()
                datagramChannel?.close()
            }
            this.datagramChannel = null
        }
        this.sendBuffer = null
        this.receiveBuffer = null

        // fire ConnectionHandler.onDisconnected
        fireConnectionDisconnectedHandler(type)

        if (type === DisconnectType.ConnectionLost && autoReconnect.get()) {
            scope().launch {
                try {
                    withContext(Dispatchers.IO) {
                        sleep(RECONNECT_DELAY)
                    }
                    connect(host, password)
                } catch (e: Exception) {
                    fireWrittenToLogHandler("Error while trying to reconnect.");
                }
            }
        }
    }

    override fun isAutoReconnect(): Boolean {
        return this.autoReconnect.get()
    }

    override fun setAutoReconnect(autoReconnect: Boolean) {
        this.autoReconnect.set(autoReconnect)
    }

    override fun scope(): CoroutineScope {
        return coroutineScope
    }

    override suspend fun sendCommand(command: String): Int {
        fireWrittenToLogHandler("SendCommand: $command")
        if (!isConnected()) {
            return -1
        }
        val cmd = Command(command)

        if (commandQueue.offer(cmd)) {
            cmd.id = getNextSequenceNumber()
        } else {
            fireWrittenToLogHandler("Command queue is full.")
            return -2
        }

        if (commandQueue.size == 1) {
            createPacket(BattleEyePacketType.Command, cmd.id, command, true)
            sendPacket()
        } else {
            fireWrittenToLogHandler("Command enqueued: $cmd")
        }
        return cmd.id
    }

    private fun getNextSequenceNumber(): Int {
        sequenceNumber = if (sequenceNumber == 255) 0 else sequenceNumber + 1
        return sequenceNumber
    }

    override suspend fun sendCommand(command: BattleEyeCommand, vararg params: String): Int {
        val commandBuilder = StringBuilder(command.commandString)

        params.forEach {
            commandBuilder.append(" ")
            commandBuilder.append(it)
        }
        return sendCommand(commandBuilder.toString())

    }

    private suspend fun sendNextCommand(id: Int) {
        var command = commandQueue.poll()
        if (command == null) {
            fireWrittenToLogHandler("Command queue empty.")
            return
        }
        if (command.id != id) {
            fireWrittenToLogHandler("Invalid command id.")
        }
        if (commandQueue.isNotEmpty()) {
            command = commandQueue.peek()
            fireWrittenToLogHandler("Send enqueued command: $command")
            createPacket(BattleEyePacketType.Command, command.id, command.command, true)
            sendPacket()
        }
    }

    override fun isEmptyCommandQueueOnConnect(): Boolean {
        return emptyCommandQueueOnConnect.get()
    }

    override fun setEmptyCommandQueueOnConnect(emptyCommandQueueOnConnect: Boolean) {
        this.emptyCommandQueueOnConnect.set(emptyCommandQueueOnConnect)
    }

    override fun getAllBattleEyeClientResponseHandlers(): List<BattleEyeResponseHandler> {
        return battleEyeClientResponseHandlerList
    }

    override fun addBattleEyeClientResponseHandler(handler: BattleEyeResponseHandler): Boolean {
        return this.battleEyeClientResponseHandlerList.add(handler)
    }

    override fun removeBattleEyeClientResponseHandler(handler: BattleEyeResponseHandler): Boolean {
        return this.battleEyeClientResponseHandlerList.remove(handler)
    }

    override fun removeAllBattleEyeClientResponseHandlers() {
        battleEyeClientResponseHandlerList.clear()
    }

    override fun addCommandMapper(mapper: BattleEyeCommandResponseMapper<*>): Boolean {
        return mappers.add(mapper)
    }

    override fun removeCommandMapper(mapper: BattleEyeCommandResponseMapper<*>): Boolean {
        return mappers.remove(mapper)
    }

    override fun getAllCommandMappers(): List<BattleEyeCommandResponseMapper<*>> {
        return mappers
    }

    override fun removeAllCommandMappers() {
        return mappers.clear()
    }

    private suspend fun connectInternal(password: String) {

        if (isConnected()) {
            return
        }
        this.password = password
        withContext(Dispatchers.IO) {
            datagramChannel = DatagramChannel.open()
            sendBuffer = ByteBuffer.allocate(datagramChannel!!.getOption(StandardSocketOptions.SO_SNDBUF))
            sendBuffer?.order(ByteOrder.LITTLE_ENDIAN)

            receiveBuffer = ByteBuffer.allocate(datagramChannel!!.getOption(StandardSocketOptions.SO_RCVBUF))
            receiveBuffer?.order(ByteOrder.LITTLE_ENDIAN)

            sequenceNumber = -1
            val time = System.currentTimeMillis()

            lastSent = AtomicLong(time)
            lastReceived = AtomicLong(time)
            lastSentTimeoutCounter = AtomicInteger(0)
            lastSentTimeoutFirst = AtomicLong(time)

            if (emptyCommandQueueOnConnect.get()) {
                commandQueue.clear()
            }
            datagramChannel?.connect(host)
            startReceivingData()
            startMonitor()
            createPacket(BattleEyePacketType.Login, -1, password, false)
            sendPacket()
        }


    }

    private fun fireCommandResponseHandler(commandResponse: String, id: Int) {
        for (commandResponseHandler in battleEyeClientResponseHandlerList) {
            commandResponseHandler.onCommandResponseReceived(BattleEyeCommandResponse(commandResponse, id))
        }
    }

    private fun fireMessageHandler(message: String?) {
        if (message == null || message.isEmpty()) {
            return
        }
        for (messageHandler in battleEyeClientResponseHandlerList) {
            messageHandler.onMessageReceived(message)
        }
    }

    private fun fireConnectionConnectedHandler() {
        for (connectionConnectedHandler in battleEyeClientResponseHandlerList) {
            connectionConnectedHandler.onConnected()
        }
    }

    private fun fireConnectionDisconnectedHandler(type: DisconnectType) {
        for (connectionDisconnectedHandler in battleEyeClientResponseHandlerList) {
            connectionDisconnectedHandler.onDisconnected(type)
        }
    }

    private fun fireWrittenToLogHandler(message: String) {
        battleEyeClientResponseHandlerList.forEach { it.onWrittenToLog(message) }

    }

    private suspend fun startReceivingData() {
        coroutineScope.launch {
            fireWrittenToLogHandler("Start receive data thread.")
            var multiPacketCache: Array<String?>? =
                null // use separate cache for every sequence number possible overlap
            var multiPacketCounter = 0
            try {
                while (this.isActive) {
                    if (!readPacket() || receiveBuffer!!.remaining() < 2) {
                        fireWrittenToLogHandler("Invalid data received.")
                        continue
                    }
//                    if (this.coroutineContext.job !== receiveDataJob) {
//                        fireWrittenToLogHandler("Instance thread changed (receive data thread).")
//                        break // exit thread
//                    }
                    val packetType = receiveBuffer!!.get()
                    when (packetType.toInt()) {
                        0x00 -> {

                            // login response
                            // 0x00 | (0x01 (successfully logged in) OR 0x00
                            // (failed))
                            if (receiveBuffer!!.remaining() != 1) {
                                fireWrittenToLogHandler("Unexpected login response received.")
                                disconnectInternal(DisconnectType.ConnectionFailed)
                                return@launch  // exit thread
                            }
                            connected.set(receiveBuffer!!.get().toInt() == 0x01)
                            if (connected.get()) {
                                fireWrittenToLogHandler("Connected to $host")
                                fireConnectionConnectedHandler()
                            } else {
                                fireWrittenToLogHandler("Connection failed to $host")
                                disconnectInternal(DisconnectType.ConnectionFailed)
                                return@launch  // exit thread
                            }
                        }
                        0x01 -> {

                            // command response
                            // 0x01 | received 1-byte sequence number |
                            // (possible header and/or response (ASCII string
                            // without null-terminator) OR nothing)
                            val sn = receiveBuffer!!.get()
                            if (receiveBuffer!!.hasRemaining()) {
                                if (receiveBuffer!!.get().toInt() == 0x00) {
                                    // multi packet response
                                    fireWrittenToLogHandler("Multi packet command response received: $sn")

                                    // 0x00 | number of packets for this
                                    // response | 0-based index of the current packet
                                    val packetCount = receiveBuffer!!.get()
                                    val packetIndex = receiveBuffer!!.get()
                                    fireWrittenToLogHandler("PacketIndex/PacketCount: " + packetIndex.toInt() + "/" + packetCount.toInt())
                                    if (multiPacketCounter == 0) multiPacketCache = arrayOfNulls(packetCount.toInt())
                                    multiPacketCache!![packetIndex.toInt()] = String(
                                        receiveBuffer!!.array(),
                                        receiveBuffer!!.position(),
                                        receiveBuffer!!.remaining()
                                    )
                                    if (++multiPacketCounter == packetCount.toInt()) {
                                        // last packet received
                                        // merge packet data
                                        val sb = StringBuilder(1024 * packetCount) // estimated size
                                        for (commandResponsePart in multiPacketCache) sb.append(commandResponsePart)
                                        multiPacketCache = null
                                        multiPacketCounter = 0
                                        fireCommandResponseHandler(sb.toString(), sn.toInt())
                                        sendNextCommand(sn.toInt())
                                    }
                                } else {
                                    // single packet response
                                    fireWrittenToLogHandler("Single packet command response received: $sn")

                                    // position -1 and remaining +1 because the call to receiveBuffer.get() increments the position!
                                    val commandResponse = String(
                                        receiveBuffer!!.array(),
                                        receiveBuffer!!.position() - 1,
                                        receiveBuffer!!.remaining() + 1
                                    )
                                    fireCommandResponseHandler(commandResponse, sn.toInt())
                                    sendNextCommand(sn.toInt())
                                }
                            } else {
                                fireWrittenToLogHandler("Empty command response received: $sn")
                                sendNextCommand(sn.toInt())
                            }
                        }
                        0x02 -> {

                            // server message
                            // 0x02 | 1-byte sequence number (starting at 0) |
                            // server message (ASCII string without
                            // null-terminator)
                            val sn = receiveBuffer!!.get()
                            fireWrittenToLogHandler("Server message received: $sn")
                            val message =
                                String(receiveBuffer!!.array(), receiveBuffer!!.position(), receiveBuffer!!.remaining())
                            createPacket(BattleEyePacketType.Acknowledge, sn.toInt(), null, true)
                            sendPacket()
                            fireMessageHandler(message)
                            sendNextCommand(sn.toInt())
                        }
                        else -> {

                            // should not happen!
                            fireWrittenToLogHandler("Invalid packet type received: $packetType")
                        }
                    }
                }
            } catch (e: IOException) {
                if (e is ClosedByInterruptException) fireWrittenToLogHandler("Receive data thread interrupted.") else fireWrittenToLogHandler(
                    "Unhandled exception while receiving data. Error: $e"
                )
            }

            fireWrittenToLogHandler("Exit receive data thread.")
        }
    }

    private suspend fun startMonitor() {
        coroutineScope.launch {
            fireWrittenToLogHandler("Start monitor thread.")
            while (this.isActive) {
                withContext(Dispatchers.IO) {
                    sleep(MONITOR_INTERVAL)
                }

//                if (this.coroutineContext.job !== monitorJob) {
//                    fireWrittenToLogHandler("Instance thread changed (monitor thread).")
//                    break
//                }
                if (!connected.get() && lastSent!!.get() + TIMEOUT_DELAY < System.currentTimeMillis()) {
                    fireWrittenToLogHandler("A timeout occured so the connection to server is lost.")
                    try {
                        disconnectInternal(DisconnectType.ConnectionFailed)
                    } catch (e: java.lang.Exception) {
                        fireWrittenToLogHandler("An Error occured while disconecting.")
                    }
                    break
                }

                //Check Timeout
                if (lastSent!!.get() - lastReceived!!.get() > TIMEOUT_DELAY && (lastSentTimeoutCounter!!.get() == 0 || lastSentTimeoutCounter!!.get() > 0 && lastSentTimeoutFirst!!.get() + TIMEOUT_DELAY < System.currentTimeMillis())) {
                    fireWrittenToLogHandler("A timeout occured so the connection to server is lost.")
                    try {
                        disconnectInternal(DisconnectType.ConnectionLost)
                    } catch (e: Exception) {
                        fireWrittenToLogHandler("An Error occured while disconecting.")
                    }
                    break
                }

                //Keep Alive Pack
                if (System.currentTimeMillis() - lastSent!!.get() > KEEP_ALIVE_DELAY || lastSentTimeoutCounter!!.get() in 1 until TIMEOUT_TRIES && System.currentTimeMillis() - lastSent!!.get() >= TIMEOUT_DELAY / TIMEOUT_TRIES) {
                    fireWrittenToLogHandler("Send keep alive packet.")
                    createPacket(BattleEyePacketType.Command, getNextSequenceNumber(), null, true)
                    lastSentTimeoutCounter!!.set(lastSentTimeoutCounter!!.get() + 1)
                    try {
                        sendPacket()
                    } catch (e: Exception) {
                        fireWrittenToLogHandler("An Error occurred while trying to send packets to the server (${lastSentTimeoutCounter!!.get()}/$TIMEOUT_TRIES)")
                    }
                }
            }
            fireWrittenToLogHandler("Exit monitor thread.")
        }
    }

    private suspend fun createPacket(
        type: BattleEyePacketType,
        sequenceNumber: Int,
        command: String?,
        printSequenceNumber: Boolean
    ) {
        withContext(Dispatchers.IO) {
            sendBuffer!!.clear()
            sendBuffer!!.put('B'.code.toByte())
            sendBuffer!!.put('E'.code.toByte())
            sendBuffer!!.position(6) // skip checksum
            sendBuffer!!.put(0xFF.toByte())
            sendBuffer!!.put(type.type)
            if (printSequenceNumber) {
                sendBuffer!!.put(sequenceNumber.toByte())
            }
            if (command != null && command.isNotEmpty()) {
                val payload = command.toByteArray()
                sendBuffer!!.put(payload)
            }
            CRC.reset()
            CRC.update(sendBuffer!!.array(), 6, sendBuffer!!.position() - 6)
            val checksum = CRC.value.toInt()
            sendBuffer!!.putInt(2, checksum)
            sendBuffer!!.flip()
        }
    }

    private suspend fun sendPacket() {
        //TODO: Changed order of setting Varibales and sending Command so
        //TODO: Timeouts should now be detected better by the System
        lastSent!!.set(System.currentTimeMillis())
        if (lastSentTimeoutCounter!!.get() == 1) {
            lastSentTimeoutFirst!!.set(System.currentTimeMillis())
        }
        var write: Int
        withContext(Dispatchers.IO) {
            write = datagramChannel!!.write(sendBuffer)
        }
        fireWrittenToLogHandler("$write bytes written to the channel.")
    }

    private suspend fun readPacket(): Boolean {
        withContext(Dispatchers.IO) {
            receiveBuffer!!.clear()
            val read = datagramChannel!!.read(receiveBuffer)
            fireWrittenToLogHandler("$read bytes read from the channel.")
            if (read < 7) {
                fireWrittenToLogHandler("Invalid header size.")
                return@withContext false
            }
            receiveBuffer!!.flip()
            if (receiveBuffer!!.get() != 'B'.code.toByte() || receiveBuffer!!.get() != 'E'.code.toByte()) {
                fireWrittenToLogHandler("Invalid header.")
                return@withContext false
            }
            val checksum = receiveBuffer!!.int
            if (receiveBuffer!!.get() != 0xFF.toByte()) {
                fireWrittenToLogHandler("Invalid header.")
                return@withContext false
            }
            lastReceived!!.set(System.currentTimeMillis())
            lastSentTimeoutCounter!!.set(0)
        }
        return true

    }

    override fun isConnected(): Boolean {
        if (datagramChannel == null) {
            return false
        }
        return datagramChannel!!.isConnected && connected.get()
    }

    override fun close() {
        scope().launch {
            disconnectInternal(DisconnectType.Manual)
        }
        scope().cancel()
    }

}