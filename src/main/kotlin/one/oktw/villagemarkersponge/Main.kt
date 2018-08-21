package one.oktw.villagemarkersponge

import com.google.inject.Inject
import net.minecraft.network.INetHandler
import net.minecraft.network.NetHandlerPlayServer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent
import org.slf4j.Logger
import org.spongepowered.api.Sponge
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.game.state.GameConstructionEvent
import org.spongepowered.api.event.game.state.GamePostInitializationEvent
import org.spongepowered.api.event.network.ClientConnectionEvent
import org.spongepowered.api.network.ChannelBinding
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.scheduler.Task
import org.spongepowered.api.world.World
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.*
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import kotlin.collections.HashMap

private const val UPDATE_FREQUENCY_TICKS: Long = 60
private const val POLL_CHANNEL = "KVM|Poll"
private const val ANSWER_CHANNEL = "KVM|Answer"
private const val DATA_CHANNEL = "KVM|Data"
private const val DATA_CHANNEL_COMPRESSED = "KVM|DataComp"
private const val MAX_PACKET_SIZE = 10000
private const val MAX_PACKET_SIZE_COMPRESSED = 150000

@Plugin(
        id = "villagemarkersponge",
        name = "Village Marker for Sponge",
        description = "Village Marker Plugin Sponge Port",
        version = "1.0-SNAPSHOT"
)
class Main {
    @Inject
    lateinit var logger: Logger

    lateinit var channel: ChannelBinding.RawDataChannel
    lateinit var channelCompressed: ChannelBinding.RawDataChannel
    private var players: MutableSet<Player> = Collections.newSetFromMap(WeakHashMap<Player, Boolean>())
    private var compressedPlayers: MutableSet<Player> = Collections.newSetFromMap(WeakHashMap<Player, Boolean>())

    @Suppress("UNUSED_PARAMETER")
    @Listener
    fun construct(event: GameConstructionEvent) {
        logger.info("Plugin Loaded")
        MinecraftForge.EVENT_BUS.register(this)
    }

    @Suppress("UNUSED_PARAMETER")
    @Listener
    fun postInit(event: GamePostInitializationEvent) {
        Sponge.getGame().channelRegistrar.getOrCreateRaw(this, POLL_CHANNEL)
        Sponge.getGame().channelRegistrar.getOrCreateRaw(this, ANSWER_CHANNEL)

        channel = Sponge.getGame().channelRegistrar.getOrCreateRaw(this, DATA_CHANNEL)
        channelCompressed = Sponge.getGame().channelRegistrar.getOrCreateRaw(this, DATA_CHANNEL_COMPRESSED)

        channel.addListener { _, _, _ -> }
        channelCompressed.addListener { _, _, _ -> }

        Task.builder()
                .execute { _ ->
                    update()
                }
                .intervalTicks(UPDATE_FREQUENCY_TICKS)
                .name("Village Marker - service")
                .submit(this)
    }

    // TODO: remove this if sponge issue fixed
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onForgeCustomPacketRegister(event: FMLNetworkEvent.CustomPacketRegistrationEvent<INetHandler>) {
        val channels = event.registrations

        val player = (event.handler as? NetHandlerPlayServer)?.player as? Player ?: return


        for (channel in channels) {
            if (channel == DATA_CHANNEL) {
                if (compressedPlayers.contains(player)) {
                    return
                }

                players.add(player)
            }

            if (channel == DATA_CHANNEL_COMPRESSED) {
                players.remove(player)
                compressedPlayers.add(player)
            }
        }
    }

    @Listener
    fun onDisconnect(event: ClientConnectionEvent.Disconnect) {
        val player = event.cause.first() as? Player ?: return
        players.remove(player)
        compressedPlayers.remove(player)
    }

    private fun update() {
        class MyGzipStream(out: OutputStream) : GZIPOutputStream(out) {
            fun setLevel(level: Int) {
                def.setLevel(level)
            }
        }

        val worlds = HashMap<World, List<String>>()
        val compressedWorlds = HashMap<World, List<String>>()
        val id = VillageHelper.nextId()

        for (player in players) {
            worlds.computeIfAbsent(player.world) {
                VillageHelper.getWorldStringList(id, it, MAX_PACKET_SIZE)
            }
        }

        for (player in compressedPlayers) {
            compressedWorlds.computeIfAbsent(player.world) {
                VillageHelper.getWorldStringList(id, it, MAX_PACKET_SIZE_COMPRESSED)
            }
        }

        for (player in players) {
            worlds[player.world]?.forEach { str ->
                val byteArray = str.toByteArray(Charsets.UTF_8)

                channel.sendTo(player) { buf ->
                    byteArray.forEach {
                        buf.writeByte(it)
                    }
                }
            }
        }

        for (player in compressedPlayers) {
            compressedWorlds[player.world]?.forEach { str ->
                val out = ByteArrayOutputStream()
                val gzip = MyGzipStream(out)
                gzip.setLevel(Deflater.BEST_COMPRESSION)
                gzip.write(str.toByteArray(Charsets.UTF_8))
                gzip.close()

                val result = out.toByteArray()
                out.close()

                channelCompressed.sendTo(player) { buf ->
                    result.forEach {
                        buf.writeByte(it)
                    }
                }
            }
        }
    }

// TODO: revert this change if sponge issue fixed
//    @Listener
//    fun onRegisterListenChannel(event: ChannelRegistrationEvent.Register, @First player: Player) {
//        logger.info("$player request channel ${event.channel.length} ${event.channel}")
//
//        if (event.channel == "KVM|Data") {
//            logger.info("add $player to players")
//            if (compressedPlayers.contains(player)) {
//                return
//            }
//
//            players.add(player)
//        }
//
//        if (event.channel == "KVM|DataComp") {
//            logger.info("add $player to compressedPlayers")
//            players.remove(player)
//            compressedPlayers.add(player)
//        }
//    }
}