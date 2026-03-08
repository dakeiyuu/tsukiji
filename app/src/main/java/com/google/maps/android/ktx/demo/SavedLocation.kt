package com.google.maps.android.ktx.demo

import com.google.android.gms.maps.model.LatLng

data class SavedLocation(
    val position: LatLng,
    val title: String,
    val description: String,
    val category: String,
    val score: Double,
    val timestamp: Long
)