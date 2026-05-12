package com.google.maps.android.ktx.demo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.ktx.awaitMap
import com.google.maps.android.ktx.awaitMapLoad
import com.google.maps.android.ktx.cameraMoveStartedEvents
import com.google.maps.android.ktx.cameraIdleEvents
import com.google.maps.android.ktx.demo.model.MyItem
import kotlinx.coroutines.launch
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private var heatmapProvider: HeatmapTileProvider? = null
    private var allBusinessLocations = mutableListOf<LatLng>()
    private var allBusinessItems = mutableListOf<MyItem>()
    private var heatmapOverlay: TileOverlay? = null
    private var googleMap: GoogleMap? = null
    private var suggestionMarkers = mutableListOf<Marker>()

    private var currentFilterCategory = "all"

    private val businessCategories = mapOf(
        "all" to "Todos",
        "restaurant" to "Restaurantes",
        "retail" to "Tiendas",
        "service" to "Servicios",
        "entertainment" to "Entretenimiento"
    )

    private val newBusinessCategories = mapOf(
        "restaurant" to "🍽️ Restaurante",
        "retail" to "🛍️ Tienda",
        "service" to "🔧 Servicio",
        "entertainment" to "🎬 Entretenimiento"
    )

    private lateinit var guardadosFragment: GuardadosFragment
    private lateinit var propiedadesFragment: PropiedadesFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isRestore = savedInstanceState != null

        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            setContentView(R.layout.activity_main)

            val container = findViewById<ConstraintLayout>(R.id.main_container)
            applyInsets(container)

            setupBottomNavigation()
            setupFilterFab()

            if (BuildConfig.MAPS_API_KEY.isEmpty()) {
                Toast.makeText(this, "Falta API key de Google Maps", Toast.LENGTH_LONG).show()
                Log.e(TAG, "API Key está vacía")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en onCreate inicial: ${e.message}", e)
            Toast.makeText(this, "Error al inicializar: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment

        if (mapFragment == null) {
            Log.e(TAG, "MapFragment no encontrado")
            Toast.makeText(this, "Error: MapFragment no encontrado", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                    googleMap = mapFragment.awaitMap()
                    Log.d(TAG, "Mapa inicializado correctamente")

                    if (!isRestore) {
                        googleMap?.awaitMapLoad()
                        Log.d(TAG, "Mapa cargado completamente")
                    }

                    try {
                        loadBusinessDataFallback()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cargando datos: ${e.message}", e)
                        Toast.makeText(
                            this@MainActivity,
                            "Error cargando datos, generando datos de muestra",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (allBusinessLocations.isEmpty()) {
                            generateSampleBusinessData()
                        }
                    }

                    Log.d(TAG, "Total de ubicaciones cargadas: ${allBusinessLocations.size}")

                    googleMap?.let { map ->
                        if (allBusinessLocations.isNotEmpty()) {
                            setupHeatmap(map, allBusinessLocations)
                            centerMapOnData(map)
                            setupFilterChips(map)
                            setupNewBusinessChips()
                            setupFindLocationButton()
                            setupHeatmapZoomListener(map)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "No se pudieron cargar datos",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    launch {
                        googleMap?.cameraMoveStartedEvents()?.collect {
                            Log.d(TAG, "Camera moved - reason $it")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en inicialización del mapa: ${e.message}", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error al cargar el mapa: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupHeatmapZoomListener(map: GoogleMap) {
        lifecycleScope.launch {
            map.cameraIdleEvents().collect {
                val zoom = map.cameraPosition.zoom
                val currentLocations = if (currentFilterCategory == "all") {
                    allBusinessLocations
                } else {
                    allBusinessItems
                        .filter { it.category == currentFilterCategory }
                        .map { it.position }
                }
                rebuildHeatmap(map, currentLocations, zoom)
            }
        }
    }

    private fun setupHeatmap(map: GoogleMap, locations: List<LatLng>) {
        heatmapOverlay?.remove()
        heatmapOverlay = null
        heatmapProvider = null
        if (locations.isEmpty()) return

        try {
            val initialZoom = maxOf(map.cameraPosition.zoom, 5f)
            rebuildHeatmap(map, locations, initialZoom)
        } catch (e: Exception) {
            Log.e(TAG, "Error creando el mapa de calor: ${e.message}", e)
        }
    }

    private fun rebuildHeatmap(map: GoogleMap, locations: List<LatLng>, zoom: Float) {
        heatmapOverlay?.remove()
        heatmapOverlay = null
        if (locations.isEmpty()) return

        val effectiveZoom = maxOf(zoom, 2f)

        val radius = when {
            effectiveZoom >= 16f -> 12
            effectiveZoom >= 14f -> 18
            effectiveZoom >= 12f -> 28
            effectiveZoom >= 10f -> 38
            effectiveZoom >= 8f  -> 45
            effectiveZoom >= 5f  -> 50
            else                 -> 55
        }

        val maxIntensity = when {
            effectiveZoom >= 16f -> 3.0
            effectiveZoom >= 14f -> 4.0
            effectiveZoom >= 12f -> 6.0
            effectiveZoom >= 10f -> 12.0
            effectiveZoom >= 8f  -> 25.0
            effectiveZoom >= 5f  -> 50.0
            else                 -> 80.0
        }

        val colors = intArrayOf(
            Color.argb(0,   0, 210,   0),
            Color.argb(200, 30, 220,  30),
            Color.argb(215, 220, 220,  0),
            Color.argb(230, 255, 130,  0),
            Color.argb(255, 210,  20,  0)
        )
        val startPoints = floatArrayOf(0.0f, 0.20f, 0.50f, 0.75f, 1.0f)
        val gradient = Gradient(colors, startPoints)

        try {
            val provider = HeatmapTileProvider.Builder()
                .data(locations)
                .gradient(gradient)
                .radius(radius)
                .opacity(0.88)
                .maxIntensity(maxIntensity)
                .build()

            heatmapProvider = provider
            heatmapOverlay = map.addTileOverlay(TileOverlayOptions().tileProvider(provider))
            Log.d(TAG, "Heatmap creado con zoom=$effectiveZoom, radius=$radius, ${locations.size} puntos")
        } catch (e: Exception) {
            Log.e(TAG, "Error en rebuildHeatmap: ${e.message}", e)
        }
    }

    private fun setupFragments() {
        guardadosFragment = GuardadosFragment()
        propiedadesFragment = PropiedadesFragment()
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()

        findViewById<FrameLayout>(R.id.fragment_container).visibility = View.VISIBLE
        findViewById<ConstraintLayout>(R.id.map_container).visibility = View.GONE
    }

    private fun showMap() {
        findViewById<FrameLayout>(R.id.fragment_container).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.map_container).visibility = View.VISIBLE
    }

    private fun setupFilterFab() {
        val fab = findViewById<View>(R.id.fab_filters)
        val filterCard = findViewById<View>(R.id.filter_card)

        fab.setOnClickListener {
            if (filterCard.visibility == View.VISIBLE) {
                filterCard.visibility = View.GONE
            } else {
                filterCard.visibility = View.VISIBLE
            }
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        setupFragments()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> {
                    showMap()
                    true
                }
                R.id.nav_saved -> {
                    showFragment(guardadosFragment)
                    true
                }
                R.id.nav_propiedades -> {
                    showFragment(propiedadesFragment)
                    true
                }
                else -> false
            }
        }

        bottomNav.selectedItemId = R.id.nav_map
    }

    fun saveCurrentLocation(title: String, category: String, score: Double) {
        googleMap?.cameraPosition?.target?.let { location ->
            val savedLocation = SavedLocation(
                position = location,
                title = title,
                description = "Puntuación: $score",
                category = category,
                score = score,
                timestamp = System.currentTimeMillis()
            )
            guardadosFragment.addSavedLocation(savedLocation)
            Toast.makeText(this, "Ubicación guardada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSaveDialog(title: String, category: String, score: Double) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("Guardar ubicación")
        builder.setMessage("¿Deseas guardar esta ubicación en tus favoritos?")

        builder.setPositiveButton("Guardar") { dialog, _ ->
            saveCurrentLocation(title, category, score)
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun setupNewBusinessChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_new_business)

        newBusinessCategories.forEach { (key, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                tag = key
                setChipBackgroundColorResource(android.R.color.white)
                setTextColor(getColor(android.R.color.darker_gray))
                chipStrokeWidth = 2f
                chipStrokeColor = getColorStateList(android.R.color.darker_gray)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        setChipBackgroundColorResource(android.R.color.holo_orange_light)
                        setTextColor(getColor(android.R.color.white))
                    } else {
                        setChipBackgroundColorResource(android.R.color.white)
                        setTextColor(getColor(android.R.color.darker_gray))
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupFindLocationButton() {
        val button = findViewById<MaterialButton>(R.id.btn_find_location)
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_new_business)

        button.setOnClickListener {
            val selectedChipId = chipGroup.checkedChipId
            if (selectedChipId == View.NO_ID) {
                Toast.makeText(this, "Por favor selecciona un tipo de negocio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedChip = chipGroup.findViewById<Chip>(selectedChipId)
            val category = selectedChip?.tag as? String ?: return@setOnClickListener

            findBestLocations(category)
        }
    }

    private fun findBestLocations(category: String) {
        googleMap?.let { map ->
            suggestionMarkers.forEach { it.remove() }
            suggestionMarkers.clear()
            setupCustomInfoWindow(map)

            Toast.makeText(this, "Analizando mejores ubicaciones para $category en esta área...", Toast.LENGTH_SHORT).show()

            val visibleRegion = map.projection.visibleRegion
            val bounds = visibleRegion.latLngBounds

            Log.d(TAG, "Buscando en área visible: ${bounds.southwest} a ${bounds.northeast}")

            val visibleBusinesses = allBusinessItems.filter { item ->
                bounds.contains(item.position)
            }

            val sameCategory = visibleBusinesses.filter { it.category == category }

            if (visibleBusinesses.isEmpty()) {
                Toast.makeText(this, "No hay negocios en esta área. Mueve el mapa a otra zona.", Toast.LENGTH_SHORT).show()
                return
            }

            if (sameCategory.isEmpty()) {
                Toast.makeText(this, "No hay negocios de tipo $category en esta área. ¡Oportunidad perfecta!", Toast.LENGTH_LONG).show()
                val centerLat = (bounds.northeast.latitude + bounds.southwest.latitude) / 2
                val centerLng = (bounds.northeast.longitude + bounds.southwest.longitude) / 2
                val centerLocation = LatLng(centerLat, centerLng)

                val marker = map.addMarker(
                    MarkerOptions()
                        .position(centerLocation)
                        .title("TOCAME PARA GUARDARME\nUbicación Sugerida #1")
                        .snippet("Excelente oportunidad: No hay competencia en esta área\nÍndice de oportunidad: 100.0")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )
                marker?.tag = LocationSuggestion(centerLocation, 100.0, "Excelente oportunidad: No hay competencia en esta área")
                marker?.let { suggestionMarkers.add(it) }
                marker?.showInfoWindow()

                map.setOnInfoWindowClickListener { clickedMarker ->
                    val suggestion = clickedMarker.tag as? LocationSuggestion
                    suggestion?.let {
                        showSaveDialog(clickedMarker.title ?: "Ubicación", category, it.score)
                    }
                }
                return
            }

            val bestLocations = findOptimalLocationsInBounds(bounds, sameCategory, visibleBusinesses)

            if (bestLocations.isEmpty()) {
                Toast.makeText(this, "No se encontraron ubicaciones óptimas", Toast.LENGTH_SHORT).show()
                return
            }

            bestLocations.take(5).forEachIndexed { index, location ->
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(location.position)
                        .title("TOCAME PARA GUARDARME\nUbicación Sugerida #${index + 1}")
                        .snippet("${location.reason}\nÍndice de oportunidad: ${String.format("%.1f", location.score)}")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                )
                marker?.tag = location
                marker?.let { suggestionMarkers.add(it) }
            }

            map.setOnInfoWindowClickListener { clickedMarker ->
                val suggestion = clickedMarker.tag as? LocationSuggestion
                suggestion?.let {
                    saveCurrentLocation(clickedMarker.title ?: "Ubicación", category, it.score)
                }
            }

            val bestLocation = bestLocations.first()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(bestLocation.position, 13f))
            suggestionMarkers.firstOrNull()?.showInfoWindow()

            Toast.makeText(this, "Se encontraron ${bestLocations.size} ubicaciones óptimas. Mostrando las mejores 5.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupCustomInfoWindow(map: GoogleMap) {
        map.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null

            override fun getInfoContents(marker: Marker): View {
                val view = layoutInflater.inflate(R.layout.custom_info_window, null)
                val titleView = view.findViewById<TextView>(R.id.title)
                val snippetView = view.findViewById<TextView>(R.id.snippet)
                titleView.text = marker.title
                snippetView.text = marker.snippet
                return view
            }
        })
    }

    private fun findOptimalLocationsInBounds(
        bounds: LatLngBounds,
        sameCategory: List<MyItem>,
        allBusinesses: List<MyItem>
    ): List<LocationSuggestion> {
        if (allBusinesses.isEmpty()) return emptyList()

        val latRange = bounds.northeast.latitude - bounds.southwest.latitude
        val lngRange = bounds.northeast.longitude - bounds.southwest.longitude

        val random = java.util.Random()
        val rawCandidates = mutableListOf<LocationSuggestion>()

        repeat(200) {
            val lat = bounds.southwest.latitude + random.nextDouble() * latRange
            val lng = bounds.southwest.longitude + random.nextDouble() * lngRange
            val candidate = LatLng(lat, lng)
            val score = calculateLocationScore(candidate, sameCategory, allBusinesses)
            if (score > 5) {
                rawCandidates.add(
                    LocationSuggestion(
                        position = candidate,
                        score = score,
                        reason = generateReason(score, sameCategory.size)
                    )
                )
            }
        }

        val minSep = 0.008
        val result = mutableListOf<LocationSuggestion>()
        for (c in rawCandidates.sortedByDescending { it.score }) {
            val tooClose = result.any { existing ->
                abs(existing.position.latitude  - c.position.latitude)  < minSep &&
                        abs(existing.position.longitude - c.position.longitude) < minSep
            }
            if (!tooClose) result.add(c)
            if (result.size >= 5) break
        }
        return result
    }

    private fun calculateLocationScore(
        location: LatLng,
        sameCategory: List<MyItem>,
        allBusinesses: List<MyItem>
    ): Double {
        val nearestCompetitor = sameCategory.minOfOrNull {
            calculateDistance(location, it.position)
        } ?: Double.MAX_VALUE

        val nearestBusiness = allBusinesses.minOfOrNull {
            calculateDistance(location, it.position)
        } ?: Double.MAX_VALUE

        val nearbyBusinessCount = allBusinesses.count {
            calculateDistance(location, it.position) < 2.0
        }

        val nearbyCompetitors = sameCategory.count {
            calculateDistance(location, it.position) < 1.0
        }

        var score = 0.0
        score += min(nearestCompetitor * 10, 50.0)
        score += max(30.0 - nearestBusiness * 5, 0.0)
        score += min(nearbyBusinessCount * 2.0, 20.0)
        score -= nearbyCompetitors * 10.0

        return max(score, 0.0)
    }

    private fun calculateDistance(pos1: LatLng, pos2: LatLng): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(pos2.latitude - pos1.latitude)
        val dLng = Math.toRadians(pos2.longitude - pos1.longitude)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(pos1.latitude)) *
                cos(Math.toRadians(pos2.latitude)) *
                sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun generateReason(score: Double, totalCompetitors: Int): String {
        return when {
            score > 70 -> "Excelente ubicación: Baja competencia, alta actividad comercial"
            score > 50 -> "Buena ubicación: Balance entre competencia y tráfico"
            score > 30 -> "Ubicación aceptable: Zona emergente con potencial"
            else -> "Ubicación con oportunidades de crecimiento"
        }
    }

    private fun loadBusinessDataFallback() {
        Log.d(TAG, "Iniciando carga de datos...")
        try {
            generateSampleBusinessData()
            if (allBusinessLocations.isEmpty()) {
                Toast.makeText(this, "No hay datos para mostrar", Toast.LENGTH_LONG).show()
            } else {
                Log.d(TAG, "Total de negocios cargados: ${allBusinessLocations.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en loadBusinessDataFallback: ${e.message}", e)
            Toast.makeText(this, "Error al cargar datos: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun generateSampleBusinessData() {
        val random = java.util.Random(42)
        val categories = listOf("restaurant", "retail", "service", "entertainment")

        data class UrbanZone(
            val name: String,
            val minLat: Double, val maxLat: Double,
            val minLng: Double, val maxLng: Double,
            val count: Int
        )

        val zones = listOf(
            UrbanZone("MTY Centro/Macroplaza",   25.664, 25.675, -100.322, -100.305, 28),
            UrbanZone("MTY Obispado",            25.672, 25.682, -100.358, -100.335, 18),
            UrbanZone("MTY Mitras Centro",       25.700, 25.712, -100.370, -100.348, 16),
            UrbanZone("MTY Anáhuac",             25.712, 25.728, -100.368, -100.342, 13),
            UrbanZone("MTY Roma Norte",          25.682, 25.695, -100.312, -100.285, 15),
            UrbanZone("MTY Chepevera",           25.678, 25.692, -100.295, -100.270, 13),
            UrbanZone("MTY Contry",              25.638, 25.652, -100.290, -100.265, 12),
            UrbanZone("MTY Del Valle",           25.648, 25.662, -100.272, -100.248, 11),
            UrbanZone("MTY Cumbres 1-2",         25.738, 25.752, -100.395, -100.368, 12),
            UrbanZone("MTY Cumbres 3-4",         25.752, 25.768, -100.388, -100.358, 10),
            UrbanZone("MTY Linda Vista",         25.722, 25.738, -100.345, -100.315, 12),
            UrbanZone("MTY Av. Constitución E",  25.655, 25.665, -100.318, -100.292, 14),
            UrbanZone("MTY Av. Constitución O",  25.655, 25.665, -100.348, -100.318, 12),
            UrbanZone("MTY Lomas",               25.685, 25.700, -100.338, -100.312, 11),
            UrbanZone("MTY Colonia del Norte",   25.710, 25.724, -100.318, -100.290, 10),
            UrbanZone("MTY Fierro/Independencia",25.665, 25.678, -100.278, -100.252, 13),
            UrbanZone("MTY Centro Sur",          25.655, 25.668, -100.310, -100.285, 12),
            UrbanZone("San Pedro GG Centro",     25.648, 25.662, -100.420, -100.388, 22),
            UrbanZone("San Pedro GG Sur",        25.628, 25.645, -100.428, -100.395, 13),
            UrbanZone("San Pedro Valle Oriente", 25.645, 25.660, -100.370, -100.342, 18),
            UrbanZone("San Pedro Carretera",     25.660, 25.672, -100.392, -100.362, 14),
            UrbanZone("Guadalupe Centro",        25.668, 25.685, -100.258, -100.228, 16),
            UrbanZone("Guadalupe Av. Lincoln",   25.688, 25.705, -100.248, -100.215, 13),
            UrbanZone("Guadalupe Las Puentes",   25.658, 25.672, -100.235, -100.202, 10),
            UrbanZone("San Nicolás Centro",      25.728, 25.748, -100.312, -100.278, 15),
            UrbanZone("San Nicolás UANL",        25.720, 25.735, -100.282, -100.248, 12),
            UrbanZone("San Nicolás Nogalar",     25.748, 25.762, -100.295, -100.262, 10),
            UrbanZone("Apodaca Centro",          25.775, 25.795, -100.200, -100.162, 12),
            UrbanZone("Apodaca Zona Industrial", 25.758, 25.778, -100.178, -100.138, 9),
            UrbanZone("Apodaca Portal",          25.792, 25.812, -100.178, -100.142, 8),
            UrbanZone("Escobedo Centro",         25.790, 25.812, -100.338, -100.308, 10),
            UrbanZone("Escobedo Mitras Norte",   25.772, 25.792, -100.312, -100.278, 9),
            UrbanZone("Santa Catarina",          25.668, 25.692, -100.472, -100.435, 10),
            UrbanZone("García NL",               25.808, 25.832, -100.595, -100.558, 7),
            UrbanZone("Juárez NL",               25.648, 25.668, -100.110, -100.072, 8),
            UrbanZone("Cadereyta",               25.578, 25.602, -99.995, -99.958, 6),
            UrbanZone("Monterrey Sur/Mezquital",  25.618, 25.638, -100.310, -100.278, 9),
            UrbanZone("MTY Nuevo Sur/Paseo",     25.605, 25.622, -100.358, -100.325, 10),
            UrbanZone("Monterrey Huinala",       25.798, 25.818, -100.148, -100.112, 7),
            UrbanZone("Saltillo Centro",         25.415, 25.432, -101.008, -100.978, 16),
            UrbanZone("Saltillo Zona Rosa",      25.432, 25.448, -101.002, -100.968, 11),
            UrbanZone("Saltillo Norte",          25.450, 25.470, -100.998, -100.960, 9),
            UrbanZone("Saltillo Sur/Tecnológico",25.388, 25.410, -100.992, -100.958, 8),
            UrbanZone("Saltillo Oriente",        25.408, 25.428, -100.955, -100.918, 6),
            UrbanZone("CDMX Centro Histórico",   19.424, 19.442, -99.145, -99.118, 24),
            UrbanZone("CDMX Polanco",            19.427, 19.440, -99.212, -99.185, 20),
            UrbanZone("CDMX Roma/Condesa",       19.408, 19.425, -99.178, -99.155, 18),
            UrbanZone("CDMX Insurgentes Sur",    19.368, 19.392, -99.178, -99.155, 15),
            UrbanZone("CDMX Coyoacán",           19.340, 19.360, -99.175, -99.150, 13),
            UrbanZone("CDMX Xochimilco",         19.255, 19.278, -99.112, -99.082, 8),
            UrbanZone("CDMX Tlalpan",            19.288, 19.312, -99.185, -99.155, 7),
            UrbanZone("CDMX Iztapalapa",         19.355, 19.382, -99.085, -99.048, 12),
            UrbanZone("CDMX Azcapotzalco",       19.478, 19.498, -99.195, -99.165, 10),
            UrbanZone("CDMX Gustavo A. Madero",  19.488, 19.515, -99.118, -99.082, 11),
            UrbanZone("Ecatepec",                19.592, 19.622, -99.072, -99.032, 10),
            UrbanZone("Neza",                    19.390, 19.415, -99.012, -98.975, 9),
            UrbanZone("Tlalnepantla",            19.528, 19.552, -99.218, -99.185, 10),
            UrbanZone("Naucalpan",               19.468, 19.492, -99.255, -99.218, 9),
            UrbanZone("Midtown Manhattan",       40.748, 40.763, -74.000, -73.972, 24),
            UrbanZone("Lower Manhattan",         40.700, 40.718, -74.020, -73.998, 18),
            UrbanZone("Upper West Side",         40.775, 40.792, -73.990, -73.968, 14),
            UrbanZone("Harlem",                  40.808, 40.828, -73.960, -73.932, 11),
            UrbanZone("Brooklyn Downtown",       40.688, 40.702, -73.995, -73.970, 15),
            UrbanZone("Brooklyn Williamsburg",   40.708, 40.722, -73.968, -73.940, 13),
            UrbanZone("Flushing Queens",         40.756, 40.768, -73.840, -73.820, 12),
            UrbanZone("Astoria Queens",          40.768, 40.782, -73.942, -73.918, 10),
            UrbanZone("Bronx",                   40.838, 40.858, -73.930, -73.900, 9),
            UrbanZone("Staten Island",           40.628, 40.645, -74.092, -74.065, 7),
            UrbanZone("Downtown LA",             34.038, 34.056, -118.268, -118.238, 22),
            UrbanZone("Hollywood",               34.090, 34.108, -118.340, -118.310, 16),
            UrbanZone("Koreatown LA",            34.055, 34.068, -118.320, -118.295, 13),
            UrbanZone("Santa Monica",            34.009, 34.025, -118.508, -118.480, 12),
            UrbanZone("Culver City",             34.018, 34.032, -118.408, -118.380, 10),
            UrbanZone("Burbank",                 34.178, 34.198, -118.328, -118.298, 9),
            UrbanZone("Long Beach",              33.768, 33.790, -118.212, -118.175, 9),
            UrbanZone("Pasadena",                34.138, 34.158, -118.158, -118.125, 8),
            UrbanZone("Inglewood",               33.958, 33.978, -118.368, -118.338, 8),
            UrbanZone("Compton",                 33.888, 33.908, -118.242, -118.212, 7),
            UrbanZone("City of London",          51.508, 51.520, -0.100, -0.070, 22),
            UrbanZone("West End/Soho",           51.510, 51.520, -0.145, -0.118, 20),
            UrbanZone("Canary Wharf",            51.498, 51.510,  0.005,  0.032, 16),
            UrbanZone("Shoreditch/Hackney",      51.520, 51.535, -0.085, -0.058, 14),
            UrbanZone("Camden",                  51.535, 51.548, -0.155, -0.128, 12),
            UrbanZone("Brixton/Clapham",         51.455, 51.470, -0.122, -0.092, 10),
            UrbanZone("Stratford/Westfield",     51.540, 51.555,  0.002,  0.028, 10),
            UrbanZone("Croydon",                 51.370, 51.390, -0.108, -0.075, 7),
            UrbanZone("Wimbledon",               51.418, 51.435, -0.215, -0.185, 7),
            UrbanZone("Ealing",                  51.508, 51.525, -0.318, -0.288, 7),
            UrbanZone("Paris Centro 1-4",        48.848, 48.862,  2.338,  2.365, 24),
            UrbanZone("Champs-Elysees 8e",       48.865, 48.878,  2.290,  2.318, 18),
            UrbanZone("Montmartre 18e",          48.878, 48.893,  2.330,  2.360, 14),
            UrbanZone("Marais 3-4e",             48.855, 48.868,  2.348,  2.372, 15),
            UrbanZone("La Defense",              48.890, 48.902,  2.220,  2.252, 12),
            UrbanZone("Bastille 11e",            48.850, 48.862,  2.368,  2.392, 12),
            UrbanZone("Vincennes/Nation",        48.842, 48.858,  2.418,  2.448, 9),
            UrbanZone("Saint-Denis",             48.928, 48.948,  2.348,  2.378, 8),
            UrbanZone("Boulogne-Billancourt",    48.828, 48.845,  2.228,  2.258, 8),
            UrbanZone("Levallois-Perret",        48.892, 48.908,  2.278,  2.308, 7),
            UrbanZone("Shinjuku",                35.685, 35.698, 139.695, 139.715, 28),
            UrbanZone("Shibuya/Harajuku",        35.655, 35.668, 139.695, 139.718, 22),
            UrbanZone("Akihabara/Ueno",          35.698, 35.712, 139.768, 139.788, 18),
            UrbanZone("Ikebukuro",               35.726, 35.738, 139.705, 139.725, 16),
            UrbanZone("Ginza/Tsukiji",           35.668, 35.680, 139.758, 139.778, 20),
            UrbanZone("Asakusa",                 35.710, 35.722, 139.788, 139.808, 14),
            UrbanZone("Shinagawa",               35.622, 35.638, 139.728, 139.748, 13),
            UrbanZone("Yokohama",                35.438, 35.455, 139.632, 139.658, 12),
            UrbanZone("Osaka Namba",             34.658, 34.672, 135.498, 135.518, 20),
            UrbanZone("Osaka Umeda",             34.698, 34.712, 135.488, 135.508, 18)
        )

        var totalGenerated = 0
        zones.forEach { zone ->
            repeat(zone.count) {
                val lat = zone.minLat + random.nextDouble() * (zone.maxLat - zone.minLat)
                val lng = zone.minLng + random.nextDouble() * (zone.maxLng - zone.minLng)
                val location = LatLng(lat, lng)

                allBusinessLocations.add(location)
                val category = categories[random.nextInt(categories.size)]
                allBusinessItems.add(
                    MyItem(location, "Negocio en ${zone.name}", "Categoría: $category", category)
                )
                totalGenerated++
            }
        }
        Log.d(TAG, "Generados $totalGenerated negocios en ${zones.size} zonas urbanas")
    }

    private fun centerMapOnData(map: GoogleMap) {
        if (allBusinessLocations.isEmpty()) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2F))
            return
        }
        try {
            val boundsBuilder = LatLngBounds.Builder()
            allBusinessLocations.forEach { boundsBuilder.include(it) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
        } catch (e: Exception) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2F))
        }
    }

    private fun setupFilterChips(map: GoogleMap) {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_filters)

        businessCategories.forEach { (key, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = key == "all"
                tag = key
                setChipBackgroundColorResource(android.R.color.white)
                setTextColor(getColor(android.R.color.darker_gray))
                chipStrokeWidth = 2f
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        setChipBackgroundColorResource(android.R.color.holo_orange_light)
                        setTextColor(getColor(android.R.color.white))
                    } else {
                        setChipBackgroundColorResource(android.R.color.white)
                        setTextColor(getColor(android.R.color.darker_gray))
                    }
                }
            }
            chipGroup.addView(chip)
        }

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                group.check(group.getChildAt(0).id)
                return@setOnCheckedStateChangeListener
            }
            val selectedChip = group.findViewById<Chip>(checkedIds.first())
            val category = selectedChip?.tag as? String ?: "all"
            filterBusinesses(map, category)
        }
    }

    private fun filterBusinesses(map: GoogleMap, category: String) {
        currentFilterCategory = category
        val filteredLocations = if (category == "all") {
            allBusinessLocations
        } else {
            allBusinessItems.filter { it.category == category }.map { it.position }
        }
        rebuildHeatmap(map, filteredLocations, map.cameraPosition.zoom)
        Toast.makeText(this, "Mostrando: ${filteredLocations.size} negocios", Toast.LENGTH_SHORT).show()
    }

    data class LocationSuggestion(
        val position: LatLng,
        val score: Double,
        val reason: String
    )

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        fun applyInsets(container: View) {
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                val innerPadding = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(innerPadding.left, innerPadding.top, innerPadding.right, innerPadding.bottom)
                insets
            }
        }
    }
}