#BattleEyeRConClient is a simple asynchronous kotlin library which allows you to manage your BE servers

###Code usage example:
```kotlin
import com.pepej.battleeyercon.client.BattleEyeClient
import com.pepej.battleeyercon.enum.BattleEyeCommand
import com.pepej.battleeyercon.enum.DisconnectType
import com.pepej.battleeyercon.response.BattleEyeResponseHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val scope = CoroutineScope(Dispatchers.IO)

fun main() {
    val client = BattleEyeClient.standard()
    scope.launch { //launch an client coroutine
        client.connect("localhost", 2305, "12345") //connect to the server
        client.addBattleEyeClientResponseHandler(DemoResponseHandler)
        client.sendCommand(BattleEyeCommand.Players)
    }

    while (true) {}
}

object DemoResponseHandler : BattleEyeResponseHandler {
    override val onConnected: () -> Unit = {
        println("Connected")
    }
    override val onDisconnected: (disconnectType: DisconnectType) -> Unit = {
        println("Disconnected with $it reason")
    }

    override val onCommandResponseReceived: (commandResponse: String, id: Int) -> Unit = { command, id ->
        println("Response received: $command with id: $id")
    }
    override val onMessageReceived: (message: String) -> Unit = {
        println("Message received $it")
    }
    override val onWrittenToLog: (message: String) -> Unit = {
        println(it)
    }
}
```