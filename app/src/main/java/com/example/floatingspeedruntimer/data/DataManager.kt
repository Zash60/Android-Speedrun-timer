package com.example.floatingspeedruntimer.data
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
class DataManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()
    var games: MutableList<Game> = mutableListOf()
        private set
    init { loadGames() }
    fun saveGames() {
        val json = gson.toJson(games)
        File(appContext.filesDir, "speedrun_data.json").writeText(json)
    }
    private fun loadGames() {
        val dataFile = File(appContext.filesDir, "speedrun_data.json")
        if (dataFile.exists()) {
            val json = dataFile.readText()
            val type = object : TypeToken<MutableList<Game>>() {}.type
            games = gson.fromJson(json, type) ?: mutableListOf()
        }
    }
    fun findGameByName(name: String?): Game? = games.find { it.name == name }
    fun findCategoryByName(game: Game?, categoryName: String?): Category? = game?.categories?.find { it.name == categoryName }
    companion object {
        @Volatile
        private var INSTANCE: DataManager? = null
        fun getInstance(context: Context): DataManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataManager(context).also { INSTANCE = it }
            }
    }
}
