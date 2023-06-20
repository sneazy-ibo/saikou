package ani.saikou.novel

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.R
import ani.saikou.databinding.ItemNovelHeaderBinding
import ani.saikou.media.Media
import ani.saikou.parsers.NovelReadSources

class NovelReadAdapter(
private val media: Media,
private val fragment: NovelReadFragment,
private val mangaReadSources: NovelReadSources
) : RecyclerView.Adapter<NovelReadAdapter.ViewHolder>() {

    lateinit var progress: View

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NovelReadAdapter.ViewHolder {
        val binding = ItemNovelHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    private val imm = fragment.requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val binding = holder.binding
        progress = binding.progress.root

        fun search(){
            val query = binding.searchBarText.text.toString()
            val source = media.selected!!.source.let { if (it >= mangaReadSources.names.size) 0 else it }
            fragment.source = source

            binding.searchBarText.clearFocus()
            imm.hideSoftInputFromWindow(binding.searchBarText.windowToken, 0)
            fragment.search(query, source)
        }

        val source = media.selected!!.source.let { if (it >= mangaReadSources.names.size) 0 else it }
        binding.animeSource.setText(mangaReadSources.names[source])
        binding.animeSource.setAdapter(ArrayAdapter(fragment.requireContext(), R.layout.item_dropdown, mangaReadSources.names))
        binding.animeSource.setOnItemClickListener { _, _, i, _ ->
            fragment.onSourceChange(i)
            search()
        }

        binding.searchBarText.setText(fragment.searchQuery)
        binding.searchBarText.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_SEARCH -> {
                    search()
                    true
                }
                else                         -> false
            }
        }
        binding.searchBar.setEndIconOnClickListener { search() }
    }

    override fun getItemCount(): Int = 1

    inner class ViewHolder(val binding: ItemNovelHeaderBinding) : RecyclerView.ViewHolder(binding.root)
}