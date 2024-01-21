@file:Suppress("UnstableApiUsage")

package com.mineinabyss.chatty

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.mineinabyss.chatty.components.*
import com.mineinabyss.chatty.helpers.*
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.idofront.commands.arguments.booleanArg
import com.mineinabyss.idofront.commands.arguments.enumArg
import com.mineinabyss.idofront.commands.arguments.optionArg
import com.mineinabyss.idofront.commands.arguments.stringArg
import com.mineinabyss.idofront.commands.execution.IdofrontCommandExecutor
import com.mineinabyss.idofront.commands.extensions.actions.ensureSenderIsPlayer
import com.mineinabyss.idofront.commands.extensions.actions.playerAction
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.events.call
import com.mineinabyss.idofront.messaging.broadcast
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.idofront.textcomponents.serialize
import io.papermc.paper.event.player.AsyncChatDecorateEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import kotlin.collections.set

class ChattyCommands : IdofrontCommandExecutor(), TabCompleter {
    override val commands = commands(chatty.plugin) {
        "chatty"(desc = "Chatty commands") {
            "reload" {
                action {
                    chatty.plugin.createChattyContext()
                    sender.sendConsoleMessage("<green>Chatty has been reloaded!")
                }
            }
            "translation" {
                "language" {
                    val language by optionArg(TranslationLanguage.entries.map { it.name })
                    playerAction {
                        val translation = player.toGearyOrNull()?.get<ChattyTranslation>() ?: ChattyTranslation()
                        player.toGearyOrNull()?.setPersisting(translation.copy(language = TranslationLanguage.valueOf(language))) ?: return@playerAction sender.error("Failed to handle translation for ${player.name}")
                        player.success("Your language has been set to ${language}")
                    }
                }
                "translateSameLanguage" {
                    val translateSameLanguage by booleanArg()
                    playerAction {
                        val translation = player.toGearyOrNull()?.get<ChattyTranslation>() ?: ChattyTranslation()
                        player.toGearyOrNull()?.setPersisting(translation.copy(translateSameLanguage = translateSameLanguage))
                            ?: return@playerAction sender.error("Failed to handle translation for ${player.name}")
                        if (translateSameLanguage) player.success("Messages with same language as yours will now be translated to ${chatty.config.translation.targetLanguage.name}")
                        else player.success("Messages with same language as yours will no longer be translated to ${chatty.config.translation.targetLanguage.name}")
                    }
                }
            }
            "ping"(desc = "Commands related to the chat-ping feature.") {
                "toggle"(desc = "Toggle the ping sound.") {
                    playerAction {
                        val gearyPlayer = player.toGeary()
                        val oldData = gearyPlayer.get<ChannelData>() ?: return@playerAction
                        gearyPlayer.setPersisting(oldData.copy(disablePingSound = !oldData.disablePingSound))
                        player.sendFormattedMessage(chatty.messages.ping.toggledPingSound)
                    }
                }
                "sound"(desc = "Change your pingsound") {
                    val soundName by stringArg()
                    playerAction {
                        val gearyPlayer = player.toGeary()
                        val oldData = gearyPlayer.get<ChannelData>() ?: return@playerAction
                        if (soundName in getAlternativePingSounds) {
                            gearyPlayer.setPersisting(oldData.copy(pingSound = soundName))
                            player.sendFormattedMessage(chatty.messages.ping.changedPingSound)
                        } else player.sendFormattedMessage(chatty.messages.ping.invalidPingSound)
                    }
                }
            }
            ("channels" / "ch")(desc = "List all channels") {
                action {
                    (sender as? Player)?.sendFormattedMessage(chatty.messages.channels.availableChannels)
                        ?: sender.sendRichMessage(chatty.messages.channels.availableChannels)
                }
            }
            ("nickname" / "nick") {
                action {
                    val nickMessage = chatty.messages.nicknames
                    val nick = arguments.toSentence()
                    val bypassFormatPerm = sender.hasPermission(ChattyPermissions.NICKNAME_OTHERS)

                    when {
                        !sender.hasPermission(ChattyPermissions.NICKNAME) ->
                            sender.sendFormattedMessage(nickMessage.selfDenied)

                        arguments.isEmpty() && sender is Player -> {
                            // Removes players displayname or sends error if sender is console
                            (sender as Player).chattyNickname = null
                            sender.sendFormattedMessage(nickMessage.selfEmpty)
                        }

                        arguments.first().startsWith(chatty.config.nicknames.nickNameOtherPrefix) -> {
                            val otherPlayer = arguments.getPlayerToNick()
                            val otherNick = nick.removePlayerToNickFromString()

                            when {
                                !sender.hasPermission(ChattyPermissions.NICKNAME_OTHERS) ->
                                    sender.sendFormattedMessage(nickMessage.otherDenied, otherPlayer)

                                otherPlayer == null || otherPlayer !in Bukkit.getOnlinePlayers() ->
                                    sender.sendFormattedMessage(nickMessage.invalidPlayer, otherPlayer)

                                otherNick.isEmpty() -> {
                                    otherPlayer.chattyNickname = null
                                    otherPlayer.sendFormattedMessage(nickMessage.selfEmpty)
                                    sender.sendFormattedMessage(nickMessage.otherEmpty, otherPlayer)
                                }

                                !bypassFormatPerm && !otherNick.verifyNickLength() ->
                                    sender.sendFormattedMessage(nickMessage.tooLong)

                                otherNick.isNotEmpty() -> {
                                    otherPlayer.chattyNickname = otherNick
                                    sender.sendFormattedMessage(nickMessage.otherSuccess, otherPlayer)
                                }
                            }
                        }

                        else  -> {
                            if (!bypassFormatPerm && !nick.verifyNickLength()) {
                                sender.sendFormattedMessage(nickMessage.tooLong)
                            } else {
                                (sender as? Player)?.chattyNickname = nick
                                sender.sendFormattedMessage(nickMessage.selfSuccess)
                            }
                        }
                    }
                }
            }
            "commandspy" {
                playerAction {
                    val gearyPlayer = player.toGeary()
                    if (gearyPlayer.has<CommandSpy>()) {
                        gearyPlayer.remove<CommandSpy>()
                        player.sendFormattedMessage(chatty.messages.spying.commandSpyOff)
                    } else {
                        gearyPlayer.getOrSetPersisting { CommandSpy() }
                        player.sendFormattedMessage(chatty.messages.spying.commandSpyOn)
                    }
                }
            }
            "spy" {
                val channelName by stringArg()
                playerAction {
                    val channel = chatty.config.channels[channelName] ?: run {
                        player.sendFormattedMessage(chatty.messages.channels.noChannelWithName)
                        return@playerAction
                    }
                    val spy = player.toGeary().getOrSetPersisting { SpyOnChannels() }

                    when {
                        channel.channelType == ChannelType.GLOBAL ->
                            player.sendFormattedMessage(chatty.messages.spying.cannotSpyOnChannel)

                        !player.hasPermission(channel.permission) ->
                            player.sendFormattedMessage(chatty.messages.spying.cannotSpyOnChannel)

                        channel.key in spy.channels -> {
                            player.sendFormattedMessage(chatty.messages.spying.stopSpyingOnChannel)
                            spy.channels.remove(channel.key)
                        }

                        else -> {
                            spy.channels.add(channel.key)
                            player.sendFormattedMessage(chatty.messages.spying.startSpyingOnChannel)
                        }
                    }
                }
            }
            chatty.config.channels.values
                .flatMap { it.channelAliases + it.key }
                .forEach { channelName ->
                    channelName {
                        playerAction {
                            swapChannel(player, chatty.config.channels[channelName])
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
            val player by stringArg()
            action {
                (sender as? Player)?.handleSendingPrivateMessage(player.toPlayer() ?: return@action, arguments, false)
            }
        }
        ("reply" / "r")(desc = "Reply to your previous private message") {
            ensureSenderIsPlayer()
            action {
                val player = sender as? Player ?: return@action
                player.toGeary().get<ChannelData>()?.lastMessager?.toPlayer()
                    ?.let { player.handleSendingPrivateMessage(it, arguments, true) }
                    ?: player.sendFormattedMessage(chatty.messages.privateMessages.emptyReply)
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        val onlinePlayers = Bukkit.getOnlinePlayers().map { it.name }
        val otherPrefix = chatty.config.nicknames.nickNameOtherPrefix
        return when (command.name) {
            "chatty" -> {
                when (args.size) {
                    1 -> listOf(
                        "translation",
                        "message",
                        "ping",
                        "reload",
                        "channels",
                        "nickname",
                        "spy",
                        "commandspy"
                    ).filter { s -> s.startsWith(args[0]) }

                    2 -> when (args[0]) {
                        "ping" -> listOf("toggle", "sound").filter { s -> s.startsWith(args[1]) }
                        "spy" ->
                            chatty.config.channels.entries.filter { s ->
                                s.key.startsWith(args[1], true) && s.value.channelType != ChannelType.GLOBAL
                            }.map { it.key }
                        "translation" -> listOf("language", "translateSameLanguage").filter { s -> s.startsWith(args[1]) }
                        else -> emptyList()
                    }

                    3 -> when {
                        args[1] == "sound" -> getAlternativePingSounds.filter { s -> s.startsWith(args[2], true) }
                        args[1].startsWith(otherPrefix) -> onlinePlayers.filter { s ->
                            s.replace(otherPrefix.toString(), "").startsWith(args[2], true)
                        }
                        args[1] == "language" -> TranslationLanguage.entries.map { it.name }.filter { s -> s.startsWith(args[2], true) }
                        args[1] == "translateSameLanguage" -> listOf("true", "false").filter { s -> s.startsWith(args[2], true) }
                        else -> emptyList()
                    }

                    else -> emptyList()
                }
            }
            "message", "msg" ->
                when (args.size) {
                    0, 1 -> onlinePlayers.filter { s -> s.startsWith(args[0], true) }.take(25)
                    else -> emptyList()
                }
            else -> emptyList()
        }
    }

    private fun Player.shortcutCommand(
        channel: Map.Entry<String, ChattyChannel>?,
        arguments: List<String>
    ) {
        val chattyData = toGeary().get<ChannelData>() ?: return
        val currentChannel = chattyData.channelId
        when {
            channel == null -> sendFormattedMessage(chatty.messages.channels.noChannelWithName)
            channel.value.permission.isNotBlank() && !hasPermission(channel.value.permission) ->
                sendFormattedMessage(chatty.messages.channels.missingChannelPermission)

            arguments.isEmpty() -> swapChannel(this, channel.value)
            else -> {
                toGeary().setPersisting(chattyData.copy(channelId = channel.key, lastChannelUsedId = channel.key))
                chatty.plugin.launch(chatty.plugin.asyncDispatcher) {
                    GenericChattyDecorateEvent(this@shortcutCommand, arguments.toSentence().miniMsg()).call {
                        this as AsyncChatDecorateEvent
                        GenericChattyChatEvent(this@shortcutCommand, this.result()).callEvent()
                    }
                    withContext(chatty.plugin.minecraftDispatcher) {
                        // chance that player logged out by now
                        toGearyOrNull()?.setPersisting(chattyData.copy(channelId = currentChannel))
                    }
                }
            }
        }
    }

    private val replyMap = mutableMapOf<Player, Job>()
    private fun handleReplyTimer(player: Player, chattyData: ChannelData): Job {
        replyMap[player]?.let { return it }
        replyMap[player]?.cancel()
        return chatty.plugin.launch {
            delay(chatty.config.privateMessages.messageReplyTime)
            replyMap[player]?.cancel()
            replyMap.remove(player)
            player.toGeary().setPersisting(chattyData.copy(lastMessager = null))
        }
    }

    private fun Player.handleSendingPrivateMessage(other: Player, arguments: List<String>, isReply: Boolean = false) {
        val chattyData = toGeary().get<ChannelData>() ?: return
        when {
            !chatty.config.privateMessages.enabled ->
                sendFormattedMessage(chatty.messages.privateMessages.disabled)

            isReply && chattyData.lastMessager == null ->
                sendFormattedMessage(chatty.messages.privateMessages.emptyReply)

            !isReply && arguments.first().toPlayer() == null ->
                sendFormattedMessage(chatty.messages.privateMessages.invalidPlayer)

            else -> {
                val msg = if (isReply) arguments.toSentence() else arguments.removeFirstArgumentOfStringList()
                if (msg.isEmpty() || this == other) return

                replyMap[other] = handleReplyTimer(other, chattyData)

                this.sendFormattedPrivateMessage(chatty.config.privateMessages.messageSendFormat, msg, other)
                other.sendFormattedPrivateMessage(chatty.config.privateMessages.messageReceiveFormat, msg, this)
                val gearyOther = other.toGeary()
                val otherChannelData = gearyOther.get<ChannelData>()
                if (otherChannelData != null) {
                    gearyOther.setPersisting(otherChannelData.copy(lastMessager = uniqueId))
                }
                if (chatty.config.privateMessages.messageSendSound.isNotEmpty())
                    this.playSound(other.location, chatty.config.privateMessages.messageSendSound, 1f, 1f)
                if (chatty.config.privateMessages.messageReceivedSound.isNotEmpty())
                    other.playSound(other.location, chatty.config.privateMessages.messageReceivedSound, 1f, 1f)
            }
        }
    }



    companion object {
        private fun CommandSender.sendFormattedMessage(message: String, optionalPlayer: Player? = null) =
            (optionalPlayer ?: this as? Player)?.let { player ->
                this.sendMessage(translatePlaceholders(player, message).miniMsg(player.buildTagResolver(true)))
            }

        private fun Player.sendFormattedPrivateMessage(messageFormat: String, message: String, receiver: Player) =
            this.sendMessage(Component.textOfChildren(
                translatePlaceholders(receiver, messageFormat).miniMsg(receiver.buildTagResolver(true)),
                message.miniMsg(receiver.buildTagResolver(true)))
            )
        private fun CommandSender.sendConsoleMessage(message: String) = this.sendMessage(message.miniMsg().parseTags(null, true))

        private fun List<String>.removeFirstArgumentOfStringList(): String =
            this.filter { it != this.first() }.toSentence()

        fun swapChannel(player: Player, newChannel: ChattyChannel?) {
            when {
                newChannel == null ->
                    player.sendFormattedMessage(chatty.messages.channels.noChannelWithName)

                newChannel.permission.isNotBlank() && !player.hasPermission(newChannel.permission) ->
                    player.sendFormattedMessage(chatty.messages.channels.missingChannelPermission)

                else -> {
                    val gearyPlayer = player.toGeary()
                    val chattyData = gearyPlayer.get<ChannelData>() ?: return
                    gearyPlayer.setPersisting(
                        chattyData.copy(
                            channelId = newChannel.key,
                            lastChannelUsedId = newChannel.key
                        )
                    )
                    player.sendFormattedMessage(chatty.messages.channels.channelChanged)
                }
            }
        }
    }
}
