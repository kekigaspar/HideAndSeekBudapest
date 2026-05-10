package com.example.hideseekbudapest

import androidx.lifecycle.ViewModel
import com.example.hideseekbudapest.tools.MapAreaTool
import com.example.hideseekbudapest.tools.ToolParams
import com.example.hideseekbudapest.tools.UnionHelper
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MapToolViewModel : ViewModel() {

    private val _allExclusions = mutableListOf<Feature>()

    // UI State
    private val _mergedGeoJson = MutableStateFlow<String?>(null)
    val mergedGeoJson: StateFlow<String?> = _mergedGeoJson.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _highlightedLine = MutableStateFlow<String?>(null)
    val highlightedLine: StateFlow<String?> = _highlightedLine.asStateFlow()

    private val _disabledLines = MutableStateFlow<Set<String>>(emptySet())
    val disabledLines: StateFlow<Set<String>> = _disabledLines.asStateFlow()

    // Actions the Activity can call
    fun toggleEditMode(editing: Boolean) {
        _isEditing.value = editing
    }

    fun addExclusion(feature: Feature) {
        _allExclusions.add(feature)
        updateMergedGeometry()
    }

    // Generic function to apply ANY tool that follows our contract
    fun <T : ToolParams> applyTool(tool: MapAreaTool<T>, params: T) {
        // Generate the shape
        val newFeature = tool.generateFeature(params)

        // Add it to the list and merge (UnionHelper handles the heavy lifting!)
        _allExclusions.add(newFeature)
        updateMergedGeometry()
    }

    private fun updateMergedGeometry() {
        val mergedFeature = UnionHelper.mergeFeatures(_allExclusions)
        val displayList = listOfNotNull(mergedFeature)
        _mergedGeoJson.value = FeatureCollection.fromFeatures(displayList).toJson()
    }

    fun setHighlightedLine(lineName: String?) {
        _highlightedLine.value = lineName
    }

    fun toggleLineVisibility(lineName: String, isVisible: Boolean) {
        val currentSet = _disabledLines.value.toMutableSet()
        if (isVisible) {
            currentSet.remove(lineName)
        } else {
            currentSet.add(lineName)
        }
        _disabledLines.value = currentSet
    }
}