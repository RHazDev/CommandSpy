package fr.rhaz.minecraft.networkSpy

import fr.rhaz.minecraft.kotlin.bungee.*
import fr.rhaz.minecraft.kotlin.catch
import fr.rhaz.minecraft.kotlin.ex
import fr.rhaz.minecraft.kotlin.lowerCase
import net.md_5.bungee.api.plugin.Plugin

class NetworkSpy: Plugin(){

    object Config: ConfigFile("config"){
        val logging by boolean("logging")
    }

    object Profiles: ConfigFile("profiles"){
        operator fun contains(key: String) = key in config.keys
    }
    class Profile(name: String): ConfigSection(Profiles, name){
        val chat = Chat()
        inner class Chat: ConfigSection(this, "chat"){
            val enabled by boolean("enabled")
            val format by string("format")
        }
        val commands = Commands()
        inner class Commands: ConfigSection(this, "commands"){
            val enabled by boolean("enabled")
            val format by string("format")
            val type by string("type")
        }
    }

    object Data: ConfigFile("data"){
        operator fun get(name: String) = config.getString(name).let(::Profile)
        operator fun set(name: String, value: String) = config.set(name, value)
    }

    override fun onEnable() {
        update(0)
        init(Config, Profiles, Data)

        command("nspy", "nspy.use", "networkspy"){
            args -> catch<Exception>(::msg){
                fun arg(index: Int) = args.getOrNull(index)?.lowerCase
                fun help() = Exception().also{
                    msg("&5&lNetworkSpy &7v${description.version}")
                    msg("&5/nspy set <profile>")
                    msg("&5/nspy reload <config|profiles|all>")
                }
                when(arg(0)){
                    "set" -> {
                        val profile = arg(1) ?: throw help()
                        if(profile !in Profiles)
                            throw ex("&cUnknown profile: $profile")
                        Data[this.name] = profile
                        Data.save()
                        msg("&bNow using $profile profile")
                    }
                    "reload" -> when(arg(1)){
                        "all" -> {
                            Config.reload()
                            Profiles.reload()
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
                        null -> throw help()
                        else -> throw ex("&cUnknown config: ${arg(1)}")
                    }
                    else -> throw help()
                }
            }
        }
    }
}