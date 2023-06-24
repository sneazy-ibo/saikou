package ani.saikou.parsers.anime.extractors

import ani.saikou.client
import ani.saikou.getSize
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType

class Mp4Upload(override val server: VideoServer) : VideoExtractor() {
    override suspend fun extract(): VideoContainer {
        println("UHhh Mp4")
        val link = client.get(server.embed.url).document
            .select("script").html()
            .substringAfter("src: \"").substringBefore("\"")
        return VideoContainer(listOf(Video(null, VideoType.CONTAINER, link, getSize(link))))
    }
}