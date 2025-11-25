package com.example.floatingspeedruntimer.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// --- MODELOS DE DADOS (JSON) ---

data class GameSearchResponse(val data: List<GameData>)
data class GameData(
    val id: String,
    val names: GameNames,
    val assets: GameAssets
)
data class GameNames(val international: String)
data class GameAssets(val cover_medium: AssetUri?)
data class AssetUri(val uri: String)

data class CategoryResponse(val data: List<CategoryData>)
data class CategoryData(
    val id: String,
    val name: String,
    val type: String // "per-game" ou "per-level"
)

// --- INTERFACE DO RETROFIT ---

interface SpeedrunService {
    // Busca jogos pelo nome
    @GET("games")
    suspend fun searchGames(@Query("name") name: String): GameSearchResponse

    // Busca categorias de um jogo específico pelo ID
    @GET("games/{id}/categories")
    suspend fun getCategories(@Path("id") gameId: String): CategoryResponse
}

// --- SINGLETON PARA ACESSO ---

object SpeedrunApiClient {
    private const val BASE_URL = "https://www.speedrun.com/api/v1/"

    val service: SpeedrunService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpeedrunService::class.java)
    }
}
