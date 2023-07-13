package ani.saikou.anime

import ImageSearchResultAdapter
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.saikou.*
import ani.saikou.App.Companion.context
import ani.saikou.anilist.Anilist
import ani.saikou.databinding.ActivityImageSearchBinding
import ani.saikou.media.MediaDetailsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ImageSearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImageSearchBinding
    private val viewModel: ImageSearchViewModel by viewModels()

    private val imageSelectionLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { imageUri ->
                val contentResolver = applicationContext.contentResolver
                lifecycleScope.launch(Dispatchers.IO) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    val result = analyzeImage(contentResolver, imageUri)
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                    }
                    viewModel.setSearchResult(result)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.uploadImage.setOnClickListener {
            clearResults()
            imageSelectionLauncher.launch("image/*")
        }
        binding.imageBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        viewModel.searchResultLiveData.observe(this) { result ->
            result?.let { displayResult(it) }
        }

    }

    private fun displayResult(result: ImageSearchViewModel.SearchResult) {
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        val searchResults: List<ImageSearchViewModel.ImageResult> = result.result.orEmpty()
        val adapter = ImageSearchResultAdapter(searchResults)

        adapter.setOnItemClickListener(object : ImageSearchResultAdapter.OnItemClickListener {
            override fun onItemClick(searchResult: ImageSearchViewModel.ImageResult) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val media = Anilist.query.getMedia(searchResult.anilist?.id?.toInt() ?: 0, false)

                    withContext(Dispatchers.Main) {
                        media?.let {
                            startActivity(
                                Intent(this@ImageSearchActivity, MediaDetailsActivity::class.java)
                                    .putExtra("media", it)
                            )
                        }
                    }
                }
            }
        })

        recyclerView.post {
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(context)
        }
    }

    private suspend fun analyzeImage(contentResolver: ContentResolver, imageUri: Uri): ImageSearchViewModel.SearchResult {
        val url = "https://api.trace.moe/search?anilistInfo"
        contentResolver.openInputStream(imageUri)?.use { inputStream ->
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    "image.jpg",
                    inputStream.readBytes().toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val jsonString = client.post(url, requestBody = requestBody).text
            return Mapper.parse(jsonString)
        }

        return ImageSearchViewModel.SearchResult()
    }

    private fun clearResults() {
        viewModel.setSearchResult(ImageSearchViewModel.SearchResult())
    }
}

class ImageSearchViewModel : ViewModel() {
    val searchResultLiveData: MutableLiveData<SearchResult> = MutableLiveData()

    fun setSearchResult(result: SearchResult) {
        searchResultLiveData.postValue(result)
    }

    @Serializable
    data class SearchResult(
        val frameCount: Long? = null,
        val error: String? = null,
        val result: List<ImageResult>? = null
    )

    @Serializable
    data class ImageResult(
        val anilist: AnilistData? = null,
        val filename: String? = null,
        @SerialName("episode") val rawEpisode: JsonElement? = null,
        val from: Double? = null,
        val to: Double? = null,
        val similarity: Double? = null,
        val video: String? = null,
        val image: String? = null
    ) {
        val episode: String?
            get() = rawEpisode?.toString()
    }

    @Serializable
    data class AnilistData(
        val id: Long? = null,
        val idMal: Long? = null,
        val title: Title? = null,
        val synonyms: List<String>? = null,
        val isAdult: Boolean? = null
    )

    @Serializable
    data class Title(
        val native: String? = null,
        val romaji: String? = null,
        val english: String? = null
    )
}