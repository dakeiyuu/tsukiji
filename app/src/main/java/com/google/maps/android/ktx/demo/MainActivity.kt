package com.google.maps.android.ktx.demo

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
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
import com.google.maps.android.heatmaps.WeightedLatLng
import com.google.maps.android.ktx.awaitMap
import com.google.maps.android.ktx.awaitMapLoad
import com.google.maps.android.ktx.cameraMoveStartedEvents
import com.google.maps.android.ktx.cameraIdleEvents
import com.google.maps.android.ktx.demo.model.MyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

class MainActivity : AppCompatActivity() {

    private var heatmapProvider: HeatmapTileProvider? = null
    private var allBusinessLocations = mutableListOf<LatLng>()
    private var allBusinessItems     = mutableListOf<MyItem>()
    private var heatmapOverlay: TileOverlay? = null
    private var googleMap: GoogleMap? = null
    private var suggestionMarkers    = mutableListOf<Marker>()
    private var isHeatmapVisible     = false

    // ── Urban-filter thresholds ──────────────────────────────────
    /** Max km from any known business — beyond this = non-urban (sea, field, mountain) */
    private val MAX_DIST_FROM_BUSINESS_KM = 1.5
    /** Radius to count "nearby" businesses when judging urbanness */
    private val URBAN_RADIUS_KM           = 2.0
    /** Minimum businesses within URBAN_RADIUS_KM to be considered urban */
    private val URBAN_MIN_COUNT           = 3

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

    private lateinit var guardadosFragment: GuardadosFragment
    private lateinit var propiedadesFragment: PropiedadesFragment

