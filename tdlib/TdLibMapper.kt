package com.hemax.tdlib

import com.hemax.models.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.drinkless.tdlib.TdApi

object TdLibMapper {

    fun toDomainUser(tdUser: TdApi.User): User = User(
        id = tdUser.id,
        phoneNumber = tdUser.phoneNumber,
        firstName = tdUser.firstName,
        lastName = tdUser.lastName,
        username = tdUser.username,
        photoUrl = tdUser.profilePhoto?.small?.local?.path,
        bio = tdUser.bio,
        isVerified = tdUser.isVerified,
        isPremium = tdUser.isPremium,
        lastSeen = if (tdUser.status is TdApi.UserStatusOnline) Clock.System.now() else null
    )

    fun toDomainChat(tdChat: TdApi.Chat, tdUsers: Map<Long, TdApi.User>): Chat {
        val lastMessage = tdChat.lastMessage?.let { toDomainMessage(it) }
        return when (val type = tdChat.type) {
            is TdApi.ChatTypePrivate -> {
                val user = tdUsers[type.userId]?.let { toDomainUser(it) }
                Chat.Private(
                    id = tdChat.id,
                    title = tdChat.title,
                    photoUrl = tdChat.photo?.small?.local?.path,
                    lastMessage = lastMessage,
                    unreadCount = tdChat.unreadCount,
                    isPinned = tdChat.isPinned,
                    order = tdChat.order,
                    draftMessage = tdChat.draftMessage?.inputMessageText?.text,
                    user = user ?: User(type.userId, "", "", null, null, null),
                    isOnline = false,
                    isTyping = false
                )
            }
            else -> Chat.Group(
                id = tdChat.id,
                title = tdChat.title,
                photoUrl = tdChat.photo?.small?.local?.path,
                lastMessage = lastMessage,
                unreadCount = tdChat.unreadCount,
                isPinned = tdChat.isPinned,
                order = tdChat.order,
                draftMessage = tdChat.draftMessage?.inputMessageText?.text,
                memberCount = 0,
                permissions = ChatPermissions(),
                isChannel = false
            )
        }
    }

    fun toDomainMessage(tdMessage: TdApi.Message): Message {
        val media = toDomainMediaFromTdMessage(tdMessage.content)
        val text = when (val c = tdMessage.content) {
            is TdApi.MessageText -> c.text.text
            is TdApi.MessagePhoto -> c.caption.text
            is TdApi.MessageVideo -> c.caption.text
            is TdApi.MessageAudio -> c.caption.text
            is TdApi.MessageDocument -> c.caption.text
            else -> ""
        }
        return Message(
            id = tdMessage.id,
            chatId = tdMessage.chatId,
            senderId = tdMessage.senderId.getConstructor()?.get(0) as? Long ?: 0L,
            text = text,
            media = media,
            date = Instant.fromEpochSeconds(tdMessage.date),
            isOutgoing = tdMessage.isOutgoing,
            isRead = tdMessage.isOutgoing && tdMessage.sendingState is TdApi.MessageIsSuccessfullySent,
            replyToMessageId = tdMessage.replyToMessageId,
            reactions = tdMessage.reactions?.reactions?.map {
                Reaction(it.type.getConstructor()?.get(0) as? String ?: "", it.totalCount)
            } ?: emptyList(),
            editDate = tdMessage.editDate.takeIf { it > 0 }?.let { Instant.fromEpochSeconds(it) }
        )
    }

    private fun toDomainMediaFromTdMessage(content: TdApi.MessageContent): Media? {
        return when (content) {
            is TdApi.MessagePhoto -> {
                val photo = content.photo.sizes.maxByOrNull { it.width }
                Media.Photo(
                    fileId = photo?.photo?.id ?: 0,
                    url = photo?.photo?.local?.path ?: "",
                    width = photo?.width ?: 0,
                    height = photo?.height ?: 0
                )
            }
            is TdApi.MessageVideo -> {
                Media.Video(
                    fileId = content.video.video.id,
                    url = content.video.video.local.path,
                    duration = content.video.duration,
                    thumbnail = content.video.thumbnail?.file?.local?.path ?: "",
                    mimeType = content.video.mimeType,
                    width = content.video.width,
                    height = content.video.height
                )
            }
            else -> null
        }
    }

    fun toTdInputMessageText(text: String): TdApi.InputMessageContent {
        return TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), false, false)
    }
}
