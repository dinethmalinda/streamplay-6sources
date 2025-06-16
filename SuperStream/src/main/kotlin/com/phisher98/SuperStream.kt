package com.phisher98

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.phisher98.SuperStreamExtractor.invokeSubtitleAPI
import com.phisher98.SuperStreamExtractor.invokeSuperstream
import com.phisher98.SuperStreamExtractor.invokecatflix
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.roundToInt

open class SuperStream(val sharedPref: SharedPreferences? = null) : TmdbProvider() {
    override var name = "SuperStream"
    override val hasMainPage = true
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon,
    )

    val token = sharedPref?.getString("token", null)

    companion object {
        /** TOOLS */
        private const val tmdbAPI = "https://api.themoviedb.org/3"
        private const val apiKey = BuildConfig.TMDB_API
        const val Catflix= "https://catflix.su"
        const val febbox="https://www.febbox.com"
        fun getType(t: String?): TvType {
            return when (t) {
                "movie" -> TvType.Movie
                else -> TvType.TvSeries
            }
        }

        fun getStatus(t: String?): ShowStatus {
            return when (t) {
                "Returning Series" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }

    }

    override val mainPage = mainPageOf(
        "$tmdbAPI/trending/all/day?api_key=$apiKey&region=US" to "Trending",
        "$tmdbAPI/movie/popular?api_key=$apiKey&region=US" to "Popular Movies",
        "$tmdbAPI/tv/popular?api_key=$apiKey&region=US&with_original_language=en" to "Popular TV Shows",
        "$tmdbAPI/tv/airing_today?api_key=$apiKey&region=US&with_original_language=en" to "Airing Today TV Shows",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=213" to "Netflix",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=1024" to "Amazon",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2739" to "Disney+",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=453" to "Hulu",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2552" to "Apple TV+",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=49" to "HBO",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=4330" to "Paramount+",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=3353" to "Peacock",
        "$tmdbAPI/discover/movie?api_key=$apiKey&language=en-US&page=1&sort_by=popularity.desc&with_origin_country=IN" to "Indian Movies",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=4008" to "JioCinema",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=5920" to "Amazon MiniTV",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=1112" to "Crunchyroll",
        "$tmdbAPI/movie/top_rated?api_key=$apiKey&region=US" to "Top Rated Movies",
        "$tmdbAPI/tv/top_rated?api_key=$apiKey&region=US" to "Top Rated TV Shows",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_original_language=ko" to "Korean Shows",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_keywords=210024|222243&sort_by=popularity.desc&air_date.lte=${getDate().today}&air_date.gte=${getDate().today}" to "Airing Today Anime",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_keywords=210024|222243&sort_by=popularity.desc&air_date.lte=${getDate().nextWeek}&air_date.gte=${getDate().today}" to "On The Air Anime",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_genres=16&sort_by=air_date.desc&air_date.lte=${getDate().nextWeek}&air_date.gte=${getDate().today}&language=jp" to "Recently Updated Anime",
        "$tmdbAPI/discover/tv?api_key=$apiKey&with_keywords=210024|222243" to "Anime",
        "$tmdbAPI/discover/movie?api_key=$apiKey&with_keywords=210024|222243" to "Anime Movies",
        //"$tmdbAPI/movie/upcoming?api_key=$apiKey&region=US" to "Upcoming Movies",
        "Personal" to "Personal Febbox Content"
        )
    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    private fun getOriImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.name.contains("Personal")) {
            if (token.isNullOrEmpty()) {
                return newHomePageResponse(
                    "Login required for Febbox Personal content.",
                    emptyList<SearchResponse>(),
                    false
                )
            }

            val htmlResponse = app.get(
                "$febbox/console/file_list",
                headers = mapOf("cookie" to token)
            ).parsedSafe<HTML>()

            val document = Jsoup.parse(htmlResponse?.html.orEmpty())
            val parsedHtmlContent = document.select("div.list_scroll > div > div")
            val filesFromHtml = parsedHtmlContent.mapNotNull { div ->
                div.toSearchResponse()
             }

            return newHomePageResponse(
                listOf(
                    HomePageList(
                        request.name,
                        filesFromHtml,
                        true
                    )
                )
            )
        }
        else {
            val adultQuery =
                if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669"
            val type = if (request.data.contains("/movie")) "movie" else "tv"

            val home = app.get("${request.data}$adultQuery&page=$page")
                .parsedSafe<Results>()
                ?.results
                ?.mapNotNull { it.toSearchResponse(type) }
                ?: throw ErrorLoadingException("Invalid JSON response")

            return newHomePageResponse(request.name, home)
        }
    }


    private fun Media.toSearchResponse(type: String? = null): SearchResponse? {
        return newMovieSearchResponse(
            title ?: name ?: originalTitle ?: return null,
            Data(id = id, type = mediaType ?: type).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = getImageUrl(posterPath)
        }
    }


    private fun Element.toSearchResponse(): SearchResponse {
        val title=this.select("p.file_name_show").text()
        val poster=this.select("div.file_icon").attr("style").substringAfter("(").substringBefore(")")
        val href="$febbox/console/share_file_comment?fid="+this.select("div").attr("data-id")
        return newMovieSearchResponse(
            title,
            href,
            TvType.Movie,
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get("$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$query&page=1&include_adult=${settingsForProvider.enableAdult}")
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse()
            }
    }

    override suspend fun load(url: String): LoadResponse? {
        if (url.startsWith("$febbox/console/share_file_comment?fid")) {
            val gson = Gson()
            val jsonString = app.get(url, headers = mapOf("cookie" to (token ?: ""))).text
            val response = gson.fromJson(jsonString, PersonalComments::class.java)
            val media = response?.file

            if (media != null) {
                return newMovieLoadResponse(
                    media.file_name,
                    "$febbox|"+media.fid.toString(),
                    TvType.Movie,
                    "$febbox|"+media.fid
                ) {
                    this.posterUrl = media.thumb_big
                    this.plot = "Added: ${media.add_time} | Updated: ${media.update_time} | Size: ${media.file_size}"
                }
            }
        }
        val data = parseJson<Data>(url)
        val type = getType(data.type)
        val append = "alternative_titles,credits,external_ids,keywords,videos,recommendations"
        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&append_to_response=$append"
        }
        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")

        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath)
        val bgPoster = getOriImageUrl(res.backdropPath)
        val orgTitle = res.originalTitle ?: res.originalName ?: return null
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
        val rating = res.vote_average.toString().toRatingInt()
        val genres = res.genres?.mapNotNull { it.name }

        val isCartoon = genres?.contains("Animation") ?: false
        val isAnime = isCartoon && (res.original_language == "zh" || res.original_language == "ja")
        val isAsian = !isAnime && (res.original_language == "zh" || res.original_language == "ko")
        val isBollywood = res.production_countries?.any { it.name == "India" } ?: false

        val keywords = res.keywords?.results?.mapNotNull { it.name }.orEmpty()
            .ifEmpty { res.keywords?.keywords?.mapNotNull { it.name } }

        val actors = res.credits?.cast?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: cast.originalName
                    ?: return@mapNotNull null, getImageUrl(cast.profilePath)
                ), roleString = cast.character
            )
        } ?: return null
        val recommendations =
            res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }

        val trailer = res.videos?.results?.filter { it.type == "Trailer" }?.map { "https://www.youtube.com/watch?v=${it.key}" }?.reversed().orEmpty()
            .ifEmpty { res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" } }

        if (type == TvType.TvSeries) {
            val lastSeason = res.last_episode_to_air?.season_number
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(LinkData(
                            data.id,
                            res.external_ids?.imdb_id,
                            res.external_ids?.tvdb_id,
                            data.type,
                            eps.seasonNumber,
                            eps.episodeNumber,
                            eps.id,
                            title = title,
                            year = season.airDate?.split("-")?.first()?.toIntOrNull(),
                            orgTitle = orgTitle,
                            isAnime = isAnime,
                            airedYear = year,
                            lastSeason = lastSeason,
                            epsTitle = eps.name,
                            jpTitle = res.alternative_titles?.results?.find { it.iso_3166_1 == "JP" }?.title,
                            date = season.airDate,
                            airedDate = res.releaseDate
                                ?: res.firstAirDate,
                            isAsian = isAsian,
                            isBollywood = isBollywood,
                            isCartoon = isCartoon
                        ).toJson())
                        {
                            this.name=eps.name + if (isUpcoming(eps.airDate)) " • [UPCOMING]" else ""
                            this.season=eps.seasonNumber
                            this.episode=eps.episodeNumber
                            this.posterUrl=getImageUrl(eps.stillPath)
                            this.rating=eps.voteAverage?.times(10)?.roundToInt()
                            this.description=eps.overview
                        }.apply {
                            this.addDate(eps.airDate)
                        }
                    }
            }?.flatten() ?: listOf()
            if (isAnime) {
                return newAnimeLoadResponse(title, url, TvType.Anime) {
                    addEpisodes(DubStatus.Subbed, episodes)
                    this.posterUrl = poster
                    this.backgroundPosterUrl = bgPoster
                    this.year = year
                    this.plot = res.overview
                    this.tags = keywords
                        ?.map { word -> word.replaceFirstChar { it.titlecase() } }
                        ?.takeIf { it.isNotEmpty() }
                        ?: genres
                    this.rating = rating
                    this.showStatus = getStatus(res.status)
                    this.recommendations = recommendations
                    this.actors = actors
                    this.contentRating = fetchContentRating(data.id, "US")
                    addTrailer(trailer)
                    addTMDbId(data.id.toString())
                    addImdbId(res.external_ids?.imdb_id)
                }
            } else {
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = bgPoster
                    this.year = year
                    this.plot = res.overview
                    this.tags = keywords
                        ?.map { word -> word.replaceFirstChar { it.titlecase() } }
                        ?.takeIf { it.isNotEmpty() }
                        ?: genres

                    this.rating = rating
                    this.showStatus = getStatus(res.status)
                    this.recommendations = recommendations
                    this.actors = actors
                    this.contentRating = fetchContentRating(data.id, "US")
                    addTrailer(trailer)
                    addTMDbId(data.id.toString())
                    addImdbId(res.external_ids?.imdb_id)
                }
            }
        } else {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LinkData(
                    data.id,
                    res.external_ids?.imdb_id,
                    res.external_ids?.tvdb_id,
                    data.type,
                    title = title,
                    year = year,
                    orgTitle = orgTitle,
                    isAnime = isAnime,
                    jpTitle = res.alternative_titles?.results?.find { it.iso_3166_1 == "JP" }?.title,
                    airedDate = res.releaseDate
                        ?: res.firstAirDate,
                    isAsian = isAsian,
                    isBollywood = isBollywood
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.comingSoon = isUpcoming(releaseDate)
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = keywords
                    ?.map { word -> word.replaceFirstChar { it.titlecase() } }
                    ?.takeIf { it.isNotEmpty() }
                    ?: genres

                this.rating = rating
                this.recommendations = recommendations
                this.actors = actors
                this.contentRating = fetchContentRating(data.id, "US")
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
            }
        }
    }
    @SuppressLint("SuspiciousIndentation")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith(febbox))
        {
            val fid=data.substringAfterLast("|")
            val postdata = mapOf(
                "fid" to fid,
                "share" to "",
                "imdb_id" to "",
                "quality" to ""
            )
            val source = app.post(url = "$febbox/console/player", data = postdata, headers = mapOf("cookie" to (token ?: ""))).text
            val regex = """\{"type":"([^"]+)","file":"([^"]+)","label":"([^"]+)"\}"""
            val pattern = Pattern.compile(regex)
            val matcher = pattern.matcher(source)
            while (matcher.find()) {
                val file = matcher.group(2)
                    ?.replace("\\/", "/")
                val label = matcher.group(3)
                if (file!=null)
                callback.invoke(
                    newExtractorLink(
                        "$name $label",
                        "$name $label",
                        file,
                        INFER_TYPE
                    )
                    {
                        this.quality= Qualities.P1080.value
                    }
                )
            }
        }
        else {
            val res = parseJson<LinkData>(data)
            argamap(
                {
                    invokeSubtitleAPI(
                        res.imdbId,
                        res.season,
                        res.episode,
                        subtitleCallback,
                    )
                },
                {
                    invokeSuperstream(
                        token,
                        res.imdbId,
                        res.season,
                        res.episode,
                        callback
                    )
                },
                {
                    if (!res.isAnime) invokecatflix(
                        res.id,
                        res.epid,
                        res.title,
                        res.episode,
                        res.season,
                        callback
                    )
                }
            )
        }
        return true
    }
    data class LinkData(
val id: Int? = null,
val imdbId: String? = null,
val tvdbId: Int? = null,
val type: String? = null,
val season: Int? = null,
val episode: Int? = null,
val epid: Int? = null,
val aniId: String? = null,
val animeId: String? = null,
val title: String? = null,
val year: Int? = null,
val orgTitle: String? = null,
val isAnime: Boolean = false,
val airedYear: Int? = null,
val lastSeason: Int? = null,
val epsTitle: String? = null,
val jpTitle: String? = null,
val date: String? = null,
val airedDate: String? = null,
val isAsian: Boolean = false,
val isBollywood: Boolean = false,
val isCartoon: Boolean = false,
)

