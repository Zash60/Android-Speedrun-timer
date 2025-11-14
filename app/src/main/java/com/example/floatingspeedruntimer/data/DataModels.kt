package com.example.floatingspeedruntimer.data

import java.io.Serializable

data class Game(var name: String, val categories: MutableList<Category> = mutableListOf())

data class Category(
    var name: String,
    var personalBest: Long = 0,
    var runs: Int = 0,
    val splits: MutableList<Split> = mutableListOf(),
    val runHistory: MutableList<Run> = mutableListOf(),
    
    // CAMPOS PARA O AUTOSPLITTER AVANÇADO
    var autoSplitterEnabled: Boolean = false,
    var autoSplitterThreshold: Double = 0.9, // Valor padrão de 90%
    var autoSplitterCaptureRegion: RectData? = null // Coordenadas do retângulo
)

// NOVA CLASSE para armazenar as coordenadas do retângulo de forma serializável
data class RectData(val left: Int, val top: Int, val right: Int, val bottom: Int) : Serializable

data class Split(
    var name: String,
    var personalBestTime: Long = 0,
    var bestSegmentTime: Long = 0,
    var autoSplitImagePath: String? = null
)

data class Run(
    val finalTime: Long,
    val timestamp: Long,
    val segmentTimes: List<Long>
) : Serializable
