package ani.saikou.parsers.novel

import ani.saikou.client
import ani.saikou.parsers.Book
import ani.saikou.parsers.NovelParser
import ani.saikou.parsers.ShowResponse
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AnnaArchive : NovelParser() {

    override val name = "Anna's Archive"
    override val saveName = "anna"
    override val hostUrl = "https://annas-archive.org"

    private fun parseShowResponse(it: Element?): ShowResponse? {
        it ?: return null
        if (!it.select("div.text-xs").text().contains("epub"))
            return null
        val name = it.select(".text-xl").text()
        var img = it.selectFirst("img")?.attr("src") ?: ""
        if(img=="") img = "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/default.jpg"
        val extra = mapOf(
            "0" to it.select("div.italic").text(),
            "1" to it.select("div.text-sm").text(),
            "2" to it.select("div.text-xs").text(),
        )
        return ShowResponse(name, "$hostUrl${it.attr("href")}", img, extra = extra)
    }

    private val defaultImg = "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/default.jpg"
    private val VolumesRegex = Regex("vol\\.? (\\d+(\\.\\d+)?)|volume (\\d+(\\.\\d+)?)", RegexOption.IGNORE_CASE)
    override suspend fun search(query: String): List<ShowResponse> {
        val vols = client.get("$hostUrl/search?ext=epub&q=$query").document.select(".main > div > div").mapNotNull { div ->
            val a = div.selectFirst("a") ?: Jsoup.parse(div.data())
            parseShowResponse(a.selectFirst("a"))
        }.groupBy { res ->
            val match = VolumesRegex.find(res.name)?.groupValues
                ?.firstOrNull { it.isNotEmpty() }
                ?.substringAfter(" ")
                ?.toDoubleOrNull() ?: Double.MAX_VALUE
            match
        }.toSortedMap()
        return vols.convertWith(query)
    }


    private fun Map<Double, List<ShowResponse>>.convertWith(query: String): List<ShowResponse> {
        val resultList = this.values
            .flatMap { showList ->
                val nonDefaultCoverShows = showList.filter { it.coverUrl.url != defaultImg }
                val bestShow = nonDefaultCoverShows.firstOrNull { it.name.contains(query) }
                    ?: nonDefaultCoverShows.firstOrNull()
                    ?: showList.first()
                listOf(bestShow)
            }
            .toMutableList()

        val remainingShows = this.values.flatten().subtract(resultList.toSet())
        resultList.addAll(remainingShows)

        return resultList
    }

    override suspend fun loadBook(link: String, extra: Map<String, String>?): Book {
        return client.get(link).document.selectFirst("main")!!.let {
            val name = it.selectFirst("div.text-3xl")!!.text()
            val img = it.selectFirst("img")?.attr("src")
                ?: "https://s4.anilist.co/file/anilistcdn/media/manga/cover/medium/default.jpg"
            val description = it.selectFirst("div.js-md5-top-box-description")?.text()
            val links = it.select("a.js-download-link").mapNotNull { a ->
                val li = a.attr("href")
                //If it's not an epub, ignore it
                //if (!li.endsWith(".epub"))
                //    return@mapNotNull null
                li
            }
            Book(name, img, description, links)
        }
    }
}