    // ═════════════════════════════════════════════════════════════
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
                Log.e(TAG, "API Key vacía")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en onCreate: ${e.message}", e)
            Toast.makeText(this, "Error al inicializar: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment ?: run {
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
                            centerMapOnMonterrey(map)
                            setupFilterChips()
                            setupNewBusinessChips()
                            setupFindLocationButton()
                        }
                    }

                    launch { googleMap?.cameraMoveStartedEvents()?.collect { } }
                    launch { googleMap?.cameraIdleEvents()?.collect { } }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error mapa: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Error al cargar el mapa", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  NAVIGATION
    // ─────────────────────────────────────────────────────────────
    private fun setupFragments() {
        guardadosFragment  = GuardadosFragment()
        propiedadesFragment = PropiedadesFragment()
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, fragment).commit()
        findViewById<FrameLayout>(R.id.fragment_container).visibility = View.VISIBLE
        findViewById<ConstraintLayout>(R.id.map_container).visibility  = View.GONE
    }

    private fun showMap() {
        findViewById<FrameLayout>(R.id.fragment_container).visibility = View.GONE
        findViewById<ConstraintLayout>(R.id.map_container).visibility  = View.VISIBLE
    }

    private fun setupFilterFab() {
        val fab        = findViewById<FloatingActionButton>(R.id.fab_filters)
        val filterCard = findViewById<CardView>(R.id.filter_card)          // ← CardView, not MaterialCardView
        fab.setOnClickListener {
            filterCard.visibility = if (filterCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun setupBottomNavigation() {
        setupFragments()
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
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

    // ─────────────────────────────────────────────────────────────
    //  SAVE
    // ─────────────────────────────────────────────────────────────
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
            .setMessage("¿Deseas guardar esta ubicación?")
            .setPositiveButton("Guardar") { d, _ -> saveCurrentLocation(title, category, score); d.dismiss() }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }

    // ─────────────────────────────────────────────────────────────
    //  CHIPS
    // ─────────────────────────────────────────────────────────────
    private fun setupNewBusinessChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_new_business)
        newBusinessCategories.forEach { (key, label) ->
            chipGroup.addView(Chip(this).apply {
                text            = label
                isCheckable     = true
                tag             = key
                setChipBackgroundColorResource(android.R.color.white)
                setTextColor(getColor(android.R.color.darker_gray))
                chipStrokeWidth = 2f
                chipStrokeColor = getColorStateList(android.R.color.darker_gray)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) { setChipBackgroundColorResource(android.R.color.holo_orange_light); setTextColor(getColor(android.R.color.white)) }
                    else         { setChipBackgroundColorResource(android.R.color.white); setTextColor(getColor(android.R.color.darker_gray)) }
                }
            })
        }
    }

    private fun setupFindLocationButton() {
        val button    = findViewById<MaterialButton>(R.id.btn_find_location)
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_new_business)
        button.setOnClickListener {
            val id = chipGroup.checkedChipId
            if (id == View.NO_ID) { Toast.makeText(this, "Selecciona un tipo de negocio", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val category = chipGroup.findViewById<Chip>(id)?.tag as? String ?: return@setOnClickListener
            findBestLocations(category)
        }
    }

    private fun setupFilterChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_filters)
        businessCategories.forEach { (key, label) ->
            chipGroup.addView(Chip(this).apply {
                text            = label
                isCheckable     = true
                isChecked       = key == "all"
                tag             = key
                setChipBackgroundColorResource(android.R.color.white)
                setTextColor(getColor(android.R.color.darker_gray))
                chipStrokeWidth = 2f
                setOnCheckedChangeListener { _, checked ->
                    if (checked) { setChipBackgroundColorResource(android.R.color.holo_orange_light); setTextColor(getColor(android.R.color.white)) }
                    else         { setChipBackgroundColorResource(android.R.color.white); setTextColor(getColor(android.R.color.darker_gray)) }
                }
            })
        }
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) { group.check(group.getChildAt(0).id); return@setOnCheckedStateChangeListener }
            if (isHeatmapVisible) {
                hideHeatmapAndMarkers()
                Toast.makeText(this, "Pulsa 'Encontrar' para analizar la nueva categoría", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CORE: find best URBAN locations + show heatmap
    // ─────────────────────────────────────────────────────────────
    private fun findBestLocations(category: String) {
        val map = googleMap ?: return
        suggestionMarkers.forEach { it.remove() }
        suggestionMarkers.clear()
        setupCustomInfoWindow(map)

        Toast.makeText(this, "Analizando mejores ubicaciones en zonas urbanas…", Toast.LENGTH_SHORT).show()

        val bounds            = map.projection.visibleRegion.latLngBounds
        val visibleBusinesses = allBusinessItems.filter { bounds.contains(it.position) }
        val sameCategory      = visibleBusinesses.filter { it.category == category }

        // Show heatmap for all locations of this category (global dataset)
        showCategoryHeatmap(map, allBusinessItems.filter { it.category == category }.map { it.position })

        if (visibleBusinesses.isEmpty()) {
            Toast.makeText(this, "No hay negocios visibles. Mueve el mapa a una ciudad.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            val bestLocations = withContext(Dispatchers.Default) {
                findOptimalUrbanLocationsInBounds(bounds, sameCategory, visibleBusinesses)
            }

            if (bestLocations.isEmpty()) {
                Toast.makeText(this@MainActivity,
                    "No se encontraron ubicaciones óptimas en zonas urbanas visibles.",
                    Toast.LENGTH_LONG).show()
                return@launch
            }

            val noCompetition = sameCategory.isEmpty()

            bestLocations.take(5).forEachIndexed { index, loc ->
                val hue = if (index == 0 || noCompetition)
                    BitmapDescriptorFactory.HUE_GREEN
                else
                    BitmapDescriptorFactory.HUE_ORANGE
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(loc.position)
                        .title("TOCAME PARA GUARDARME\nUbicación Sugerida #${index + 1}")
                        .snippet("${loc.reason}\nÍndice de oportunidad: ${String.format("%.1f", loc.score)}")
                        .icon(BitmapDescriptorFactory.defaultMarker(hue))
                )
                marker?.tag = loc
                marker?.let { suggestionMarkers.add(it) }
            }

            map.setOnInfoWindowClickListener { m ->
                (m.tag as? LocationSuggestion)?.let { saveCurrentLocation(m.title ?: "Ubicación", category, it.score) }
            }

            map.animateCamera(CameraUpdateFactory.newLatLngZoom(bestLocations.first().position, 14f))
            suggestionMarkers.firstOrNull()?.showInfoWindow()

            val msg = if (noCompetition)
                "¡Sin competencia! Se encontraron ${bestLocations.size} zonas urbanas óptimas."
            else
                "Se encontraron ${bestLocations.size} ubicaciones óptimas en zonas urbanas."
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  HEATMAP — smooth Gaussian blobs, semi-transparent, no squares
    //
    //  Key insight: WeightedLatLng lets us encode cluster density as
    //  intensity. The HeatmapTileProvider then renders a smooth
    //  Gaussian kernel around each point. Using DEFAULT_RADIUS (not 50)
    //  avoids the blocky appearance that comes from very large radii.
    // ─────────────────────────────────────────────────────────────
    private fun showCategoryHeatmap(map: GoogleMap, locations: List<LatLng>) {
        heatmapOverlay?.remove()
        heatmapOverlay = null
        if (locations.isEmpty()) { isHeatmapVisible = false; return }

        try {
            // Compute local density for each point (neighbours within 500 m)
            val weighted = locations.map { loc ->
                val neighbours = locations.count { other ->
                    other !== loc && calculateDistance(loc, other) < 0.5
                }
                WeightedLatLng(loc, 1.0 + neighbours.toDouble())
            }

            // Gradient: fully transparent → green → yellow → orange → red
            // Starting transparent ensures the "empty" areas of the tile are invisible
            val colors = intArrayOf(
                Color.argb(  0,   0, 180,   0),  // transparent (no data)
                Color.argb(140,   0, 210,   0),  // 🟢 green  — baja densidad
                Color.argb(170, 255, 220,   0),  // 🟡 yellow — media
                Color.argb(200, 255, 120,   0),  // 🟠 orange — alta
                Color.argb(230, 210,  20,  20)   // 🔴 red    — muy alta
            )
            val stops    = floatArrayOf(0.0f, 0.2f, 0.5f, 0.75f, 1.0f)
            val gradient = Gradient(colors, stops)

            val provider = HeatmapTileProvider.Builder()
                .weightedData(weighted)
                .gradient(gradient)
                // DEFAULT_RADIUS = 20 — keeps blobs smooth without pixel-square artefacts
                .radius(HeatmapTileProvider.DEFAULT_RADIUS)
                .opacity(0.72)   // semi-transparent so map labels show through
                .build()

            heatmapProvider  = provider
            heatmapOverlay   = map.addTileOverlay(TileOverlayOptions().tileProvider(provider))
            isHeatmapVisible = true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating heatmap: ${e.message}", e)
        }
    }

    private fun hideHeatmapAndMarkers() {
        heatmapOverlay?.remove(); heatmapOverlay = null; heatmapProvider = null; isHeatmapVisible = false
        suggestionMarkers.forEach { it.remove() }; suggestionMarkers.clear()
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

    // ─────────────────────────────────────────────────────────────
    //  URBAN VALIDATION
    //  A grid cell is "urban" only if it is near enough to known
    //  businesses AND has a minimum cluster size around it.
    //  This eliminates suggestions on mountains, rivers, sea, etc.
    // ─────────────────────────────────────────────────────────────
    private fun isUrbanCandidate(location: LatLng, allBusinesses: List<MyItem>): Boolean {
        val nearest = allBusinesses.minOfOrNull { calculateDistance(location, it.position) } ?: return false
        if (nearest > MAX_DIST_FROM_BUSINESS_KM) return false
        val nearby = allBusinesses.count { calculateDistance(location, it.position) <= URBAN_RADIUS_KM }
        return nearby >= URBAN_MIN_COUNT
    }

    private fun findOptimalUrbanLocationsInBounds(
        bounds: LatLngBounds,
        sameCategory: List<MyItem>,
        allBusinesses: List<MyItem>
    ): List<LocationSuggestion> {
        val suggestions = mutableListOf<LocationSuggestion>()
        val gridSize = 20
        val latStep  = (bounds.northeast.latitude  - bounds.southwest.latitude)  / gridSize
        val lngStep  = (bounds.northeast.longitude - bounds.southwest.longitude) / gridSize

        for (i in 0..gridSize) for (j in 0..gridSize) {
            val candidate = LatLng(
                bounds.southwest.latitude  + latStep * i,
                bounds.southwest.longitude + lngStep * j
            )
            if (!isUrbanCandidate(candidate, allBusinesses)) continue   // skip non-urban
            val score = calculateLocationScore(candidate, sameCategory, allBusinesses)
            if (score > 0) suggestions.add(LocationSuggestion(candidate, score, generateReason(score, sameCategory.size)))
        }
        return suggestions.sortedByDescending { it.score }
    }

    // ─────────────────────────────────────────────────────────────
    //  SCORING
    // ─────────────────────────────────────────────────────────────
    private fun calculateLocationScore(
        location: LatLng,
        sameCategory: List<MyItem>,
        allBusinesses: List<MyItem>
    ): Double {
        val nearestCompetitor   = sameCategory.minOfOrNull  { calculateDistance(location, it.position) } ?: Double.MAX_VALUE
        val nearestBusiness     = allBusinesses.minOfOrNull { calculateDistance(location, it.position) } ?: Double.MAX_VALUE
        val nearbyBusinessCount = allBusinesses.count       { calculateDistance(location, it.position) < 2.0 }
        val nearbyCompetitors   = sameCategory.count        { calculateDistance(location, it.position) < 1.0 }

        var score = 0.0
        score += min(nearestCompetitor * 10, 50.0)
        score += max(30.0 - nearestBusiness * 5, 0.0)
        score += min(nearbyBusinessCount * 2.0, 20.0)
        score -= nearbyCompetitors * 10.0
        return max(score, 0.0)
    }

    private fun calculateDistance(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.latitude  - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).pow(2)
        return 6371.0 * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun generateReason(score: Double, @Suppress("UNUSED_PARAMETER") n: Int) = when {
        score > 70 -> "Excelente: Baja competencia, alta actividad comercial"
        score > 50 -> "Buena ubicación: Balance entre competencia y tráfico"
        score > 30 -> "Zona emergente con potencial"
        else       -> "Oportunidad de crecimiento"
    }

    // ─────────────────────────────────────────────────────────────
    //  DATA — tighter clusters so urban filter works correctly
    // ─────────────────────────────────────────────────────────────
    private fun loadBusinessDataFallback() {
        generateSampleBusinessData()
        Log.d(TAG, "Negocios cargados: ${allBusinessLocations.size}")
    }

    private fun generateSampleBusinessData() {
        val rng        = java.util.Random()
        val categories = listOf("restaurant", "retail", "service", "entertainment")
        val cities = listOf(
            LatLng(25.6866, -100.3161) to "Monterrey",
            LatLng(25.4232, -100.9903) to "Saltillo",
            LatLng(19.4326,  -99.1332) to "Ciudad de México",
            LatLng(40.7128,  -74.0060) to "Nueva York",
            LatLng(34.0522, -118.2437) to "Los Ángeles",
            LatLng(51.5074,   -0.1278) to "Londres",
            LatLng(48.8566,    2.3522) to "París",
            LatLng(35.6762,  139.6503) to "Tokio"
        )
        cities.forEach { (center, name) ->
            // Dense downtown core (±0.03° ≈ ±3 km)
            repeat(60 + rng.nextInt(30)) {
                addBusiness(LatLng(center.latitude  + (rng.nextDouble() - 0.5) * 0.06,
                    center.longitude + (rng.nextDouble() - 0.5) * 0.06),
                    name, categories[rng.nextInt(categories.size)])
            }
            // Sparser suburbs (±0.09° ≈ ±9 km) — still within urban filter
            repeat(20 + rng.nextInt(10)) {
                addBusiness(LatLng(center.latitude  + (rng.nextDouble() - 0.5) * 0.09,
                    center.longitude + (rng.nextDouble() - 0.5) * 0.09),
                    name, categories[rng.nextInt(categories.size)])
            }
        }
        Log.d(TAG, "Generados ${allBusinessLocations.size} negocios en ${cities.size} ciudades")
    }

    private fun addBusiness(loc: LatLng, city: String, category: String) {
        allBusinessLocations.add(loc)
        allBusinessItems.add(MyItem(loc, "Negocio en $city", "Categoría: $category", category))
    }

    private fun centerMapOnMonterrey(map: GoogleMap) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(25.6866, -100.3161), 12f))
    }

    // ─────────────────────────────────────────────────────────────
    data class LocationSuggestion(val position: LatLng, val score: Double, val reason: String)

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        fun applyInsets(container: View) {
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                val p = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                view.setPadding(p.left, p.top, p.right, p.bottom)
                insets
            }
        }
    }
}