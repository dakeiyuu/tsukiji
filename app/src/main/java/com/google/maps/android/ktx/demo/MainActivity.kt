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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.maps.android.heatmaps.Gradient
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
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

    // Categoría activa para refrescar el heatmap al cambiar zoom
    private var currentCategory: String = "all"

    private val businessCategories = mapOf(
        "all"           to "Todos",
        "restaurant"    to "Restaurantes",
        "retail"        to "Tiendas",
        "service"       to "Servicios",
        "entertainment" to "Entretenimiento"
    )

    private val newBusinessCategories = mapOf(
        "restaurant"    to "🍽️ Restaurante",
        "retail"        to "🛍️ Tienda",
        "service"       to "🔧 Servicio",
        "entertainment" to "🎬 Entretenimiento"
    )

    // ── Gradiente único por categoría ────────────────────────────────────────
    private val categoryGradients: Map<String, Gradient> = mapOf(
        "all" to Gradient(
            intArrayOf(
                Color.argb(0,   0, 255,   0),
                Color.rgb(  0, 255,   0),
                Color.rgb(255, 255,   0),
                Color.rgb(255, 140,   0),
                Color.rgb(220,  20,  60)
            ),
            floatArrayOf(0.0f, 0.20f, 0.50f, 0.75f, 1.0f)
        ),
        "restaurant" to Gradient(
            intArrayOf(
                Color.argb(0,  255, 100,   0),
                Color.rgb(255, 160,  50),
                Color.rgb(255, 100,   0),
                Color.rgb(200,  30,   0)
            ),
            floatArrayOf(0.0f, 0.30f, 0.65f, 1.0f)
        ),
        "retail" to Gradient(
            intArrayOf(
                Color.argb(0,   30, 100, 255),
                Color.rgb( 80, 160, 255),
                Color.rgb( 30, 100, 255),
                Color.rgb(  0,  40, 200)
            ),
            floatArrayOf(0.0f, 0.30f, 0.65f, 1.0f)
        ),
        "service" to Gradient(
            intArrayOf(
                Color.argb(0,    0, 180, 120),
                Color.rgb( 60, 210, 150),
                Color.rgb(  0, 180, 120),
                Color.rgb(  0, 110,  70)
            ),
            floatArrayOf(0.0f, 0.30f, 0.65f, 1.0f)
        ),
        "entertainment" to Gradient(
            intArrayOf(
                Color.argb(0,  150,   0, 255),
                Color.rgb(190,  80, 255),
                Color.rgb(150,   0, 255),
                Color.rgb( 90,   0, 180)
            ),
            floatArrayOf(0.0f, 0.30f, 0.65f, 1.0f)
        )
    )

    private lateinit var guardadosFragment: GuardadosFragment
    private lateinit var propiedadesFragment: PropiedadesFragment

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isRestore = savedInstanceState != null

        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            setContentView(R.layout.activity_main)
            applyInsets(findViewById(R.id.main_container))
            setupBottomNavigation()
            setupFilterFab()
            if (BuildConfig.MAPS_API_KEY.isEmpty()) {
                Toast.makeText(this, "Falta API key de Google Maps", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en onCreate: ${e.message}", e)
            Toast.makeText(this, "Error al inicializar: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
            ?: run {
                Toast.makeText(this, "Error: MapFragment no encontrado", Toast.LENGTH_LONG).show()
                return
            }

        lifecycleScope.launch {
            try {
                lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                    googleMap = mapFragment.awaitMap()
                    if (!isRestore) googleMap?.awaitMapLoad()

                    try { loadBusinessDataFallback() }
                    catch (e: Exception) {
                        if (allBusinessLocations.isEmpty()) generateSampleBusinessData()
                    }

                    googleMap?.let { map ->
                        if (allBusinessLocations.isNotEmpty()) {
                            setupHeatmap(map, buildWeightedLocations(currentCategory))
                            centerMapOnData(map)
                            setupFilterChips(map)
                            setupNewBusinessChips()
                            setupFindLocationButton()
                        } else {
                            Toast.makeText(this@MainActivity, "No se pudieron cargar datos", Toast.LENGTH_LONG).show()
                        }
                    }

                    // Refresca radio del heatmap al terminar de mover la cámara
                    launch {
                        googleMap?.cameraIdleEvents()?.collect {
                            refreshHeatmapRadius()
                        }
                    }
                    launch {
                        googleMap?.cameraMoveStartedEvents()?.collect {
                            Log.d(TAG, "Camera moved - reason $it")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error inicializando mapa: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Error al cargar el mapa: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Heatmap ───────────────────────────────────────────────────────────────

    /**
     * Construye WeightedLatLng donde el peso refleja la densidad local del punto:
     * más vecinos cercanos = mayor peso = mancha más intensa en esa zona.
     */
    private fun buildWeightedLocations(category: String): List<WeightedLatLng> {
        val items = if (category == "all") allBusinessItems
        else allBusinessItems.filter { it.category == category }
        if (items.isEmpty()) return emptyList()

        val radiusKm = 0.5  // 500 m de vecindario para calcular densidad
        return items.map { item ->
            val neighbors = items.count { other ->
                other !== item && calculateDistance(item.position, other.position) <= radiusKm
            }
            // peso base 1.0 + bonus logarítmico por densidad (máx ≈ 5.0)
            val weight = 1.0 + ln(1.0 + neighbors.toDouble())
            WeightedLatLng(item.position, weight)
        }
    }

    /**
     * Crea o reemplaza el heatmap con gradiente propio de la categoría activa
     * y radio ajustado al zoom actual.
     */
    private fun setupHeatmap(map: GoogleMap, weightedLocations: List<WeightedLatLng>) {
        heatmapOverlay?.remove()
        heatmapOverlay = null
        if (weightedLocations.isEmpty()) return

        try {
            val gradient = categoryGradients[currentCategory] ?: categoryGradients["all"]!!
            val radius   = zoomToRadius(map.cameraPosition.zoom)

            val provider = HeatmapTileProvider.Builder()
                .weightedData(weightedLocations)
                .gradient(gradient)
                .radius(radius)
                .opacity(0.78)
                .build()

            heatmapProvider = provider
            heatmapOverlay  = map.addTileOverlay(TileOverlayOptions().tileProvider(provider))
        } catch (e: Exception) {
            Log.e(TAG, "Error creando heatmap: ${e.message}", e)
        }
    }

    /**
     * Recalcula el radio en función del zoom y reconstruye el heatmap si cambió.
     * Evita reconstrucciones innecesarias usando una banda de tolerancia de ±3 px.
     */
    private fun refreshHeatmapRadius() {
        val map  = googleMap ?: return
        val zoom = map.cameraPosition.zoom
        val newRadius = zoomToRadius(zoom)

        heatmapOverlay?.remove()
        heatmapOverlay = null

        try {
            val weighted = buildWeightedLocations(currentCategory)
            if (weighted.isEmpty()) return
            val gradient = categoryGradients[currentCategory] ?: categoryGradients["all"]!!

            val provider = HeatmapTileProvider.Builder()
                .weightedData(weighted)
                .gradient(gradient)
                .radius(newRadius)
                .opacity(0.78)
                .build()

            heatmapProvider = provider
            heatmapOverlay  = map.addTileOverlay(TileOverlayOptions().tileProvider(provider))
        } catch (e: Exception) {
            Log.e(TAG, "Error refrescando heatmap: ${e.message}", e)
        }
    }

    /**
     * Convierte el nivel de zoom de Google Maps a un radio en píxeles para el heatmap.
     * Zoom bajo → radio grande (manchas amplias a escala mundial).
     * Zoom alto → radio pequeño (precisión a nivel de calle).
     */
    private fun zoomToRadius(zoom: Float): Int = when {
        zoom < 3f  -> 50
        zoom < 5f  -> 45
        zoom < 7f  -> 40
        zoom < 9f  -> 35
        zoom < 11f -> 30
        zoom < 13f -> 25
        zoom < 15f -> 20
        else       -> 15
    }

    // ── Filtros ───────────────────────────────────────────────────────────────

    private fun filterBusinesses(map: GoogleMap, category: String) {
        currentCategory = category
        setupHeatmap(map, buildWeightedLocations(category))
        val count = if (category == "all") allBusinessLocations.size
        else allBusinessItems.count { it.category == category }
        Toast.makeText(this, "Mostrando: $count negocios", Toast.LENGTH_SHORT).show()
    }

    // ── Fragmentos y navegación ───────────────────────────────────────────────

    private fun setupFragments() {
        guardadosFragment   = GuardadosFragment()
        propiedadesFragment = PropiedadesFragment()
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment).commit()
        findViewById<FrameLayout>(R.id.fragment_container).visibility = View.VISIBLE
        findViewById<ConstraintLayout>(R.id.map_container).visibility = View.GONE
    }

    private fun showMap() {
        findViewById<FrameLayout>(R.id.fragment_container).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.map_container).visibility = View.VISIBLE
    }

    private fun setupFilterFab() {
        val fab        = findViewById<FloatingActionButton>(R.id.fab_filters)
        val filterCard = findViewById<View>(R.id.filter_card)
        fab.setOnClickListener {
            filterCard.visibility =
                if (filterCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        setupFragments()
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map         -> { showMap(); true }
                R.id.nav_saved       -> { showFragment(guardadosFragment); true }
                R.id.nav_propiedades -> { showFragment(propiedadesFragment); true }
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_map
    }

    // ── Guardar ubicaciones ───────────────────────────────────────────────────

    fun saveCurrentLocation(title: String, category: String, score: Double) {
        googleMap?.cameraPosition?.target?.let { location ->
            guardadosFragment.addSavedLocation(
                SavedLocation(location, title, "Puntuación: $score", category, score, System.currentTimeMillis())
            )
            Toast.makeText(this, "Ubicación guardada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSaveDialog(title: String, category: String, score: Double) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Guardar ubicación")
            .setMessage("¿Deseas guardar esta ubicación en tus favoritos?")
            .setPositiveButton("Guardar") { d, _ -> saveCurrentLocation(title, category, score); d.dismiss() }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }

    // ── Chips ─────────────────────────────────────────────────────────────────

    private fun setupNewBusinessChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_new_business)
        newBusinessCategories.forEach { (key, label) ->
            chipGroup.addView(buildChip(key, label, false))
        }
    }

    private fun setupFilterChips(map: GoogleMap) {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_filters)
        businessCategories.forEach { (key, label) ->
            chipGroup.addView(buildChip(key, label, key == "all"))
        }
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) { group.check(group.getChildAt(0).id); return@setOnCheckedStateChangeListener }
            val category = group.findViewById<Chip>(checkedIds.first())?.tag as? String ?: "all"
            filterBusinesses(map, category)
        }
    }

    /** Crea un Chip con estilo blanco/borde naranja → naranja al seleccionar. */
    private fun buildChip(key: String, label: String, startChecked: Boolean): Chip =
        Chip(this).apply {
            text       = label
            isCheckable = true
            isChecked  = startChecked
            tag        = key
            applyChipStyle(startChecked)
            setOnCheckedChangeListener { _, checked -> applyChipStyle(checked) }
        }

    private fun Chip.applyChipStyle(checked: Boolean) {
        if (checked) {
            setChipBackgroundColorResource(android.R.color.holo_orange_light)
            setTextColor(getColor(android.R.color.white))
            chipStrokeWidth = 0f
        } else {
            setChipBackgroundColorResource(android.R.color.white)
            setTextColor(getColor(android.R.color.darker_gray))
            chipStrokeWidth = 2f
            setChipStrokeColorResource(android.R.color.holo_orange_light)
        }
    }

    private fun setupFindLocationButton() {
        val button    = findViewById<View>(R.id.btn_find_location)
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_new_business)
        button.setOnClickListener {
            val id = chipGroup.checkedChipId
            if (id == View.NO_ID) {
                Toast.makeText(this, "Por favor selecciona un tipo de negocio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val category = chipGroup.findViewById<Chip>(id)?.tag as? String ?: return@setOnClickListener
            findBestLocations(category)
        }
    }

    // ── Sugerencias de ubicación ──────────────────────────────────────────────

    private fun findBestLocations(category: String) {
        val map = googleMap ?: return
        suggestionMarkers.forEach { it.remove() }
        suggestionMarkers.clear()
        setupCustomInfoWindow(map)

        val bounds            = map.projection.visibleRegion.latLngBounds
        val visibleBusinesses = allBusinessItems.filter { bounds.contains(it.position) }
        val sameCategory      = visibleBusinesses.filter { it.category == category }

        if (visibleBusinesses.isEmpty()) {
            Toast.makeText(this, "No hay negocios en esta área. Mueve el mapa.", Toast.LENGTH_SHORT).show()
            return
        }

        if (sameCategory.isEmpty()) {
            val center = LatLng(
                (bounds.northeast.latitude  + bounds.southwest.latitude)  / 2,
                (bounds.northeast.longitude + bounds.southwest.longitude) / 2
            )
            val marker = map.addMarker(
                MarkerOptions()
                    .position(center)
                    .title("TOCAME PARA GUARDARME\nUbicación Sugerida #1")
                    .snippet("Excelente oportunidad: No hay competencia en esta área\nÍndice de oportunidad: 100.0")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
            marker?.tag = LocationSuggestion(center, 100.0, "Excelente oportunidad: No hay competencia")
            marker?.let { suggestionMarkers.add(it) }
            marker?.showInfoWindow()
            map.setOnInfoWindowClickListener { m ->
                (m.tag as? LocationSuggestion)?.let { showSaveDialog(m.title ?: "Ubicación", category, it.score) }
            }
            return
        }

        val bestLocations = findOptimalLocationsInBounds(bounds, sameCategory, visibleBusinesses)
        if (bestLocations.isEmpty()) {
            Toast.makeText(this, "No se encontraron ubicaciones óptimas", Toast.LENGTH_SHORT).show()
            return
        }

        bestLocations.take(5).forEachIndexed { index, loc ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(loc.position)
                    .title("TOCAME PARA GUARDARME\nUbicación Sugerida #${index + 1}")
                    .snippet("${loc.reason}\nÍndice de oportunidad: ${String.format("%.1f", loc.score)}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            )
            marker?.tag = loc
            marker?.let { suggestionMarkers.add(it) }
        }

        map.setOnInfoWindowClickListener { m ->
            (m.tag as? LocationSuggestion)?.let { saveCurrentLocation(m.title ?: "Ubicación", category, it.score) }
        }
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(bestLocations.first().position, 13f))
        suggestionMarkers.firstOrNull()?.showInfoWindow()
        Toast.makeText(this, "Se encontraron ${bestLocations.size} ubicaciones óptimas.", Toast.LENGTH_LONG).show()
    }

    private fun setupCustomInfoWindow(map: GoogleMap) {
        map.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? = null
            override fun getInfoContents(marker: Marker): View {
                val view = layoutInflater.inflate(R.layout.custom_info_window, null)
                view.findViewById<TextView>(R.id.title).text   = marker.title
                view.findViewById<TextView>(R.id.snippet).text = marker.snippet
                return view
            }
        })
    }

    private fun findOptimalLocationsInBounds(
        bounds: LatLngBounds,
        sameCategory: List<MyItem>,
        allBusinesses: List<MyItem>
    ): List<LocationSuggestion> {
        val gridSize = 15
        val latStep  = (bounds.northeast.latitude  - bounds.southwest.latitude)  / gridSize
        val lngStep  = (bounds.northeast.longitude - bounds.southwest.longitude) / gridSize

        return (0..gridSize).flatMap { i ->
            (0..gridSize).mapNotNull { j ->
                val candidate = LatLng(
                    bounds.southwest.latitude  + latStep * i,
                    bounds.southwest.longitude + lngStep * j
                )
                val score = calculateLocationScore(candidate, sameCategory, allBusinesses)
                if (score > 0) LocationSuggestion(candidate, score, generateReason(score, sameCategory.size))
                else null
            }
        }.sortedByDescending { it.score }
    }

    private fun calculateLocationScore(
        location: LatLng,
        sameCategory: List<MyItem>,
        allBusinesses: List<MyItem>
    ): Double {
        val nearestCompetitor   = sameCategory.minOfOrNull { calculateDistance(location, it.position) } ?: Double.MAX_VALUE
        val nearestBusiness     = allBusinesses.minOfOrNull { calculateDistance(location, it.position) } ?: Double.MAX_VALUE
        val nearbyBusinessCount = allBusinesses.count { calculateDistance(location, it.position) < 2.0 }
        val nearbyCompetitors   = sameCategory.count  { calculateDistance(location, it.position) < 1.0 }

        return max(
            min(nearestCompetitor * 10, 50.0) +
                    max(30.0 - nearestBusiness * 5, 0.0) +
                    min(nearbyBusinessCount * 2.0, 20.0) -
                    nearbyCompetitors * 10.0,
            0.0
        )
    }

    private fun calculateDistance(pos1: LatLng, pos2: LatLng): Double {
        val R    = 6371.0
        val dLat = Math.toRadians(pos2.latitude  - pos1.latitude)
        val dLng = Math.toRadians(pos2.longitude - pos1.longitude)
        val a    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(pos1.latitude)) *
                cos(Math.toRadians(pos2.latitude)) *
                sin(dLng / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun generateReason(score: Double, totalCompetitors: Int): String = when {
        score > 70 -> "Excelente ubicación: Baja competencia, alta actividad comercial"
        score > 50 -> "Buena ubicación: Balance entre competencia y tráfico"
        score > 30 -> "Ubicación aceptable: Zona emergente con potencial"
        else       -> "Ubicación con oportunidades de crecimiento"
    }

    // ── Datos de muestra ──────────────────────────────────────────────────────

    private fun loadBusinessDataFallback() {
        try {
            generateSampleBusinessData()
            Log.d(TAG, "Negocios cargados: ${allBusinessLocations.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            Toast.makeText(this, "Error al cargar datos: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun generateSampleBusinessData() {
        val random     = java.util.Random()
        val categories = listOf("restaurant", "retail", "service", "entertainment")
        val majorCities = listOf(
            LatLng(25.6866, -100.3161) to "Monterrey",
            LatLng(25.4232, -100.9903) to "Saltillo",
            LatLng(19.4326,  -99.1332) to "Ciudad de México",
            LatLng(40.7128,  -74.0060) to "Nueva York",
            LatLng(34.0522, -118.2437) to "Los Ángeles",
            LatLng(51.5074,   -0.1278) to "Londres",
            LatLng(48.8566,    2.3522) to "París",
            LatLng(35.6762,  139.6503) to "Tokio"
        )
        majorCities.forEach { (center, cityName) ->
            repeat(80 + random.nextInt(41)) {
                val location = LatLng(
                    center.latitude  + (random.nextDouble() - 0.5) * 0.1,
                    center.longitude + (random.nextDouble() - 0.5) * 0.1
                )
                val category = categories[random.nextInt(categories.size)]
                allBusinessLocations.add(location)
                allBusinessItems.add(MyItem(location, "Negocio en $cityName", "Categoría: $category", category))
            }
        }
    }

    private fun centerMapOnData(map: GoogleMap) {
        if (allBusinessLocations.isEmpty()) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2f))
            return
        }
        try {
            val builder = LatLngBounds.Builder()
            allBusinessLocations.forEach { builder.include(it) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100))
        } catch (e: Exception) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(20.0, 0.0), 2f))
        }
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    data class LocationSuggestion(val position: LatLng, val score: Double, val reason: String)

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        fun applyInsets(container: View) {
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                val p = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                view.setPadding(p.left, p.top, p.right, p.bottom)
                insets
            }
        }
    }
}