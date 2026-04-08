package com.hemax.models

sealed class Media {
    abstract val fileId: Int

    data class Photo(
        override val fileId: Int,
        val url: String,
        val width: Int,
        val height: Int
    ) : Media()

    data class Video(
        override val fileId: Int,
        val url: String,
        val duration: Int,
        val thumbnail: String,
        val mimeType: String,
        val width: Int,
        val height: Int
    ) : Media()

    data class Audio(
        override val fileId: Int,
        val url: String,
        val duration: Int,
        val title: String,
        val performer: String
    ) : Media()

    data class Document(
        override val fileId: Int,
        val url: String,
        val name: String,
        val size: Long,
        val mimeType: String
    ) : Media()

    data class Voice(
        override val fileId: Int,
        val url: String,
        val duration: Int,
        val waveform: ByteArray
    ) : Media() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Voice
            if (fileId != other.fileId) return false
            if (url != other.url) return false
            if (duration != other.duration) return false
            if (!waveform.contentEquals(other.waveform)) return false
            return true
        }
        override fun hashCode(): Int {
            var result = fileId
            result = 31 * result + url.hashCode()
            result = 31 * result + duration
            result = 31 * result + waveform.contentHashCode()
            return result
        }
    }

    data class Sticker(
        override val fileId: Int,
        val url: String,
        val emoji: String,
        val width: Int,
        val height: Int
    ) : Media()
}
