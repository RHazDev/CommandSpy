package fr.rhaz.minecraft.networkSpy

import fr.rhaz.minecraft.kotlin.bungee.*
import fr.rhaz.minecraft.kotlin.catch
import fr.rhaz.minecraft.kotlin.ex
import fr.rhaz.minecraft.kotlin.lowerCase
import fr.rhaz.minecraft.kotlin.textOf
import fr.rhaz.minecraft.networkSpy.NetworkSpy.Profile.Module
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ClickEvent.Action.*
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.event.ChatEvent
import net.md_5.bungee.api.plugin.Plugin

class NetworkSpy: Plugin(){

    object Config: ConfigFile("config"){
        val logging by boolean("logging")
        val format by string("format")
        val bypass by string("bypass-permission")
        val receive by string("receive-permission")
    }

    object Profiles: ConfigFile("profiles"){
        operator fun contains(key: String) = key in config.keys
        operator fun contains(profile: Profile) = profile.path in config.keys
    }

    class Profile(name: String): ConfigSection(Profiles, name){
        inner class Module(name: String): ConfigSection(this, name){
            val type by string("type")
            val list by stringList("list")
        }
        val chat = Module("chat")
        val commands = Module("commands")
        val servers = Module("servers")
        val players = Module("players")
    }

    object Data: ConfigFile("data"){
        operator fun get(name: String) = config.getString(name, null)?.let(::Profile)
        operator fun set(name: String, profile: Profile) = config.set(name, profile.path)
        operator fun set(name: String, profile: String) = config.set(name, profile)
    }

    override fun onEnable(){
        update(9846)
        init(Config, Profiles, Data)
        mkcommand()

        listen<ChatEvent>{
            val player = it.sender as? ProxiedPlayer ?: return@listen
            if(player.hasPermission(Config.bypass)) return@listen

            val server = player.server.info.name

            val placeholders = placeholders(player, it.message)
            val msg = Config.format.replace(placeholders)
            if(Config.logging) logToFile(msg)

            val text = textOf(msg).apply{
                clickEvent = ClickEvent(SUGGEST_COMMAND, it.message)
            }

            val receivers = proxy.players
                .filter{it.hasPermission(Config.receive)}
                .filter{it != player}
                .map{it as CommandSender}
                .plus(proxy.console)

            for(receiver in receivers){
                val profile = Data[receiver.name] ?: continue
                if(profile !in Profiles) continue
                if(profile.players rejects player.name) continue
                if(profile.servers rejects server){
                    if(receiver !is ProxiedPlayer) continue
                    val rserver = receiver.server.info.name
                    if(rserver != server) continue
                    if(profile.servers rejects "server") continue
                }
                if(it.isCommand){
                    val command = it.message.split(" ")[0]
                    if(profile.commands rejects command) continue
                } else{
                    val words = it.message.split(" ").map{it.lowerCase}
                    if(profile.chat rejects words) continue
                }
                receiver.msg(text)
            }
        }
    }

    infix fun Module.rejects(entry: String) = when(type){
        "blacklist" -> entry.lowerCase in list.map{it.lowerCase}
        "whitelist" -> entry.lowerCase !in list.map{it.lowerCase}
        else -> throw ex("&cBad configuration")
    }

    infix fun Module.rejects(entries: List<String>) = when(type){
        "blacklist" -> list.any { it.lowerCase in entries }
        "whitelist" -> list.all { it.lowerCase !in entries }
        else -> throw ex("&cBad configuration")
    }

    fun placeholders(p: ProxiedPlayer, message: String) = mapOf(
        "%server%" to p.server.info.name,
        "%player%" to p.name,
        "%message%" to message
    )

    fun String.replace(map: Map<String, String>): String {
        var str = this
        map.forEach { k, v -> str = str.replace(k,v) }
        return str
    }

    fun mkcommand() = command("nspy", "nspy.use", "networkspy"){
        args -> catch<Exception>(::msg){
            fun arg(index: Int) = args.getOrNull(index)?.lowerCase
            fun help() = Exception().also{
                msg("&b&lNetworkSpy &7&lv${description.version}")
                val profiles = Profiles.config.keys.joinToString(" | ")
                msg("&b/nspy set <$profiles>")
                msg("&b/nspy reload <config | profiles | data | all>")
            }
            when(arg(0)){
                "set" -> {
                    val profile = arg(1) ?: throw help()
                    if(profile !in Profiles)
                        throw ex("&cUnknown profile: $profile")
                    Data[this.name] = profile
                    msg("&bNow using $profile profile")
                    if(!hasPermission(Config.receive))
                        msg("&cWarning: you don't have permission to receive spy messages")
                }
                "reload" -> when(arg(1)){
                    "all" -> {
                        Config.reload()
                        Profiles.reload()
                        Data.reload()
                        msg("&bConfigs reloaded!")
                    }
                    "profiles" -> {
                        Profiles.reload()
                        val file = Profiles.file.name
                        msg("&bConfig $file reloaded!")
                    }
                    "config" -> {
                        Config.reload()
                        val file = Config.file.name
                        msg("&bConfig $file reloaded!")
                    }
                    "data" -> {
                        Data.reload()
                        val file = Data.file.name
                        msg("&bConfig $file reloaded!")
                    }
                    null -> throw help()
                    else -> throw ex("&cUnknown config: ${arg(1)}")
                }
                else -> throw help()
            }
        }
    }
}