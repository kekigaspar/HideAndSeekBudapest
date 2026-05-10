package com.example.hideseekbudapest

import androidx.lifecycle.ViewModel
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

    // Actions the Activity can call
    fun toggleEditMode(editing: Boolean) {
        _isEditing.value = editing
    }

    fun addExclusion(feature: Feature) {
        _allExclusions.add(feature)
        updateMergedGeometry()
    }

    private fun updateMergedGeometry() {
        val mergedFeature = UnionHelper.mergeFeatures(_allExclusions)
        val displayList = listOfNotNull(mergedFeature)
        _mergedGeoJson.value = FeatureCollection.fromFeatures(displayList).toJson()
    }
}