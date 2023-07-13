import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.R
import ani.saikou.anime.ImageSearchViewModel
import ani.saikou.currActivity
import ani.saikou.loadImage

class ImageSearchResultAdapter(private val searchResults: List<ImageSearchViewModel.ImageResult>) :
    RecyclerView.Adapter<ImageSearchResultAdapter.SearchResultViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(searchResult: ImageSearchViewModel.ImageResult)
    }

    private var itemClickListener: OnItemClickListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener) {
        itemClickListener = listener
    }

    inner class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val animeTextView: TextView = itemView.findViewById(R.id.itemCompactTitle)
        val filenameTextView: TextView = itemView.findViewById(R.id.itemTotal)
        val episodeTextView: TextView = itemView.findViewById(R.id.episedeNumber)
        val timestampTextView: TextView = itemView.findViewById(R.id.timeStamp)
        val itemCompactBanner: AppCompatImageView = itemView.findViewById(R.id.itemCompactBanner)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.image_search_recycler, parent, false)
        return SearchResultViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val searchResult = searchResults[position]
        holder.itemView.setOnClickListener {
            itemClickListener?.onItemClick(searchResult)
        }

        holder.animeTextView.text = searchResult.anilist?.title?.romaji
        holder.filenameTextView.text = currActivity()!!.getString(
            R.string.similarity_text, String.format("%.1f", searchResult.similarity?.times(100))
        )

        holder.episodeTextView.text = currActivity()!!.getString(R.string.episode_num, searchResult.episode.toString())
        holder.timestampTextView.text =
            currActivity()!!.getString(R.string.time_range,convertSecondsToTimestamp(searchResult.from),convertSecondsToTimestamp(searchResult.to)  )

        holder.itemCompactBanner.loadImage(searchResult.image)
    }

    override fun getItemCount(): Int {
        return searchResults.size
    }

    private fun convertSecondsToTimestamp(seconds: Double?): String {
        val minutes = (seconds?.div(60))?.toInt()
        val remainingSeconds = (seconds?.mod(60.0))?.toInt()

        val minutesString = minutes.toString().padStart(2, '0')
        val secondsString = remainingSeconds.toString().padStart(2, '0')

        return "$minutesString:$secondsString"
    }
}
