package com.example.floatingspeedruntimer.data
import java.io.Serializable
data class Game(var name: String, val categories: MutableList<Category> = mutableListOf())
data class Category(var name: String, var personalBest: Long = 0, var runs: Int = 0, val splits: MutableList<Split> = mutableListOf(), val runHistory: MutableList<Run> = mutableListOf())
data class Split(var name: String, var personalBestTime: Long = 0, var bestSegmentTime: Long = 0)
data class Run(val finalTime: Long, val timestamp: Long, val segmentTimes: List<Long>) : Serializable
