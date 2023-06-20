package ani.saikou.parsers

import ani.saikou.FileUrl

abstract class NovelParser : BaseParser() {
    abstract suspend fun loadBook(link: String, extra: Map<String, String>?): Book
}

data class Book(
    val name: String,
    val img: FileUrl,
    val description: String? = null,
    val links: List<FileUrl>
) {
    constructor (name: String, img: String, description: String? = null, links: List<String>) : this(
        name,
        FileUrl(img),
        description,
        links.map { FileUrl(it) }
    )
}