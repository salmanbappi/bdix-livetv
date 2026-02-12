package eu.kanade.tachiyomi.animeextension.all.bdixlivetv

import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import extensions.utils.Source
import okhttp3.OkHttpClient
import androidx.preference.PreferenceScreen

class BDIXLiveTV : Source(), ConfigurableAnimeSource {

    override val name = "BDIX Live TV"
    override val baseUrl = "http://172.16.29.28"
    override val lang = "all"
    override val supportsLatest = false
    override val id: Long = 4519283712345678910L

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            chain.proceed(request)
        }
        .build()

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        return getSearchAnime(page, "", getFilterList())
    }

    override suspend fun getLatestUpdates(page: Int): AnimesPage = AnimesPage(emptyList(), false)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val response = client.newCall(GET(baseUrl)).execute()
        val html = response.body?.string() ?: ""
        
        val categoryFilter = filters.find { it is CategoryFilter } as? CategoryFilter
        val selectedCategory = categoryFilter?.let { it.values[it.state] } ?: "ALL"

        val animeList = mutableListOf<SAnime>()
        
        // The website structure has categories like SPORTS: [...], BANGLA: [...]
        // We'll parse the entire 'channels' object or just match all entries
        val channelRegex = Regex("""\{name:\s*"(.*?)",\s*url:\s*'(.*?)',\s*logo:\s*"(.*?)"\}""")
        
        // To handle categories, we might need to be more specific with the regex 
        // or just parse the categories block by block.
        // For now, let's extract the category blocks first.
        val categoryBlocks = Regex("""(\w+):\s*\[([\s\S]*?)\]""").findAll(html)
        
        categoryBlocks.forEach { block ->
            val categoryName = block.groups[1]?.value ?: ""
            val blockContent = block.groups[2]?.value ?: ""
            
            if (selectedCategory == "ALL" || selectedCategory.equals(categoryName, ignoreCase = true)) {
                channelRegex.findAll(blockContent).forEach { match ->
                    val name = match.groups[1]?.value ?: ""
                    val url = match.groups[2]?.value ?: ""
                    val logo = match.groups[3]?.value ?: ""
                    
                    if (name.isNotBlank() && name.contains(query, ignoreCase = true)) {
                        animeList.add(SAnime.create().apply {
                            this.title = name
                            this.url = url
                            this.thumbnail_url = fixUrl(logo)
                            this.genre = categoryName
                            this.initialized = true
                        })
                    }
                }
            }
        }
        
        return AnimesPage(animeList, false)
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return anime.apply {
            status = SAnime.UNKNOWN
            description = "Category: ${anime.genre}\nLive Stream: ${anime.title}"
            initialized = true
        }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return listOf(SEpisode.create().apply {
            name = anime.title
            url = anime.url
            episode_number = 1F
        })
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val url = episode.url
        return listOf(Video(url, "Live Stream", url))
    }

    private fun fixUrl(url: String): String = if (url.startsWith("http")) url else "$baseUrl${if (url.startsWith("/")) "" else "/"}$url"

    override fun getFilterList() = AnimeFilterList(
        CategoryFilter()
    )

    private class CategoryFilter : AnimeFilter.Select<String>(
        "Category",
        arrayOf("ALL", "SPORTS", "BANGLA", "HINDI", "NEWS", "MUSIC", "KIDS", "ENGLISH")
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {}
}