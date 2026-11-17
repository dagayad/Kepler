package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.firebase.configureAppCheck
import com.example.ui.StudioViewModel
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.KeplerTheme
import com.example.ui.theme.StudioBackground

private const val ROUTE_HOME = "home"
private const val ROUTE_EDITOR = "editor"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI or ViewModel
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            KeplerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = StudioBackground
                ) {
                    val navController = rememberNavController()
                    val studioViewModel: StudioViewModel = viewModel()

                    val savedProjects by studioViewModel.savedProjects.collectAsStateWithLifecycle()
                    val activeProject by studioViewModel.activeProject.collectAsStateWithLifecycle()
                    val selectedTab by studioViewModel.selectedTab.collectAsStateWithLifecycle()
                    val aiTaskState by studioViewModel.aiTaskState.collectAsStateWithLifecycle()
                    val exportState by studioViewModel.exportState.collectAsStateWithLifecycle()
                    val playheadMs by studioViewModel.playheadMs.collectAsStateWithLifecycle()
                    val isPlaying by studioViewModel.isPlaying.collectAsStateWithLifecycle()
                    val seekRequestMs by studioViewModel.seekRequestMs.collectAsStateWithLifecycle()

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME
                    ) {
                        composable(ROUTE_HOME) {
                            HomeScreen(
                                savedProjects = savedProjects,
                                aiTaskState = aiTaskState,
                                onImportVideoUri = { uri ->
                                    studioViewModel.importVideo(uri) {
                                        navController.navigate(ROUTE_EDITOR)
                                    }
                                },
                                onOpenProject = { project ->
                                    studioViewModel.openSavedProject(project) {
                                        navController.navigate(ROUTE_EDITOR)
                                    }
                                },
                                onDeleteProject = studioViewModel::deleteSavedProject,
                                onDismissError = studioViewModel::dismissAiError
                            )
                        }

                        composable(ROUTE_EDITOR) {
                            val currentProject = activeProject
                            if (currentProject != null) {
                                EditorScreen(
                                    project = currentProject,
                                    selectedTab = selectedTab,
                                    aiTaskState = aiTaskState,
                                    exportState = exportState,
                                    playheadMs = playheadMs,
                                    isPlaying = isPlaying,
                                    seekRequestMs = seekRequestMs,
                                    viewModel = studioViewModel,
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
