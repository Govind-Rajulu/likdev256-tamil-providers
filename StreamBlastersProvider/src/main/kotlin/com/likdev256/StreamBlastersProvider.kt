# Getting Started with com.likdev256

package com.likdev256

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class StreamBlastersProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.streamblasters.link"
    override var name = "StreamBlasters"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
    "$mainUrl/category/anime/" to "Anime",
    "$mainUrl/category/anime/hindi-anime-series/2024-hindi-anime-series/" to "2024 Hindi Anime Series",
    "$mainUrl/category/anime/japanese-anime-series/2024-japanese-anime-series/" to "2024 Japanese Anime Series",
    "$mainUrl/category/anime/tamil-anime-series/2024-tamil-anime-series/" to "2024 Tamil Anime Series",
    "$mainUrl/category/anime/telugu-anime-movies/2024-telugu-anime-movies/" to "2024 Telugu Anime Movies",
    "$mainUrl/category/anime/telugu-anime-series/2024-telugu-anime-series/" to "2024 Telugu Anime Series",
    "$mainUrl/category/chinese/" to "Chinese",
    "$mainUrl/category/english/" to "English",
    "$mainUrl/category/english/2024-english-movies/" to "2024 English Movies",
    "$mainUrl/category/hindi/" to "Hindi",
    "$mainUrl/category/hindi/2024-hindi-movies/" to "2024 Hindi Movies",
    "$mainUrl/category/kannada/" to "Kannada",
    "$mainUrl/category/kannada/2024-kannada-movies/" to "2024 Kannada Movies",
    "$mainUrl/category/korean/" to "Korean",
    "$mainUrl/category/malayalam/" to "Malayalam",
    "$mainUrl/category/malayalam/2024-malayalam-movies/" to "2024 Malayalam Movies",
    "$mainUrl/category/tamil/" to "Tamil",
    "$mainUrl/category/tamil/2024-tamil-movies/" to "2024 Tamil Movies",
    "$mainUrl/category/telugu/" to "Telugu",
    "$mainUrl/category/telugu/2024-telugu-movies/" to "2024 Telugu Movies",
    "$mainUrl/category/web-series/" to "Web Series",
    "$mainUrl/category/web-series/english-web-series/2024-english-web-series/" to "2024 English Web Series",
    "$mainUrl/category/web-series/hindi-web-series/2024-hindi-web-series/" to "2024 Hindi Web Series",
    "$mainUrl/category/web-series/tamil-web-series/2024-tamil-web-series/" to "2024 Tamil Web Series",
    "$mainUrl/category/web-series/telugu-web-series/2024-telugu-web-series/" to "2024 Telugu Web Series",
    "$mainUrl/live/sports/" to "Live Sports"
).apply {
    // Optimization: Pre-fetch and cache the main page links for faster access
    prefetchLinks()
}

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        val quality = getQualityFromString(this.select("span.quality").text())

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        return document.select(".result-item").mapNotNull {
            val title = it.select(".title a").text().trim()
            val href = fixUrl(it.selectFirst(".title a")?.attr("href").toString())
            val posterUrl = fixUrlNull(it.selectFirst(".thumbnail img")?.attr("src"))
            val quality = getQualityFromString(it.select("span.quality").text())
            val tvtype = if (href.contains("tvshows")) TvType.TvSeries else TvType.Movie
            newMovieSearchResponse(title, href, tvtype) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.sheader h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        //val tags = document.select("div.mvici-left p:nth-child(1) a").map { it.text() }
        val year = document.select("span.date").text().trim().split(",").last()
            .toIntOrNull()
        val tvType = if (document.select("#seasons")
                .isNullOrEmpty()
        ) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst(".wp-content p")?.text()?.trim()
        val trailer = fixUrlNull(document.select("iframe").attr("src"))
        val rating = document.select("#info span strong").text().toRatingInt()
        val actors = document.select("#cast > div:nth-child(4)").map { it.text() }
        val recommendations = document.select("article").mapNotNull {
            it.toSearchResult()
        }

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("ul.episodios li").mapNotNull {
                val href = fixUrl(it.select("a").attr("href")?: return null)
                val name = it.select("a").text().trim()
                val thumbs = it.select("img").attr("src")
                val season = it.select(".numerando").text().split(" - ").first().toInt()
                val episode = it.select(".numerando").text().split(" - ").last().toInt()
                Episode(
                    href,
                    name,
                    season,
                    episode,
                    thumbs
                )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                //this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                //this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        val id = document.select(".dooplay_player_option").attr("data-post")
        document.select("ul#playeroptionsul li#player-option-1").map {
            it.attr("data-nume")
        }.apmap { nume ->
            safeApiCall {
                val response = app.post(
                    url = "$mainUrl/wp-admin/admin-ajax.php",
                    data = mapOf(
                        "action" to "doo_player_ajax",
                        "post" to id,
                        "nume" to nume,
                        "type" to "movie"
                    ),
                    referer = data,
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).parsed<ResponseHash>().embed_url
                val source = if(response.contains("iframe")) Jsoup.parse(response).select("iframe").attr("src") else response
                loadExtractor(source, data, subtitleCallback, callback)
            }
        }
        return true
    }
    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String?,
    )
}
