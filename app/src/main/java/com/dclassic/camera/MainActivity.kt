package com.dclassic.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private lateinit var photo: ImageView
    private lateinit var status: TextView
    private lateinit var filterButton: Button
    private lateinit var strength: SeekBar
    private lateinit var action: Button
    private var selected = 0
    private var power = 85
    private var front = false
    private var capture: ImageCapture? = null
    private var original: Bitmap? = null
    private var editing = false
    private val names = listOf("Kodak", "Fuji", "Leica Mono", "Polaroid", "Cinema", "Experimental")
    private val variants = listOf("Original", "Soft", "Warm", "Cool", "Fade", "Deep", "Matte", "Gold", "Night", "Classic", "Dream", "Punch", "Pastel", "Grainy", "Retro", "Bright", "Moody")
    private val gold = Color.rgb(218, 185, 126)
    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) { original = bmp; editing = true; action.text = "SAVE JPEG"; renderEdit() }
            } catch (e: Exception) { status.text = "Gallery error: ${e.message}" }
        }
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) startCamera() else status.text = "Camera permission diperlukan" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(14,14,14)
        window.navigationBarColor = Color.rgb(14,14,14)
        // Android 15+ edge-to-edge: reserve system bar space explicitly.
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(14,14,14)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val title = TextView(this).apply {
            text = "D'CLASSIC  /  ULTIMATE"; textSize = 17f
            setTextColor(gold); gravity = Gravity.CENTER; setPadding(8,14,8,14)
        }
        root.addView(title)
        photo = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.BLACK) }
        root.addView(photo, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply {
            setTextColor(gold); gravity = Gravity.CENTER; textSize = 14f
            text = "100 CLASSIC FILTERS"; setPadding(4,12,4,12)
        }
        root.addView(status)
        fun btn(label: String, fn: () -> Unit): Button = Button(this).apply {
            text = label; isAllCaps = false; setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(53,52,51)))
            setOnClickListener { fn() }
        }
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        navigation.addView(btn("◀", { selected = (selected + 99) % 100; updateFilter() }), LinearLayout.LayoutParams(0,-2,1f))
        filterButton = btn("99+ FILTERS", { showFilters() })
        navigation.addView(filterButton, LinearLayout.LayoutParams(0,-2,2f))
        navigation.addView(btn("▶", { selected = (selected + 1) % 100; updateFilter() }), LinearLayout.LayoutParams(0,-2,1f))
        root.addView(navigation)
        strength = SeekBar(this).apply {
            max = 100; progress = power; progressTintList = android.content.res.ColorStateList.valueOf(gold)
            thumbTintList = android.content.res.ColorStateList.valueOf(gold)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    power = value; if (editing) renderEdit() else updateFilter()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        root.addView(strength)
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(btn("GALLERY", { gallery.launch("image/*") }), LinearLayout.LayoutParams(0,-2,1f))
        action = btn("● SNAP", { if (editing) saveEdit() else snap() })
        action.setTextColor(Color.rgb(30,27,22))
        action.backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
        bottom.addView(action, LinearLayout.LayoutParams(0,-2,2f))
        bottom.addView(btn("BACK", {
            if (editing) { editing = false; original = null; action.text = "● SNAP"; startCamera(); updateFilter() }
            else { front = !front; startCamera() }
        }), LinearLayout.LayoutParams(0,-2,1f))
        root.addView(bottom)
        val help = TextView(this).apply {
            text = "GALLERY  •  SNAP / SAVE  •  BACK / FLIP"; textSize = 10f
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(4,4,4,8)
        }
        root.addView(help)
        setContentView(root)
        updateFilter()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera() else permission.launch(Manifest.permission.CAMERA)
    }

    private fun label(index: Int): String {
        val group = index / 17
        val variant = index % 17
        return "${index + 1}/100  •  ${names[group]} ${variants[variant]}"
    }
    private fun updateFilter() { status.text = label(selected) + "  •  $power%"; filterButton.text = "99+ FILTERS  •  ${selected+1}/100"; if (editing) renderEdit() }
    private fun showFilters() {
        val list = (0 until 100).map { label(it) }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("FILTER LIBRARY  •  99+ FILTERS").setSingleChoiceItems(list, selected) { d, which -> selected = which; updateFilter(); d.dismiss() }.setNegativeButton("Close", null).show()
    }
    private fun startCamera() {
        if (editing) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                provider.unbindAll()
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
                val analysis = ImageAnalysis.Builder().setTargetResolution(android.util.Size(640,480)).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { frame ->
                    if (!busy.compareAndSet(false,true)) { frame.close(); return@setAnalyzer }
                    try {
                        val bmp = frameToBitmap(frame)
                        val filtered = applyFilter(bmp, selected, power)
                        runOnUiThread { if (!editing) photo.setImageBitmap(filtered); busy.set(false) }
                    } catch (_: Exception) { busy.set(false) } finally { frame.close() }
                }
                provider.bindToLifecycle(this, selector, capture, analysis)
            } catch (e: Exception) { status.text = "Camera error: ${e.message}" }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun frameToBitmap(frame: ImageProxy): Bitmap {
        val y = frame.planes[0]; val u = frame.planes[1]; val v = frame.planes[2]
        val w = frame.width; val h = frame.height
        val data = IntArray(w*h)
        val ybuf = y.buffer; val ubuf = u.buffer; val vbuf = v.buffer
        for (j in 0 until h) for (i in 0 until w) {
            val yy = (ybuf.get(j*y.rowStride+i*y.pixelStride).toInt() and 255)
            val uvIndex = (j/2)*u.rowStride+(i/2)*u.pixelStride
            val uu = (ubuf.get(uvIndex).toInt() and 255)-128
            val vv = (vbuf.get((j/2)*v.rowStride+(i/2)*v.pixelStride).toInt() and 255)-128
            val r = (yy+1.402f*vv).roundToInt().coerceIn(0,255)
            val g = (yy-0.344136f*uu-0.714136f*vv).roundToInt().coerceIn(0,255)
            val b = (yy+1.772f*uu).roundToInt().coerceIn(0,255)
            data[j*w+i] = Color.rgb(r,g,b)
        }
        val bmp = Bitmap.createBitmap(data,w,h,Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply { postRotate(frame.imageInfo.rotationDegrees.toFloat()); if (front) postScale(-1f,1f) }
        return Bitmap.createBitmap(bmp,0,0,w,h,matrix,true)
    }
    private fun applyFilter(source: Bitmap, index: Int, amount: Int): Bitmap {
        val family = index / 17; val variant = index % 17
        val t = amount/100f
        val warmth = (when(family) { 0->15f; 1->-5f; 2->0f; 3->11f; 4->-8f; else->18f } + (variant-8)*2.1f)*t
        val saturation = (when(family) { 0->1.10f; 1->0.82f; 2->0f; 3->0.72f; 4->1.18f; else->1.3f } + (variant-8)*0.022f)
        val sat = 1f + (saturation-1f)*t
        val contrast = 1f + ((variant%7)-3)*0.065f*t
        val lift = (if (family==3) 13f else 0f) + ((variant%5)-2)*3f
        val out = Bitmap.createBitmap(source.width,source.height,Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val cm = ColorMatrix().apply { setSaturation(sat) }
        val scale = contrast
        val shift = 128f*(1f-scale) + lift*t
        cm.postConcat(ColorMatrix(floatArrayOf(scale,0f,0f,0f,shift+warmth, 0f,scale,0f,0f,shift, 0f,0f,scale,0f,shift-warmth, 0f,0f,0f,1f,0f)))
        c.drawBitmap(source,0f,0f,Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }
    private fun snap() {
        val imageCapture = capture ?: return
        status.text = "Capturing..."
        imageCapture.takePicture(executor, object: ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val plane = image.planes[0].buffer
                    val bytes = ByteArray(plane.remaining()); plane.get(bytes)
                    val raw = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                    val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()); if (front) postScale(-1f,1f) }
                    val rotated = Bitmap.createBitmap(raw,0,0,raw.width,raw.height,matrix,true)
                    runOnUiThread { original = rotated; editing = true; action.text = "SAVE JPEG"; renderEdit() }
                } catch (e: Exception) { runOnUiThread { status.text = "Capture error: ${e.message}" } }
                finally { image.close() }
            }
            override fun onError(exception: ImageCaptureException) { runOnUiThread { status.text = "Capture error: ${exception.message}" } }
        })
    }
    private fun renderEdit() {
        val bitmap = original ?: return
        val preview = if (bitmap.width>1200) Bitmap.createScaledBitmap(bitmap,1200,(bitmap.height*1200f/bitmap.width).toInt(),true) else bitmap
        photo.setImageBitmap(applyFilter(preview,selected,power))
        status.text = "EDIT  •  " + label(selected) + "  •  $power%"
    }
    private fun saveEdit() {
        val bmp = original ?: return
        action.isEnabled = false; status.text = "Saving..."
        executor.execute {
            try {
                val result = applyFilter(bmp,selected,power)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME,"DClassic_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/DClassic")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("Cannot create image")
                contentResolver.openOutputStream(uri)?.use { result.compress(Bitmap.CompressFormat.JPEG,94,it) } ?: error("Cannot write image")
                runOnUiThread { status.text = "Saved to Pictures/DClassic"; action.isEnabled = true }
            } catch (e: Exception) { runOnUiThread { status.text = "Save failed: ${e.message}"; action.isEnabled = true } }
        }
    }
    override fun onDestroy() { super.onDestroy(); executor.shutdown() }
}
