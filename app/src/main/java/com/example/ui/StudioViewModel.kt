package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.KeplerAiService
import com.example.data.ProjectRepository
import com.example.media.MediaHelper
import com.example.media.VideoExporter
import com.example.model.AiTaskState
import com.example.model.AutoZoomIntensity
import com.example.model.BRollDisplayMode
import com.example.model.BRollItem
import com.example.model.ExportAspectRatio
import com.example.model.ExportResolution
import com.example.model.ExportState
import com.example.model.StudioProject
import com.example.model.StudioTab
import com.example.model.SubtitleCue
import com.example.model.SubtitleLanguageMode
import com.example.model.SubtitleStyleConfig
import com.example.model.ZoomKeyframe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "StudioViewModel"
    }

    private val appContext = application.applicationContext
    private val repository = ProjectRepository(appContext)
    private val aiService = KeplerAiService()
    private val exporter = VideoExporter(appContext)

    val savedProjects: StateFlow<List<StudioProject>> = repository.savedProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _activeProject = MutableStateFlow<StudioProject?>(null)
    val activeProject: StateFlow<StudioProject?> = _activeProject.asStateFlow()

    private val _selectedTab = MutableStateFlow(StudioTab.SUBTITLES)
    val selectedTab: StateFlow<StudioTab> = _selectedTab.asStateFlow()

    private val _aiTaskState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiTaskState: StateFlow<AiTaskState> = _aiTaskState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _seekRequestMs = MutableStateFlow<Long?>(null)
    val seekRequestMs: StateFlow<Long?> = _seekRequestMs.asStateFlow()

    fun selectTab(tab: StudioTab) {
        _selectedTab.value = tab
    }

    fun updatePlayhead(ms: Long) {
        _playheadMs.value = ms
    }

    fun updatePlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun requestSeek(ms: Long) {
        val duration = _activeProject.value?.videoMetadata?.durationMs ?: Long.MAX_VALUE
        val clamped = ms.coerceIn(0L, duration)
        _playheadMs.value = clamped
        _seekRequestMs.value = clamped
    }

    fun clearSeekRequest() {
        _seekRequestMs.value = null
    }

    fun dismissAiError() {
        if (_aiTaskState.value is AiTaskState.Error) {
            _aiTaskState.value = AiTaskState.Idle
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    fun importVideo(uri: Uri, onReady: () -> Unit) {
        viewModelScope.launch {
            try {
                val (defLang, defFont, defAspect) = repository.getDefaultSettings()
                val metadata = MediaHelper.extractVideoMetadata(appContext, uri)
                val peaks = MediaHelper.extractWaveformPeaks(appContext, uri)
                val inferredAspect = if (metadata.width > metadata.height) {
                    ExportAspectRatio.YOUTUBE_16_9
                } else {
                    defAspect
                }
                val cleanTitle = metadata.displayName
                    .substringBeforeLast('.')
                    .ifBlank { "Kepler Project" }

                val project = StudioProject(
                    title = cleanTitle,
                    videoMetadata = metadata,
                    languageMode = defLang,
                    styleConfig = SubtitleStyleConfig(fontOption = defFont),
                    aspectRatio = inferredAspect,
                    waveformSamples = peaks
                )
                _activeProject.value = project
                _selectedTab.value = StudioTab.SUBTITLES
                _aiTaskState.value = AiTaskState.Idle
                _exportState.value = ExportState.Idle
                _playheadMs.value = 0L
                repository.saveProject(project)
                onReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import video", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Could not open selected video."
                )
            }
        }
    }

    fun openSavedProject(project: StudioProject, onReady: () -> Unit) {
        viewModelScope.launch {
            val peaks = if (project.waveformSamples.isEmpty() && project.videoMetadata != null) {
                MediaHelper.extractWaveformPeaks(
                    appContext,
                    Uri.parse(project.videoMetadata.uriString)
                )
            } else {
                project.waveformSamples
            }
            _activeProject.value = project.copy(waveformSamples = peaks)
            _selectedTab.value = StudioTab.SUBTITLES
            _aiTaskState.value = AiTaskState.Idle
            _exportState.value = ExportState.Idle
            _playheadMs.value = 0L
            onReady()
        }
    }

    fun deleteSavedProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            if (_activeProject.value?.id == projectId) {
                _activeProject.value = null
            }
        }
    }

    private fun ensureFirebaseReady(): Boolean {
        if (com.google.firebase.FirebaseApp.getApps(appContext).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(appContext)
        }
        if (com.google.firebase.FirebaseApp.getApps(appContext).isEmpty()) {
            _aiTaskState.value = AiTaskState.Error(
                "Firebase is not configured in this APK (missing google-services.json). Build the APK from AI Studio or add GOOGLE_SERVICES_JSON in GitHub Actions."
            )
            return false
        }
        return true
    }

    fun generateAutoSubtitlesAndPlan() {
        val project = _activeProject.value ?: return
        val metadata = project.videoMetadata ?: return
        if (!ensureFirebaseReady()) return

        viewModelScope.launch {
            try {
                _aiTaskState.value = AiTaskState.ExtractingAudio
                val payload = MediaHelper.extractAudioForAi(
                    appContext,
                    Uri.parse(metadata.uriString)
                )

                _aiTaskState.value = AiTaskState.GeneratingSubtitles
                val result = aiService.transcribeAndAnalyzeMedia(
                    mediaBytes = payload.bytes,
                    mimeType = payload.mimeType,
                    durationMs = metadata.durationMs,
                    languageMode = project.languageMode
                )

                updateProject { current ->
                    current.copy(
                        detectedTopic = result.detectedTopic,
                        subtitles = result.cues,
                        zoomKeyframes = result.zoomKeyframes,
                        bRollItems = result.bRollSuggestions
                    )
                }
                _aiTaskState.value = AiTaskState.Idle
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI transcription timed out", e)
                _aiTaskState.value = AiTaskState.Error(
                    "AI transcription timed out after 60 seconds. Please retry with a shorter clip."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI transcription failed", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Speech-to-text transcription failed."
                )
            }
        }
    }

    fun generateAllPendingBRoll() {
        val project = _activeProject.value ?: return
        val targets = project.bRollItems.filter { it.enabled }
        if (targets.isEmpty()) {
            _aiTaskState.value = AiTaskState.Error(
                "Add a B-Roll slot or run AI Subtitles first to plan B-Roll topics."
            )
            return
        }
        if (!ensureFirebaseReady()) return

        viewModelScope.launch {
            try {
                val total = targets.size
                targets.forEachIndexed { idx, item ->
                    _aiTaskState.value = AiTaskState.GeneratingBRoll(
                        current = idx + 1,
                        total = total,
                        label = item.label
                    )
                    val bitmap = aiService.generateBRollImage(
                        prompt = item.prompt,
                        aspectRatio = project.aspectRatio
                    )
                    val savedPath = MediaHelper.saveBitmapToBRollFile(
                        context = appContext,
                        bitmap = bitmap,
                        itemId = "${item.id}_${System.currentTimeMillis()}"
                    )
                    updateProject { current ->
                        current.copy(
                            bRollItems = current.bRollItems.map { existing ->
                                if (existing.id == item.id) existing.copy(imagePath = savedPath) else existing
                            }
                        )
                    }
                }
                _aiTaskState.value = AiTaskState.Idle
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI B-Roll timed out", e)
                _aiTaskState.value = AiTaskState.Error(
                    "AI B-Roll image generation timed out after 60 seconds."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI B-Roll generation failed", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "AI B-Roll generation failed."
                )
            }
        }
    }

    fun generateSingleBRoll(itemId: String) {
        val project = _activeProject.value ?: return
        val item = project.bRollItems.firstOrNull { it.id == itemId } ?: return
        if (!ensureFirebaseReady()) return

        viewModelScope.launch {
            try {
                _aiTaskState.value = AiTaskState.GeneratingBRoll(1, 1, item.label)
                val bitmap = aiService.generateBRollImage(
                    prompt = item.prompt,
                    aspectRatio = project.aspectRatio
                )
                val savedPath = MediaHelper.saveBitmapToBRollFile(
                    context = appContext,
                    bitmap = bitmap,
                    itemId = "${item.id}_${System.currentTimeMillis()}"
                )
                updateProject { current ->
                    current.copy(
                        bRollItems = current.bRollItems.map { existing ->
                            if (existing.id == itemId) existing.copy(imagePath = savedPath, enabled = true)
                            else existing
                        }
                    )
                }
                _aiTaskState.value = AiTaskState.Idle
            } catch (e: TimeoutCancellationException) {
                _aiTaskState.value = AiTaskState.Error("B-Roll image generation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Single B-Roll generation failed", e)
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Could not generate B-Roll image."
                )
            }
        }
    }

    fun attachCustomBRollPhoto(itemId: String, uri: Uri) {
        viewModelScope.launch {
            try {
                val bitmap = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                } ?: throw IllegalStateException("Could not decode selected image.")

                val savedPath = MediaHelper.saveBitmapToBRollFile(
                    context = appContext,
                    bitmap = bitmap,
                    itemId = "${itemId}_custom_${System.currentTimeMillis()}"
                )
                updateProject { current ->
                    current.copy(
                        bRollItems = current.bRollItems.map { existing ->
                            if (existing.id == itemId) existing.copy(imagePath = savedPath, enabled = true)
                            else existing
                        }
                    )
                }
            } catch (e: Exception) {
                _aiTaskState.value = AiTaskState.Error(
                    e.localizedMessage ?: "Unable to load custom B-Roll image."
                )
            }
        }
    }

    fun addBRollSlotAtPlayhead() {
        val project = _activeProject.value ?: return
        val duration = project.videoMetadata?.durationMs ?: 10_000L
        val start = _playheadMs.value.coerceIn(0L, (duration - 1000L).coerceAtLeast(0L))
        val end = (start + 2500L).coerceAtMost(duration)
        val defaultTopic = project.detectedTopic.ifBlank { "cinematic creator studio scene" }
        val newItem = BRollItem(
            startMs = start,
            endMs = end,
            label = "B-Roll ${project.bRollItems.size + 1}",
            prompt = "Visual cutaway illustrating $defaultTopic"
        )
        updateProject { it.copy(bRollItems = (it.bRollItems + newItem).sortedBy { b -> b.startMs }) }
    }

    fun updateBRollPrompt(itemId: String, prompt: String) {
        updateProject { current ->
            current.copy(
                bRollItems = current.bRollItems.map {
                    if (it.id == itemId) it.copy(prompt = prompt) else it
                }
            )
        }
    }

    fun updateBRollDisplayMode(itemId: String, mode: BRollDisplayMode) {
        updateProject { current ->
            current.copy(
                bRollItems = current.bRollItems.map {
                    if (it.id == itemId) it.copy(displayMode = mode) else it
                }
            )
        }
    }

    fun toggleBRollEnabled(itemId: String) {
        updateProject { current ->
            current.copy(
                bRollItems = current.bRollItems.map {
                    if (it.id == itemId) it.copy(enabled = !it.enabled) else it
                }
            )
        }
    }

    fun deleteBRoll(itemId: String) {
        updateProject { current ->
            current.copy(bRollItems = current.bRollItems.filterNot { it.id == itemId })
        }
    }

    fun updateLanguageMode(mode: SubtitleLanguageMode) {
        updateProject { it.copy(languageMode = mode) }
        savePrefs()
    }

    fun updateSubtitleCue(cueId: String, primary: String, secondary: String) {
        updateProject { current ->
            current.copy(
                subtitles = current.subtitles.map {
                    if (it.id == cueId) it.copy(primaryText = primary, secondaryText = secondary) else it
                }
            )
        }
    }

    fun addSubtitleCueAtPlayhead() {
        val project = _activeProject.value ?: return
        val duration = project.videoMetadata?.durationMs ?: 10_000L
        val start = _playheadMs.value.coerceIn(0L, (duration - 800L).coerceAtLeast(0L))
        val end = (start + 2200L).coerceAtMost(duration)
        val newCue = SubtitleCue(
            id = UUID.randomUUID().toString(),
            startMs = start,
            endMs = end,
            primaryText = "නව උපසිරැසිය / New caption",
            secondaryText = ""
        )
        updateProject { it.copy(subtitles = (it.subtitles + newCue).sortedBy { c -> c.startMs }) }
    }

    fun deleteSubtitleCue(cueId: String) {
        updateProject { current ->
            current.copy(subtitles = current.subtitles.filterNot { it.id == cueId })
        }
    }

    fun updateStyleConfig(transform: (SubtitleStyleConfig) -> SubtitleStyleConfig) {
        updateProject { it.copy(styleConfig = transform(it.styleConfig)) }
        savePrefs()
    }

    fun updateAutoZoomIntensity(intensity: AutoZoomIntensity) {
        updateProject { it.copy(autoZoomIntensity = intensity) }
    }

    fun addZoomKeyframeAtPlayhead() {
        val project = _activeProject.value ?: return
        val duration = project.videoMetadata?.durationMs ?: 10_000L
        val start = _playheadMs.value.coerceIn(0L, (duration - 800L).coerceAtLeast(0L))
        val end = (start + 2200L).coerceAtMost(duration)
        val kf = ZoomKeyframe(
            startMs = start,
            endMs = end,
            targetScale = project.autoZoomIntensity.maxScale.coerceAtLeast(1.15f),
            panX = 0f,
            panY = -0.03f,
            label = "Manual Punch-In"
        )
        updateProject { it.copy(zoomKeyframes = (it.zoomKeyframes + kf).sortedBy { z -> z.startMs }) }
    }

    fun deleteZoomKeyframe(id: String) {
        updateProject { current ->
            current.copy(zoomKeyframes = current.zoomKeyframes.filterNot { it.id == id })
        }
    }

    fun updateAspectRatio(aspectRatio: ExportAspectRatio) {
        updateProject { it.copy(aspectRatio = aspectRatio) }
        savePrefs()
    }

    fun updateResolution(resolution: ExportResolution) {
        updateProject { it.copy(resolution = resolution) }
    }

    fun updateExportFlags(
        burnSubtitles: Boolean? = null,
        burnAutoZoom: Boolean? = null,
        burnBRoll: Boolean? = null
    ) {
        updateProject { current ->
            current.copy(
                burnSubtitlesOnExport = burnSubtitles ?: current.burnSubtitlesOnExport,
                burnAutoZoomOnExport = burnAutoZoom ?: current.burnAutoZoomOnExport,
                burnBRollOnExport = burnBRoll ?: current.burnBRollOnExport
            )
        }
    }

    fun startExport() {
        val project = _activeProject.value ?: return
        viewModelScope.launch {
            try {
                _exportState.value = ExportState.Exporting(1, "Preparing export...")
                val outcome = exporter.exportProject(project) { pct, label ->
                    _exportState.value = ExportState.Exporting(pct, label)
                }
                updateProject { it.copy(lastExportedPath = outcome.internalFilePath) }
                _exportState.value = ExportState.Completed(
                    outputFilePath = outcome.internalFilePath,
                    publicUriString = outcome.publicUriString,
                    fileSizeBytes = outcome.fileSizeBytes
                )
            } catch (e: CancellationException) {
                exporter.cancelExport()
                _exportState.value = ExportState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _exportState.value = ExportState.Error(
                    e.localizedMessage ?: "Video export failed."
                )
            }
        }
    }

    fun cancelExport() {
        exporter.cancelExport()
        _exportState.value = ExportState.Idle
    }

    private fun updateProject(transform: (StudioProject) -> StudioProject) {
        val current = _activeProject.value ?: return
        val updated = transform(current).copy(updatedAtMs = System.currentTimeMillis())
        _activeProject.value = updated
        viewModelScope.launch {
            repository.saveProject(updated)
        }
    }

    private fun savePrefs() {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            repository.saveDefaultSettings(
                languageMode = current.languageMode,
                fontOption = current.styleConfig.fontOption,
                aspectRatio = current.aspectRatio
            )
        }
    }
}
