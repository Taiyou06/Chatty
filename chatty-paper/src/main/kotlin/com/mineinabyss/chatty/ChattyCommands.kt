package com.mineinabyss.chatty

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.chatty.components.SpyOnLocal
import com.mineinabyss.chatty.components.playerData
import com.mineinabyss.chatty.helpers.*
import com.mineinabyss.geary.papermc.access.toGeary
import com.mineinabyss.idofront.commands.arguments.stringArg
import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.commands.extensions.actions.ensureSenderIsPlayer
import com.mineinabyss.idofront.commands.extensions.actions.playerAction
import com.mineinabyss.idofront.messaging.miniMsg
import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ChattyCommands : IdofrontCommandExecutor(), TabCompleter {
    override val commands = commands(chattyPlugin) {
        "chatty"(desc = "Chatty commands") {
            ("message" / "msg")(desc = "Private message another player") {
                ensureSenderIsPlayer()
                playerAction {
                    (sender as? Player)?.handlePrivateMessage(player, arguments)
                }
            }
            "ping"(desc = "Commands related to the chat-ping feature.") {
                "toggle"(desc = "Toggle the ping sound.") {
                    ensureSenderIsPlayer()
                    action {
                        val player = sender as? Player ?: return@action
                        player.playerData.disablePingSound = !player.playerData.disablePingSound
                        player.sendFormattedMessage(chattyMessages.ping.toggledPingSound)
                    }
                }
                "sound"(desc = "Change your pingsound") {
                    val soundName by stringArg()
                    ensureSenderIsPlayer()
                    action {
                        val player = sender as? Player ?: return@action
                        if (soundName in getAlternativePingSounds) {
                            player.playerData.pingSound = soundName
                            player.sendFormattedMessage(chattyMessages.ping.changedPingSound)
                        } else {
                            player.sendFormattedMessage(chattyMessages.ping.invalidPingSound)
                        }
                    }
                }
            }
            ("channels" / "ch")(desc = "List all channels") {
                action {
                    (sender as? Player)?.sendFormattedMessage(chattyMessages.channels.availableChannels)
                        ?: sender.sendMessage(chattyMessages.channels.availableChannels)
                }
            }
            ("nickname" / "nick") {
                action {
                    val nickMessage = chattyMessages.nicknames
                    val nickConfig = chattyConfig.nicknames
                    val nick = arguments.joinToString(" ")
                    val player = sender as? Player
                    val bypassFormatPerm = player?.checkPermission(nickConfig.bypassFormatPermission) == true

                    when {
                        player is Player && !player.checkPermission(nickConfig.permission) ->
                            player.sendFormattedMessage(nickMessage.selfDenied)
                        arguments.isEmpty() -> {
                            // Removes players displayname or sends error if sender is console
                            player?.displayName(player.name.miniMsg())
                            player?.sendFormattedMessage(nickMessage.selfEmpty)
                                ?: sender.sendConsoleMessage(nickMessage.consoleNicknameSelf)
                        }
                        arguments.first().startsWith(nickConfig.nickNameOtherPrefix) -> {
                            val otherPlayer = arguments.getPlayerToNick()
                            val otherNick = nick.removePlayerToNickFromString()

                            when {
                                player?.checkPermission(nickConfig.nickOtherPermission) == false ->
                                    player.sendFormattedMessage(nickMessage.otherDenied, otherPlayer)
                                otherPlayer == null || otherPlayer !in Bukkit.getOnlinePlayers() ->
                                    player?.sendFormattedMessage(nickMessage.invalidPlayer, otherPlayer)
                                otherNick.isEmpty() -> {
                                    otherPlayer.displayName(player?.name?.miniMsg())
                                    player?.sendFormattedMessage(nickMessage.otherEmpty, otherPlayer)
                                }
                                !bypassFormatPerm && !otherNick.verifyNickStyling() ->
                                    player?.sendFormattedMessage(nickMessage.disallowedStyling)
                                !bypassFormatPerm && !otherNick.verifyNickLength() ->
                                    player?.sendFormattedMessage(nickMessage.tooLong)
                                otherNick.isNotEmpty() -> {
                                    otherPlayer.displayName(otherNick.miniMsg())
                                    player?.sendFormattedMessage(nickMessage.otherSuccess, otherPlayer)
                                }
                            }
                        }
                        else -> {
                            if (!bypassFormatPerm && !nick.verifyNickStyling()) {
                                player?.sendFormattedMessage(nickMessage.disallowedStyling)
                            } else if (!bypassFormatPerm && !nick.verifyNickLength()) {
                                player?.sendFormattedMessage(nickMessage.tooLong)
                            } else {
                                player?.displayName(nick.miniMsg())
                                player?.sendFormattedMessage(nickMessage.selfSuccess)
                            }
                        }
                    }
                }
            }
            ("reload" / "rl") {
                "config" {
                    action {
                        ChattyConfig.reload()
                        ChattyConfig.load()
                        (sender as? Player)?.sendFormattedMessage(chattyMessages.other.configReloaded)
                            ?: sender.sendConsoleMessage(chattyMessages.other.configReloaded)
                    }
                }
                "messages" {
                    action {
                        ChattyMessages.reload()
                        ChattyMessages.load()
                        (sender as? Player)?.sendFormattedMessage(chattyMessages.other.messagesReloaded)
                            ?: sender.sendConsoleMessage(chattyMessages.other.messagesReloaded)
                    }
                }

            }
            "spy" {
                playerAction {
                    val player = sender as? Player ?: return@playerAction
                    val spy = player.toGeary().has<SpyOnLocal>()
                    if (spy) {
                        player.toGeary().remove<SpyOnLocal>()
                        player.sendFormattedMessage("<gold>You are no longer spying on chat.")
                    } else {
                        player.toGeary().setPersisting(SpyOnLocal())
                        player.sendFormattedMessage("<gold>You are no longer spying on chat.")
                    }
                }
            }
            getAllChannelNames().forEach { channelName ->
                channelName {
                    ensureSenderIsPlayer()
                    action {
                        val player = sender as? Player ?: return@action
                        player.swapChannelCommand(channelName)
                    }
                }
            }
            chattyConfig.channels.forEach { (channelId, channel) ->
                channel.channelAliases.forEach { alias ->
                    alias {
                        ensureSenderIsPlayer()
                        action {
                            val player = sender as? Player ?: return@action
                            player.swapChannelCommand(channelId)
                        }
                    }
                }
            }
        }
        ("global" / "g") {
            ensureSenderIsPlayer()
            action {
                (sender as? Player)?.shortcutCommand(getGlobalChat(), arguments)
            }
        }
        ("local" / "l") {
            ensureSenderIsPlayer()
            action {
                (sender as? Player)?.shortcutCommand(getRadiusChannel(), arguments)
            }
        }
        ("admin" / "a") {
            ensureSenderIsPlayer()
            action {
                (sender as? Player)?.shortcutCommand(getAdminChannel(), arguments)
            }
        }
        ("message" / "msg")(desc = "Private message another player") {
            ensureSenderIsPlayer()
            playerAction {
                (sender as? Player)?.handlePrivateMessage(player, arguments)
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return if (command.name == "chatty") {
            when (args.size) {
                1 -> listOf("message", "ping", "reload", "channels", "nickname", "spy")
                2 -> when (args[0]) {
                    "ping" -> listOf("toggle", "sound")
                    "reload", "rl" -> listOf("config", "messages")
                    "message", "msg" -> Bukkit.getOnlinePlayers().map { it.name }.filter { s -> s.startsWith(args[1]) }
                    else -> emptyList()
                }
                3 -> when {
                    args[1] == "sound" -> getAlternativePingSounds
                    args[1].startsWith(chattyConfig.nicknames.nickNameOtherPrefix) ->
                        Bukkit.getOnlinePlayers().map { it.name }.filter { s ->
                            s.replace(chattyConfig.nicknames.nickNameOtherPrefix.toString(), "").startsWith(args[1])
                        }
                    else -> emptyList()
                }
                else -> emptyList()
            }
        } else emptyList()
    }

    private fun Player.swapChannelCommand(channelId: String) {
        val newChannel = getChannelFromId(channelId)

        if (newChannel == null) {
            sendFormattedMessage(chattyMessages.channels.noChannelWithName)
        } else if (!checkPermission(newChannel.permission)) {
            sendFormattedMessage(chattyMessages.channels.missingChannelPermission)
        } else {
            playerData.channelId = channelId
            sendFormattedMessage(chattyMessages.channels.channelChanged)
        }
    }

    private fun Player.shortcutCommand(
        channel: Map.Entry<String, ChattyConfig.ChattyChannel>?,
        arguments: List<String>
    ) {
        val currentChannel = playerData.channelId
        val msg = arguments.joinToString(" ").miniMsg()

        if (channel?.value?.permission?.isNotEmpty() == true && !checkPermission(channel.value.permission))
            sendFormattedMessage(chattyMessages.channels.missingChannelPermission)
        else if (channel?.key != null && arguments.isEmpty())
            swapChannelCommand(channel.key)
        else if (channel?.key != null && arguments.isNotEmpty()) {
            playerData.channelId = channel.key
            chattyPlugin.launch(chattyPlugin.asyncDispatcher) {
                AsyncChatEvent(
                    true, this@shortcutCommand, mutableSetOf(), ChatRenderer.defaultRenderer(), msg, msg
                ).callEvent()
                playerData.channelId = currentChannel
            }
        }
    }

    private fun Player.handlePrivateMessage(player: Player, arguments: List<String>) {
        if (!chattyConfig.privateMessages.enabled) {
            this.sendFormattedMessage(chattyMessages.privateMessages.disabled)
        } else if (arguments.first().toPlayer() == null) {
            this.sendFormattedMessage(chattyMessages.privateMessages.invalidPlayer)
        } else {
            val msg = arguments.removeFirstArgumentOfStringList()
            val privateMessages = chattyConfig.privateMessages
            if (msg.isEmpty()) return

            this.sendFormattedPrivateMessage(privateMessages.messageSendFormat, msg, player)
            player.sendFormattedPrivateMessage(privateMessages.messageReceiveFormat, msg, this)
            if (privateMessages.messageSendSound.isNotEmpty())
                this.playSound(player.location, privateMessages.messageSendSound, 1f, 1f)
            if (privateMessages.messageReceivedSound.isNotEmpty())
                player.playSound(player.location, privateMessages.messageReceivedSound, 1f, 1f)
        }
    }
}
