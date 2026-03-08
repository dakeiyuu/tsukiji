package com.google.maps.android.ktx.demo.api

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.ktx.demo.model.MyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PlacesApiHelper(private val apiKey: String) {

    companion object {
        private const val TAG = "PlacesApiHelper"
        private const val PLACES_API_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
    }

    /**
     * Obtiene lugares de múltiples ciudades importantes del mundo
     */
    suspend fun getGlobalPlaces(): List<MyItem> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<MyItem>()

        // Ciudades principales del mundo
        val majorCities = listOf(
            LatLng(40.7128, -74.0060) to "Nueva York",
            LatLng(51.5074, -0.1278) to "Londres",
            LatLng(35.6762, 139.6503) to "Tokio",
            LatLng(48.8566, 2.3522) to "París",
            LatLng(19.4326, -99.1332) to "Ciudad de México",
            LatLng(25.7617, -80.1918) to "Miami",
            LatLng(-23.5505, -46.6333) to "São Paulo",
            LatLng(55.7558, 37.6173) to "Moscú",
            LatLng(1.3521, 103.8198) to "Singapur",
            LatLng(37.7749, -122.4194) to "San Francisco"
        )

        // Tipos de lugares que queremos buscar
        val placeTypes = listOf(
            "restaurant",
            "cafe",
            "store",
            "shopping_mall",
            "bar",
            "gym",
            "hospital",
            "school"
        )

        // Para cada ciudad, hacer búsqueda
        majorCities.forEach { (location, cityName) ->
            try {
                // Buscar lugares cercanos
                val places = searchNearbyPlaces(location, radius = 5000, type = placeTypes.random())
                allItems.addAll(places)
                Log.d(TAG, "Obtenidos ${places.size} lugares de $cityName")
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo lugares de $cityName: ${e.message}")
            }
        }

        allItems
    }

    /**
     * Busca lugares cercanos a una ubicación usando Google Places API
     */
    private suspend fun searchNearbyPlaces(
        location: LatLng,
        radius: Int = 5000,
        type: String = "restaurant"
    ): List<MyItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MyItem>()

        try {
            val locationParam = "${location.latitude},${location.longitude}"
            val urlString = "$PLACES_API_URL?location=$locationParam&radius=$radius&type=$type&key=$apiKey"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.getString("status") == "OK") {
                    val results = jsonObject.getJSONArray("results")

                    for (i in 0 until results.length()) {
                        val place = results.getJSONObject(i)
                        val geometry = place.getJSONObject("geometry")
                        val locationObj = geometry.getJSONObject("location")

                        val lat = locationObj.getDouble("lat")
                        val lng = locationObj.getDouble("lng")
                        val name = place.optString("name", "Negocio")
                        val types = place.optJSONArray("types")
                        val category = mapPlaceTypeToCategory(types?.optString(0) ?: type)

                        items.add(
                            MyItem(
                                LatLng(lat, lng),
                                name,
                                "Categoría: $category",
                                category
                            )
                        )
                    }
                } else {
                    Log.w(TAG, "API returned status: ${jsonObject.getString("status")}")
                }
            } else {
                Log.e(TAG, "HTTP error: $responseCode")
            }

            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error en searchNearbyPlaces: ${e.message}", e)
        }

        items
    }

    /**
     * Mapea los tipos de Google Places a nuestras categorías
     */
    private fun mapPlaceTypeToCategory(placeType: String): String {
        return when {
            placeType.contains("restaurant") || placeType.contains("cafe") ||
                    placeType.contains("food") || placeType.contains("bar") -> "restaurant"

            placeType.contains("store") || placeType.contains("shop") ||
                    placeType.contains("mall") || placeType.contains("clothing") -> "retail"

            placeType.contains("movie") || placeType.contains("amusement") ||
                    placeType.contains("night_club") || placeType.contains("casino") -> "entertainment"

            else -> "service"
        }
    }
}