data class Data(
val id: Int? = null,
val type: String? = null,
val aniId: String? = null,
val malId: Int? = null,
)

data class Results(
@JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
)

data class Media(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
@JsonProperty("title") val title: String? = null,
@JsonProperty("original_title") val originalTitle: String? = null,
@JsonProperty("media_type") val mediaType: String? = null,
@JsonProperty("poster_path") val posterPath: String? = null,
)

data class Genres(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
)

data class Keywords(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
)

data class KeywordResults(
@JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(),
@JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
)

data class Seasons(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
@JsonProperty("season_number") val seasonNumber: Int? = null,
@JsonProperty("air_date") val airDate: String? = null,
)

data class Cast(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
@JsonProperty("original_name") val originalName: String? = null,
@JsonProperty("character") val character: String? = null,
@JsonProperty("known_for_department") val knownForDepartment: String? = null,
@JsonProperty("profile_path") val profilePath: String? = null,
)

data class Episodes(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("name") val name: String? = null,
@JsonProperty("overview") val overview: String? = null,
@JsonProperty("air_date") val airDate: String? = null,
@JsonProperty("still_path") val stillPath: String? = null,
@JsonProperty("vote_average") val voteAverage: Double? = null,
@JsonProperty("episode_number") val episodeNumber: Int? = null,
@JsonProperty("season_number") val seasonNumber: Int? = null,
)

