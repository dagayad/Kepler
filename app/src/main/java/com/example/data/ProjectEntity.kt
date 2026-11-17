package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studio_projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val videoUriString: String,
    val videoDisplayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val detectedTopic: String,
    val languageModeName: String,
    val subtitlesJson: String,
    val styleConfigJson: String,
    val autoZoomIntensityName: String,
    val zoomKeyframesJson: String,
    val bRollItemsJson: String,
    val aspectRatioName: String,
    val resolutionName: String,
    val lastExportedPath: String?,
    val updatedAtMs: Long
)
