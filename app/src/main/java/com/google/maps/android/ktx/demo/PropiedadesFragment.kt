package com.google.maps.android.ktx.demo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.tabs.TabLayout

data class PropiedadItem(
    val nombre: String,
    val direccion: String,
    val precio: String,
    val tipo: String,
    val fotos: List<Uri> = emptyList(),
    val descripcion: String = ""
)

class PropiedadesFragment : Fragment() {

    private val ciudadesPorPais: Map<String, List<String>> = linkedMapOf(
        "México" to listOf(
            "Monterrey", "San Pedro Garza García", "Guadalupe",
            "San Nicolás de los Garza", "Apodaca", "General Escobedo",
            "Santa Catarina", "García", "Juárez", "Cadereyta Jiménez",
            "Saltillo", "Ciudad de México", "Ecatepec", "Nezahualcóyotl",
            "Tlalnepantla", "Naucalpan"
        ),
        "Estados Unidos" to listOf(
            "Nueva York", "Los Ángeles", "Burbank", "Long Beach",
            "Pasadena", "Inglewood", "Compton"
        ),
        "Reino Unido" to listOf("Londres"),
        "Francia" to listOf(
            "París", "Saint-Denis", "Boulogne-Billancourt", "Levallois-Perret"
        ),
        "Japón" to listOf("Tokio", "Yokohama", "Osaka")
    )

    private val paises: List<String> get() = ciudadesPorPais.keys.toList()

    private val propiedades = mutableListOf(
        PropiedadItem("Local Comercial Centro", "Av. Constitución 100, Col. Centro, Monterrey, NL, México", "$15,000 MXN/mes", "Renta", descripcion = "Local en excelente ubicación en el centro de Monterrey. 120 m², planta baja, dos accesos, baño completo y bodega."),
        PropiedadItem("Oficina San Pedro", "Blvd. Antonio L. Rodríguez 3000, Col. Santa María, San Pedro Garza García, NL, México", "$25,000 MXN/mes", "Renta", descripcion = "Oficina ejecutiva en torre corporativa. 85 m², piso 8, vista panorámica, estacionamiento incluido."),
        PropiedadItem("Bodega Industrial", "Carretera a Laredo km 12, Col. Industrial, Monterrey, NL, México", "$2,500,000 MXN", "Venta", descripcion = "Bodega industrial de 800 m², altura 8 metros, andén de carga, oficinas administrativas y vigilancia 24/7."),
        PropiedadItem("Local en Plaza", "Plaza Fiesta San Agustín, Col. Valle Oriente, San Pedro Garza García, NL, México", "$18,000 MXN/mes", "Renta", descripcion = "Local dentro de plaza comercial de alto tráfico. 65 m², zona de comida, con instalaciones de gas y electricidad trifásica."),
        PropiedadItem("Terreno Comercial", "Av. Eugenio Garza Sada 3000, Col. Tecnológico, Monterrey, NL, México", "$8,000,000 MXN", "Venta", descripcion = "Terreno esquinero de 600 m² en avenida principal. Uso de suelo comercial, frente de 20 metros, ideal para desarrollo.")
    )

    private lateinit var adapter: PropiedadAdapter
    private var currentFilter = "Todas"

    private val fotoUris = arrayOfNulls<Uri>(4)
    private var slotSeleccionado = 0
    private var dialogView: View? = null

    private data class SlotIds(val frame: Int, val imageView: Int, val placeholder: Int, val btnRemove: Int)

    private val slots = listOf(
        SlotIds(R.id.frame_foto_1, R.id.iv_foto_1, R.id.placeholder_1, R.id.btn_remove_1),
        SlotIds(R.id.frame_foto_2, R.id.iv_foto_2, R.id.placeholder_2, R.id.btn_remove_2),
        SlotIds(R.id.frame_foto_3, R.id.iv_foto_3, R.id.placeholder_3, R.id.btn_remove_3),
        SlotIds(R.id.frame_foto_4, R.id.iv_foto_4, R.id.placeholder_4, R.id.btn_remove_4)
    )

