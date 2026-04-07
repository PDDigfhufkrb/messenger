// Добавить в существующий объект TdLibMapper

fun toDomainMediaFromTdMessage(content: TdApi.MessageContent): Media? {
    return when (content) {
        is TdApi.MessagePhoto -> {
            val photo = content.photo.sizes.maxByOrNull { it.width }
            Media.Photo(
                url = photo?.photo?.local?.path ?: photo?.photo?.remote?.id ?: "",
                width = photo?.width ?: 0,
                height = photo?.height ?: 0,
                fileId = photo?.photo?.id ?: 0
            )
        }
        is TdApi.MessageVideo -> {
            Media.Video(
                url = content.video.video.local.path,
                duration = content.video.duration,
                thumbnail = content.video.thumbnail?.file?.local?.path ?: "",
                fileId = content.video.video.id,
                mimeType = content.video.mimeType,
                width = content.video.width,
                height = content.video.height
            )
        }
        is TdApi.MessageAudio -> {
            Media.Audio(
                url = content.audio.audio.local.path,
                duration = content.audio.duration,
                title = content.audio.title,
                performer = content.audio.performer,
                fileId = content.audio.audio.id
            )
        }
        is TdApi.MessageDocument -> {
            Media.Document(
                url = content.document.document.local.path,
                name = content.document.fileName,
                size = content.document.document.size,
                mimeType = content.document.mimeType,
                fileId = content.document.document.id
            )
        }
        is TdApi.MessageVoiceNote -> {
            Media.Voice(
                url = content.voiceNote.voice.local.path,
                duration = content.voiceNote.duration,
                waveform = content.voiceNote.waveform,
                fileId = content.voiceNote.voice.id
            )
        }
        is TdApi.MessageSticker -> {
            Media.Sticker(
                url = content.sticker.sticker.local.path,
                emoji = content.sticker.emoji,
                width = content.sticker.width,
                height = content.sticker.height,
                fileId = content.sticker.sticker.id
            )
        }
        else -> null
    }
}

// Обновить Media sealed class в domain
