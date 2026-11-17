package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AutoZoomIntensity
import com.example.model.BRollItem
import com.example.model.ExportAspectRatio
import com.example.model.ExportResolution
import com.example.model.StudioProject
import com.example.model.SubtitleCue
import com.example.model.SubtitleFontOption
import com.example.model.SubtitleLanguageMode
import com.example.model.SubtitleStyleConfig
import com.example.model.VideoMetadata
import com.example.model.ZoomKeyframe
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kepler_studio_prefs"
)

class ProjectRepository(
    context: Context,
    private val dao: ProjectDao = KeplerDatabase.getInstance(context).projectDao()
) {
    private val appContext = context.applicationContext

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val cueListAdapter = moshi.adapter<List<SubtitleCue>>(
        Types.newParameterizedType(List::class.java, SubtitleCue::class.java)
    )
    private val zoomListAdapter = moshi.adapter<List<ZoomKeyframe>>(
        Types.newParameterizedType(List::class.java, ZoomKeyframe::class.java)
    )
    private val bRollListAdapter = moshi.adapter<List<BRollItem>>(
        Types.newParameterizedType(List::class.java, BRollItem::class.java)
    )
    private val styleAdapter = moshi.adapter(SubtitleStyleConfig::class.java)

    private object PrefKeys {
        val DEFAULT_LANGUAGE = stringPreferencesKey("default_language")
        val DEFAULT_FONT = stringPreferencesKey("default_font")
        val DEFAULT_ASPECT = stringPreferencesKey("default_aspect")
    }

    val savedProjects: Flow<List<StudioProject>> = dao.observeAllProjects().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getDefaultSettings(): Triple<SubtitleLanguageMode, SubtitleFontOption, ExportAspectRatio> {
        val prefs = appContext.studioDataStore.data.first()
        val lang = prefs[PrefKeys.DEFAULT_LANGUAGE]?.let {
            runCatching { SubtitleLanguageMode.valueOf(it) }.getOrNull()
        } ?: SubtitleLanguageMode.SINHALA_ENGLISH_MIXED
        val font = prefs[PrefKeys.DEFAULT_FONT]?.let {
            runCatching { SubtitleFontOption.valueOf(it) }.getOrNull()
        } ?: SubtitleFontOption.NOTO_SANS_SINHALA
        val aspect = prefs[PrefKeys.DEFAULT_ASPECT]?.let {
            runCatching { ExportAspectRatio.valueOf(it) }.getOrNull()
        } ?: ExportAspectRatio.REELS_9_16
        return Triple(lang, font, aspect)
    }

    suspend fun saveDefaultSettings(
        languageMode: SubtitleLanguageMode,
        fontOption: SubtitleFontOption,
        aspectRatio: ExportAspectRatio
    ) {
        appContext.studioDataStore.edit { prefs ->
            prefs[PrefKeys.DEFAULT_LANGUAGE] = languageMode.name
            prefs[PrefKeys.DEFAULT_FONT] = fontOption.name
            prefs[PrefKeys.DEFAULT_ASPECT] = aspectRatio.name
        }
    }

    suspend fun saveProject(project: StudioProject) {
        val meta = project.videoMetadata ?: return
        val entity = ProjectEntity(
            id = project.id,
            title = project.title,
            videoUriString = meta.uriString,
            videoDisplayName = meta.displayName,
            durationMs = meta.durationMs,
            width = meta.width,
            height = meta.height,
            fileSizeBytes = meta.fileSizeBytes,
            detectedTopic = project.detectedTopic,
            languageModeName = project.languageMode.name,
            subtitlesJson = cueListAdapter.toJson(project.subtitles),
            styleConfigJson = styleAdapter.toJson(project.styleConfig),
            autoZoomIntensityName = project.autoZoomIntensity.name,
            zoomKeyframesJson = zoomListAdapter.toJson(project.zoomKeyframes),
            bRollItemsJson = bRollListAdapter.toJson(project.bRollItems),
            aspectRatioName = project.aspectRatio.name,
            resolutionName = project.resolution.name,
            lastExportedPath = project.lastExportedPath,
            updatedAtMs = System.currentTimeMillis()
        )
        dao.upsertProject(entity)
    }

    suspend fun getProject(id: String): StudioProject? {
        return dao.getProjectById(id)?.toDomain()
    }

    suspend fun deleteProject(id: String) {
        dao.deleteProjectById(id)
    }

    private fun ProjectEntity.toDomain(): StudioProject {
        val cues = runCatching { cueListAdapter.fromJson(subtitlesJson) }.getOrNull().orEmpty()
        val zooms = runCatching { zoomListAdapter.fromJson(zoomKeyframesJson) }.getOrNull().orEmpty()
        val bRolls = runCatching { bRollListAdapter.fromJson(bRollItemsJson) }.getOrNull().orEmpty()
        val style = runCatching { styleAdapter.fromJson(styleConfigJson) }.getOrNull()
            ?: SubtitleStyleConfig()

        return StudioProject(
            id = id,
            title = title,
            videoMetadata = VideoMetadata(
                uriString = videoUriString,
                displayName = videoDisplayName,
                durationMs = durationMs,
                width = width,
                height = height,
                fileSizeBytes = fileSizeBytes
            ),
            detectedTopic = detectedTopic,
            languageMode = runCatching { SubtitleLanguageMode.valueOf(languageModeName) }
                .getOrDefault(SubtitleLanguageMode.SINHALA_ENGLISH_MIXED),
            subtitles = cues,
            styleConfig = style,
            autoZoomIntensity = runCatching { AutoZoomIntensity.valueOf(autoZoomIntensityName) }
                .getOrDefault(AutoZoomIntensity.DYNAMIC_REELS),
            zoomKeyframes = zooms,
            bRollItems = bRolls,
            aspectRatio = runCatching { ExportAspectRatio.valueOf(aspectRatioName) }
                .getOrDefault(ExportAspectRatio.REELS_9_16),
            resolution = runCatching { ExportResolution.valueOf(resolutionName) }
                .getOrDefault(ExportResolution.FHD_1080P),
            lastExportedPath = lastExportedPath,
            updatedAtMs = updatedAtMs
        )
    }
}