    private val pickImageLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                fotoUris[slotSeleccionado] = uri
                mostrarFotoEnSlot(slotSeleccionado, uri)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_propiedades, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_propiedades)
        val tabFilter    = view.findViewById<TabLayout>(R.id.tab_filter)
        val btnAgregar   = view.findViewById<ImageView>(R.id.btn_agregar_propiedad)

        adapter = PropiedadAdapter(
            getFilteredList(),
            onDelete = { position ->
                val toRemove = getFilteredList()[position]
                propiedades.remove(toRemove)
                adapter.updateData(getFilteredList())
                Toast.makeText(requireContext(), "Propiedad eliminada", Toast.LENGTH_SHORT).show()
            },
            onClick = { propiedad -> showDetalleDialog(propiedad) }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        tabFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFilter = tab?.text?.toString() ?: "Todas"
                adapter.updateData(getFilteredList())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnAgregar.setOnClickListener { showAddPropertyDialog() }
    }

    private fun getFilteredList(): List<PropiedadItem> = when (currentFilter) {
        "Renta" -> propiedades.filter { it.tipo == "Renta" }
        "Venta" -> propiedades.filter { it.tipo == "Venta" }
        else    -> propiedades.toList()
    }

    // ── Dialog detalle ────────────────────────────────────────────────────────

    private fun showDetalleDialog(propiedad: PropiedadItem) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_detalle_propiedad, null)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.tv_detalle_nombre).text  = propiedad.nombre
        view.findViewById<TextView>(R.id.tv_detalle_direccion).text = propiedad.direccion
        view.findViewById<TextView>(R.id.tv_detalle_precio).text   = propiedad.precio
        view.findViewById<TextView>(R.id.tv_detalle_precio_full).text = propiedad.precio

        val tvTipo = view.findViewById<TextView>(R.id.tv_detalle_tipo)
        tvTipo.text = if (propiedad.tipo == "Renta") "Renta 🏠" else "Venta 🏢"
        tvTipo.setBackgroundResource(
            if (propiedad.tipo == "Renta") R.drawable.chip_renta_bg else R.drawable.chip_venta_bg
        )

        val cardDesc = view.findViewById<CardView>(R.id.card_descripcion)
        val tvDesc   = view.findViewById<TextView>(R.id.tv_detalle_descripcion)
        if (propiedad.descripcion.isNotEmpty()) {
            cardDesc.visibility = View.VISIBLE
            tvDesc.text = propiedad.descripcion
        } else {
            cardDesc.visibility = View.GONE
        }

        val llFotos    = view.findViewById<LinearLayout>(R.id.ll_fotos)
        val llSinFotos = view.findViewById<LinearLayout>(R.id.ll_sin_fotos)
        val hsvFotos   = view.findViewById<View>(R.id.hsv_fotos)

        if (propiedad.fotos.isEmpty()) {
            hsvFotos.visibility   = View.GONE
            llSinFotos.visibility = View.VISIBLE
        } else {
            hsvFotos.visibility   = View.VISIBLE
            llSinFotos.visibility = View.GONE
            val fotoPxWidth = (300 * resources.displayMetrics.density).toInt()
            propiedad.fotos.forEach { uri ->
                val img = ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        fotoPxWidth,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ).apply { marginEnd = 8 }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageURI(uri)
                }
                llFotos.addView(img)
            }
        }

        view.findViewById<ImageView>(R.id.btn_cerrar_detalle).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // ── Dialog agregar propiedad ──────────────────────────────────────────────

    private fun showAddPropertyDialog() {
        fotoUris.fill(null)

        val inflatedView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_agregar_propiedad, null)
        dialogView = inflatedView

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(inflatedView)
            .create()

        // Referencias directas a EditText (sin TextInputLayout)
        val etNombre      = inflatedView.findViewById<EditText>(R.id.et_nombre)
        val etDescripcion = inflatedView.findViewById<EditText>(R.id.et_descripcion)
        val etCalle       = inflatedView.findViewById<EditText>(R.id.et_calle)
        val etNumExt      = inflatedView.findViewById<EditText>(R.id.et_numero_ext)
        val etNumInt      = inflatedView.findViewById<EditText>(R.id.et_numero_int)
        val etColonia     = inflatedView.findViewById<EditText>(R.id.et_colonia)
        val etCp          = inflatedView.findViewById<EditText>(R.id.et_cp)
        val etEstado      = inflatedView.findViewById<EditText>(R.id.et_estado)
        val etPrecio      = inflatedView.findViewById<EditText>(R.id.et_precio)
        val actvCiudad    = inflatedView.findViewById<AutoCompleteTextView>(R.id.et_ciudad)
        val actvPais      = inflatedView.findViewById<AutoCompleteTextView>(R.id.et_pais)
        val toggleGroup   = inflatedView.findViewById<MaterialButtonToggleGroup>(R.id.toggle_tipo)
        val btnPublicar   = inflatedView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_publicar)
        val btnCerrar     = inflatedView.findViewById<ImageView>(R.id.btn_cerrar_dialog)

        // Dropdown país
        val paisAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, paises)
        actvPais.setAdapter(paisAdapter)
        actvPais.setOnItemClickListener { _, _, position, _ ->
            actualizarDropdownCiudad(actvCiudad, paises[position])
        }
        actualizarDropdownCiudad(actvCiudad, null)

        toggleGroup.check(R.id.btn_venta)
        btnCerrar.setOnClickListener { dialog.dismiss() }

        configurarSlotsFoto(inflatedView)

        btnPublicar.setOnClickListener {
            val nombre      = etNombre.text.toString().trim()
            val descripcion = etDescripcion.text.toString().trim()
            val calle       = etCalle.text.toString().trim()
            val numExt      = etNumExt.text.toString().trim()
            val numInt      = etNumInt.text.toString().trim()
            val colonia     = etColonia.text.toString().trim()
            val ciudad      = actvCiudad.text.toString().trim()
            val cp          = etCp.text.toString().trim()
            val estado      = etEstado.text.toString().trim()
            val pais        = actvPais.text.toString().trim()
            val precio      = etPrecio.text.toString().trim()

            if (nombre.isEmpty() || calle.isEmpty() || numExt.isEmpty() || ciudad.isEmpty() || precio.isEmpty()) {
                Toast.makeText(requireContext(), "Completa los campos obligatorios (*)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (pais.isNotEmpty() && ciudad.isNotEmpty()) {
                val ciudadesDelPais = ciudadesPorPais[pais] ?: emptyList()
                if (ciudadesDelPais.isNotEmpty() && !ciudadesDelPais.contains(ciudad)) {
                    Toast.makeText(requireContext(), "Selecciona una ciudad válida para $pais", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            val numStr        = if (numInt.isNotEmpty()) "$numExt Int. $numInt" else numExt
            val coloniaStr    = if (colonia.isNotEmpty()) "Col. $colonia, " else ""
            val cpStr         = if (cp.isNotEmpty()) "C.P. $cp, " else ""
            val estadoStr     = if (estado.isNotEmpty()) "$estado, " else ""
            val paisStr       = if (pais.isNotEmpty()) pais else ""
            val direccionCompleta = "$calle $numStr, ${coloniaStr}${ciudad}, ${estadoStr}${cpStr}${paisStr}"
                .trim().trimEnd(',')

            val tipoSeleccionado = when (toggleGroup.checkedButtonId) {
                R.id.btn_renta -> "Renta"
                else           -> "Venta"
            }

            val fotosGuardadas = fotoUris.filterNotNull()

            propiedades.add(0, PropiedadItem(
                nombre      = nombre,
                direccion   = direccionCompleta,
                precio      = "$$precio MXN${if (tipoSeleccionado == "Renta") "/mes" else ""}",
                tipo        = tipoSeleccionado,
                fotos       = fotosGuardadas,
                descripcion = descripcion
            ))

            adapter.updateData(getFilteredList())
            dialog.dismiss()
            dialogView = null

            val msgFotos = if (fotosGuardadas.isNotEmpty()) " con ${fotosGuardadas.size} foto(s)" else ""
            Toast.makeText(requireContext(), "Propiedad publicada$msgFotos", Toast.LENGTH_SHORT).show()
        }

        dialog.setOnDismissListener { dialogView = null }
        dialog.show()
    }

    private fun actualizarDropdownCiudad(actvCiudad: AutoCompleteTextView, pais: String?) {
        val ciudades = if (pais != null) {
            ciudadesPorPais[pais] ?: emptyList()
        } else {
            ciudadesPorPais.values.flatten().distinct().sorted()
        }
        val ciudadAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, ciudades)
        actvCiudad.setAdapter(ciudadAdapter)
        if (pais != null) actvCiudad.setText("", false)
    }

    // ── Gestión de fotos ──────────────────────────────────────────────────────

    private fun configurarSlotsFoto(root: View) {
        slots.forEachIndexed { index, ids ->
            root.findViewById<FrameLayout>(ids.frame)?.setOnClickListener {
                slotSeleccionado = index
                abrirGaleria()
            }
            root.findViewById<ImageView>(ids.btnRemove)?.setOnClickListener {
                fotoUris[index] = null
                limpiarSlot(index)
            }
        }
    }

    private fun abrirGaleria() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            .apply { type = "image/*" }
        pickImageLauncher.launch(intent)
    }

    private fun mostrarFotoEnSlot(index: Int, uri: Uri) {
        val root = dialogView ?: return
        val ids  = slots.getOrNull(index) ?: return
        root.findViewById<ImageView>(ids.imageView)?.apply { setImageURI(uri); visibility = View.VISIBLE }
        root.findViewById<LinearLayout>(ids.placeholder)?.visibility = View.GONE
        root.findViewById<ImageView>(ids.btnRemove)?.visibility = View.VISIBLE
    }

    private fun limpiarSlot(index: Int) {
        val root = dialogView ?: return
        val ids  = slots.getOrNull(index) ?: return
        root.findViewById<ImageView>(ids.imageView)?.apply { setImageURI(null); visibility = View.GONE }
        root.findViewById<LinearLayout>(ids.placeholder)?.visibility = View.VISIBLE
        root.findViewById<ImageView>(ids.btnRemove)?.visibility = View.GONE
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    inner class PropiedadAdapter(
        private var items: List<PropiedadItem>,
        private val onDelete: (Int) -> Unit,
        private val onClick: (PropiedadItem) -> Unit
    ) : RecyclerView.Adapter<PropiedadAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNombre:    TextView  = view.findViewById(R.id.tv_nombre)
            val tvPrecio:    TextView  = view.findViewById(R.id.tv_precio)
            val tvTipo:      TextView  = view.findViewById(R.id.tv_tipo)
            val btnEliminar: ImageView = view.findViewById(R.id.btn_eliminar)
        }

        fun updateData(newItems: List<PropiedadItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_propiedad, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvNombre.text = item.nombre
            holder.tvPrecio.text = "Precio: ${item.precio}"
            holder.tvTipo.text   = if (item.tipo == "Renta") "Renta 🏠" else "Venta 🏢"
            holder.tvTipo.setBackgroundResource(
                if (item.tipo == "Renta") R.drawable.chip_renta_bg else R.drawable.chip_venta_bg
            )
            holder.itemView.setOnClickListener { onClick(item) }
            holder.btnEliminar.setOnClickListener { onDelete(holder.adapterPosition) }
        }

        override fun getItemCount() = items.size
    }
}