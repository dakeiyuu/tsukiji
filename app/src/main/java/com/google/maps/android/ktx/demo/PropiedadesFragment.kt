package com.google.maps.android.ktx.demo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout

data class PropiedadItem(
    val nombre: String,
    val direccion: String,
    val precio: String,
    val tipo: String,
    val fotos: List<Uri> = emptyList()
)

class PropiedadesFragment : Fragment() {

    private val propiedades = mutableListOf(
        PropiedadItem("Local Comercial Centro", "Av. Constitución 100, Col. Centro, Monterrey, NL, México", "$15,000 MXN/mes", "Renta"),
        PropiedadItem("Oficina San Pedro", "Blvd. Antonio L. Rodríguez 3000, Col. Santa María, San Pedro Garza García, NL, México", "$25,000 MXN/mes", "Renta"),
        PropiedadItem("Bodega Industrial", "Carretera a Laredo km 12, Col. Industrial, Monterrey, NL, México", "$2,500,000 MXN", "Venta"),
        PropiedadItem("Local en Plaza", "Plaza Fiesta San Agustín, Col. Valle Oriente, San Pedro Garza García, NL, México", "$18,000 MXN/mes", "Renta"),
        PropiedadItem("Terreno Comercial", "Av. Eugenio Garza Sada 3000, Col. Tecnológico, Monterrey, NL, México", "$8,000,000 MXN", "Venta")
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
                try { requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                fotoUris[slotSeleccionado] = uri
                mostrarFotoEnSlot(slotSeleccionado, uri)
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_propiedades, container, false)

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
            onClick = { propiedad ->
                showDetalleDialog(propiedad)
            }
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

    // ── NUEVO: Dialog de detalle ──────────────────────────────────────────────

    private fun showDetalleDialog(propiedad: PropiedadItem) {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_detalle_propiedad, null)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        // Rellenar datos
        view.findViewById<TextView>(R.id.tv_detalle_nombre).text = propiedad.nombre
        view.findViewById<TextView>(R.id.tv_detalle_direccion).text = propiedad.direccion
        view.findViewById<TextView>(R.id.tv_detalle_precio).text = propiedad.precio
        view.findViewById<TextView>(R.id.tv_detalle_precio_full).text = propiedad.precio

        val tvTipo = view.findViewById<TextView>(R.id.tv_detalle_tipo)
        tvTipo.text = if (propiedad.tipo == "Renta") "Renta 🏠" else "Venta 🏢"
        tvTipo.setBackgroundResource(
            if (propiedad.tipo == "Renta") R.drawable.chip_renta_bg else R.drawable.chip_venta_bg
        )

        // Fotos
        val llFotos     = view.findViewById<LinearLayout>(R.id.ll_fotos)
        val llSinFotos  = view.findViewById<LinearLayout>(R.id.ll_sin_fotos)
        val hsvFotos    = view.findViewById<View>(R.id.hsv_fotos)

        if (propiedad.fotos.isEmpty()) {
            hsvFotos.visibility  = View.GONE
            llSinFotos.visibility = View.VISIBLE
        } else {
            hsvFotos.visibility  = View.VISIBLE
            llSinFotos.visibility = View.GONE
            propiedad.fotos.forEach { uri ->
                val img = ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(400, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                        marginEnd = 12
                    }
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

    // ── Dialog agregar propiedad (sin cambios) ────────────────────────────────

    private fun showAddPropertyDialog() {
        fotoUris.fill(null)

        val inflatedView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_agregar_propiedad, null)
        dialogView = inflatedView

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(inflatedView)
            .create()

        val toggleGroup  = inflatedView.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggle_tipo)
        val btnPublicar  = inflatedView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_publicar)
        val btnCerrar    = inflatedView.findViewById<ImageView>(R.id.btn_cerrar_dialog)

        val tilNombre    = inflatedView.findViewById<TextInputLayout>(R.id.til_nombre)
        val tilCalle     = inflatedView.findViewById<TextInputLayout>(R.id.til_calle)
        val tilNumExt    = inflatedView.findViewById<TextInputLayout>(R.id.til_numero_ext)
        val tilNumInt    = inflatedView.findViewById<TextInputLayout>(R.id.til_numero_int)
        val tilColonia   = inflatedView.findViewById<TextInputLayout>(R.id.til_colonia)
        val tilCiudad    = inflatedView.findViewById<TextInputLayout>(R.id.til_ciudad)
        val tilCp        = inflatedView.findViewById<TextInputLayout>(R.id.til_cp)
        val tilEstado    = inflatedView.findViewById<TextInputLayout>(R.id.til_estado)
        val tilPais      = inflatedView.findViewById<TextInputLayout>(R.id.til_pais)
        val tilPrecio    = inflatedView.findViewById<TextInputLayout>(R.id.til_precio)

        toggleGroup.check(R.id.btn_venta)
        btnCerrar.setOnClickListener { dialog.dismiss() }

        configurarSlotsFoto(inflatedView)

        btnPublicar.setOnClickListener {
            val nombre   = tilNombre?.editText?.text?.toString()?.trim() ?: ""
            val calle    = tilCalle?.editText?.text?.toString()?.trim() ?: ""
            val numExt   = tilNumExt?.editText?.text?.toString()?.trim() ?: ""
            val numInt   = tilNumInt?.editText?.text?.toString()?.trim() ?: ""
            val colonia  = tilColonia?.editText?.text?.toString()?.trim() ?: ""
            val ciudad   = tilCiudad?.editText?.text?.toString()?.trim() ?: ""
            val cp       = tilCp?.editText?.text?.toString()?.trim() ?: ""
            val estado   = tilEstado?.editText?.text?.toString()?.trim() ?: ""
            val pais     = tilPais?.editText?.text?.toString()?.trim() ?: ""
            val precio   = tilPrecio?.editText?.text?.toString()?.trim() ?: ""

            if (nombre.isEmpty() || calle.isEmpty() || numExt.isEmpty() || ciudad.isEmpty() || precio.isEmpty()) {
                Toast.makeText(requireContext(), "Completa los campos obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val numStr     = if (numInt.isNotEmpty()) "$numExt Int. $numInt" else numExt
            val coloniaStr = if (colonia.isNotEmpty()) "Col. $colonia, " else ""
            val cpStr      = if (cp.isNotEmpty()) "C.P. $cp, " else ""
            val direccionCompleta = "$calle $numStr, ${coloniaStr}${ciudad}, $estado $cpStr$pais".trim().trimEnd(',')

            val tipoSeleccionado = when (toggleGroup.checkedButtonId) {
                R.id.btn_renta -> "Renta"
                else           -> "Venta"
            }

            val fotosGuardadas = fotoUris.filterNotNull()

            propiedades.add(0, PropiedadItem(
                nombre    = nombre,
                direccion = direccionCompleta,
                precio    = "$$precio MXN${if (tipoSeleccionado == "Renta") "/mes" else ""}",
                tipo      = tipoSeleccionado,
                fotos     = fotosGuardadas
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
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { type = "image/*" }
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

        fun updateData(newItems: List<PropiedadItem>) { items = newItems; notifyDataSetChanged() }

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
            // Click en toda la tarjeta → abre detalle
            holder.itemView.setOnClickListener { onClick(item) }
            // Click en eliminar → borra
            holder.btnEliminar.setOnClickListener { onDelete(holder.adapterPosition) }
        }

        override fun getItemCount() = items.size
    }
}