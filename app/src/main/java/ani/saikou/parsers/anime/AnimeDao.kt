package ani.saikou.parsers.anime

import android.net.Uri
import ani.saikou.client
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.MixDrop
import ani.saikou.parsers.anime.extractors.StreamSB
import ani.saikou.parsers.anime.extractors.StreamTape
import ani.saikou.parsers.anime.extractors.VidStreaming
import ani.saikou.printIt
import ani.saikou.sortByTitle

class AnimeDao : AnimeParser() {
    override val name = "AnimeDao"
    override val saveName = "anime_dao"
    override val hostUrl = "https://animedao.to"
    override val isDubAvailableSeparately = true

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val res = client.get(animeLink).document
        val eps = res.select(".episodelist").map {
            Episode(
                it.select(".animename").text().substringAfter("Episode "),
                hostUrl + it.select("a").attr("href"),
                it.select("animetitle").text()
            )
        }
        val sp = res.select(".speciallist").map {
            Episode(
                it.select(".animename").text(),
                hostUrl + it.select("a").attr("href"),
                it.select(".animetitle").text(),
                it.select("img").attr("data-src")
            )
        }
        return eps + sp
    }

    private val epRegex = Regex("\"(\\/redirect\\/.*?)\"")
    override suspend fun loadVideoServers(episodeLink: String, extra: Map<String, String>?): List<VideoServer> {
        val res = client.get(episodeLink)
        val links = epRegex.findAll(res.text).toList().map {
            client.head(hostUrl+it.groupValues[1]).url
        }.printIt("Links : ")
        val names = res.document.select("#videotab .nav-link").map { it.text() }
        return links.mapIndexed { i, it ->
            VideoServer(names[i], it)
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        val domain = Uri.parse(server.embed.url).host ?: ""
        val extractor: VideoExtractor? = when {
            "sb" in domain       -> StreamSB(server)
            "streamta" in domain -> StreamTape(server)
            "vidstream" in domain -> VidStreaming(server)
            "mixdrop" in domain  -> MixDrop(server)
            else                 -> null
        }
        return extractor
    }

    override suspend fun search(query: String): List<ShowResponse> {
        return client.get("$hostUrl/search/?search=$query").document
            .select(".row .card-body").map {
                ShowResponse(
                    it.select(".animename").text(),
                    hostUrl + it.select("a").attr("href"),
                    it.select("img").attr("data-src")
                )
            }.sortByTitle(query)
    }
}