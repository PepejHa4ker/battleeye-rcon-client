# BattleEyeRConClient is a simple asynchronous kotlin library which allows you to manage your BE servers

### Code usage example:
```kotlin
import com.pepej.battleeyercon.client.BattleEyeClient
import com.pepej.battleeyercon.enum.BattleEyeCommand
import com.pepej.battleeyercon.enum.DisconnectType
import com.pepej.battleeyercon.mapper.BattleEyePlayersCommandResponseMapper
import com.pepej.battleeyercon.mapper.mapTo
import com.pepej.battleeyercon.models.Player
import com.pepej.battleeyercon.response.BattleEyeCommandResponse
import com.pepej.battleeyercon.response.BattleEyeResponseHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val scope = CoroutineScope(Dispatchers.IO)

fun main() {
    val client = BattleEyeClient.standard()
    scope.launch { //launch an client coroutine
        client.connect("localhost", 2305, "12345") //connect to the server
        client.addBattleEyeClientResponseHandler(DemoResponseHandler(client))
        client.sendCommand(BattleEyeCommand.Players)
    }

    while (true) {}

}

class DemoResponseHandler(override val client: BattleEyeClient) : BattleEyeResponseHandler {

    override val onConnected: () -> Unit = {
        println("Connected")
    }
    override val onDisconnected: (disconnectType: DisconnectType) -> Unit = {
        println("Disconnected with $it reason")
    }

    override val onCommandResponseReceived: (BattleEyeCommandResponse) -> Unit = { r ->
        r.mapTo<BattleEyePlayersCommandResponseMapper, List<Player>>(client)
            .forEach(::println)
    }
    
    override val onMessageReceived: (message: String) -> Unit = {
        println("Message received $it")
    }
    override val onWrittenToLog: (message: String) -> Unit = {
        println(it)
    }
}
```