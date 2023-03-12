package com.mineinabyss.chatty

import com.mineinabyss.chatty.listeners.ChatListener
import com.mineinabyss.chatty.listeners.ChattyProxyListener
import com.mineinabyss.chatty.listeners.DiscordListener
import com.mineinabyss.chatty.listeners.PlayerListener
import com.mineinabyss.chatty.placeholders.PlaceholderAPIHook
import com.mineinabyss.geary.addon.autoscan
import com.mineinabyss.geary.papermc.dsl.gearyAddon
import com.mineinabyss.idofront.platforms.IdofrontPlatforms
import com.mineinabyss.idofront.plugin.registerEvents
import github.scarsz.discordsrv.DiscordSRV
import org.bukkit.plugin.java.JavaPlugin

class ChattyPlugin : JavaPlugin() {

    override fun onLoad() {
        IdofrontPlatforms.load(this, "mineinabyss")
    }

    override fun onEnable() {
        saveDefaultAssets()
        saveResource("config.yml", false)
        saveResource("emotefixer.yml", false)
        saveResource("messages.yml", false)

        // Register the proxy listener
        try {
            server.messenger.registerIncomingPluginChannel(this, chattyProxyChannel, ChattyProxyListener())
            server.messenger.registerOutgoingPluginChannel(this, chattyProxyChannel)
        } catch (e: IllegalArgumentException) {
            logger.warning("Could not register proxy channel. Is another plugin using it?")
        }

        if (ChattyContext.isPlaceholderApiLoaded)
            PlaceholderAPIHook().register()

        if (ChattyContext.isDiscordSRVLoaded)
            DiscordSRV.api.subscribe(DiscordListener())

        gearyAddon {
            autoscan("com.mineinabyss") {
                all()
            }
            ChattyCommands()

            registerEvents(ChatListener(), PlayerListener())
        }
    }

    override fun onDisable() {
        if (ChattyContext.isDiscordSRVLoaded)
            DiscordSRV.api.unsubscribe(DiscordListener())
    }

    private fun saveDefaultAssets() {
        chatty.saveResource("assets/minecraft/font/chatty_heads.json", true)
        chatty.saveResource("assets/space/textures/ui/utils/null.png", true)
        chatty.saveResource("assets/space/textures/ui/utils/whiteblank_4.png", true)
    }
}
