package com.motionstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import com.motionstudio.ui.*

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val connector=MotionStudioConnector(this);connector.createProject();setContent{val ui=remember{mutableStateOf(connector.state().project?.let{connector.state().project!!.toUiState(connector.state().currentTimeUs,connector.state().selectedLayerIds,connector.state().playing)} ?: MotionStudioUiState())};remember{connector.subscribe{ui.value=connector.state().project?.toUiState(connector.state().currentTimeUs,connector.state().selectedLayerIds,connector.state().playing)?:MotionStudioUiState()}};MotionStudioApp(ui.value,MotionStudioUiCallbacks(onCreateProject={connector.createProject()},onSaveProject={connector.saveProject()},onImportUris={connector.importUris(it)},onSelectLayer={connector.selectLayer(it)},onSeek={connector.seek(it)},onPlayPause={connector.playPause()},onSplit={connector.split()},onDeleteSelection={connector.deleteSelected()},onToggleLayerVisibility={connector.toggleVisibility(it)},onToggleLayerLock={connector.toggleLock(it)},onAddLayer={connector.addLayer(com.motionstudio.part1.LayerType.valueOf(it.uppercase()))}))}}
    override fun onDestroy(){super.onDestroy()}
}