data class MediaDetailEpisodes(
@JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
)

data class Trailers(
@JsonProperty("key") val key: String? = null,
@JsonProperty("type") val type: String? = null,
)

data class ResultsTrailer(
@JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
)

data class AltTitles(
@JsonProperty("iso_3166_1") val iso_3166_1: String? = null,
@JsonProperty("title") val title: String? = null,
@JsonProperty("type") val type: String? = null,
)

data class ResultsAltTitles(
@JsonProperty("results") val results: ArrayList<AltTitles>? = arrayListOf(),
)

data class ExternalIds(
@JsonProperty("imdb_id") val imdb_id: String? = null,
@JsonProperty("tvdb_id") val tvdb_id: Int? = null,
)

data class Credits(
@JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
)

data class ResultsRecommendations(
@JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
)

data class LastEpisodeToAir(
@JsonProperty("episode_number") val episode_number: Int? = null,
@JsonProperty("season_number") val season_number: Int? = null,
)

data class ProductionCountries(
@JsonProperty("name") val name: String? = null,
)

data class MediaDetail(
@JsonProperty("id") val id: Int? = null,
@JsonProperty("imdb_id") val imdbId: String? = null,
@JsonProperty("title") val title: String? = null,
@JsonProperty("name") val name: String? = null,
@JsonProperty("original_title") val originalTitle: String? = null,
@JsonProperty("original_name") val originalName: String? = null,
@JsonProperty("poster_path") val posterPath: String? = null,
@JsonProperty("backdrop_path") val backdropPath: String? = null,
@JsonProperty("release_date") val releaseDate: String? = null,
@JsonProperty("first_air_date") val firstAirDate: String? = null,
@JsonProperty("overview") val overview: String? = null,
@JsonProperty("runtime") val runtime: Int? = null,
@JsonProperty("vote_average") val vote_average: Any? = null,
@JsonProperty("original_language") val original_language: String? = null,
@JsonProperty("status") val status: String? = null,
@JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
@JsonProperty("keywords") val keywords: KeywordResults? = null,
@JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null,
@JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
@JsonProperty("videos") val videos: ResultsTrailer? = null,
@JsonProperty("external_ids") val external_ids: ExternalIds? = null,
@JsonProperty("credits") val credits: Credits? = null,
@JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
@JsonProperty("alternative_titles") val alternative_titles: ResultsAltTitles? = null,
@JsonProperty("production_countries") val production_countries: ArrayList<ProductionCountries>? = arrayListOf(),
)



    private fun getDate(): TmdbDate {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calender = Calendar.getInstance()
        val today = formatter.format(calender.time)
        calender.add(Calendar.WEEK_OF_YEAR, 1)
        val nextWeek = formatter.format(calender.time)
        return TmdbDate(today, nextWeek)
    }

    data class TmdbDate(
        val today: String,
        val nextWeek: String,
    )

    private fun isUpcoming(dateString: String?): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
            unixTimeMS < dateTime
        } catch (t: Throwable) {
            logError(t)
            false
        }
    }

}
