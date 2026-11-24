package com.dclassic.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private val ratios = arrayOf("1:1", "3:4", "9:16", "Full")
    private val qualities = arrayOf("High", "Standard", "Low")
    private val prefs by lazy { getSharedPreferences("dclassic_settings", MODE_PRIVATE) }
    private var ratio = "Full"
    private var quality = "High"
    private val gallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) { original = bmp; editing = true; action.text = "SAVE JPEG"; renderEdit() }
            } catch (e: Exception) { status.text = "Gallery error: ${e.message}" }
        }
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) startCamera() else status.text = "Camera permission diperlukan"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ratio = prefs.getString("ratio", "Full")?.takeIf { it in ratios } ?: "Full"
        quality = prefs.getString("quality", "High")?.takeIf { it in qualities } ?: "High"
        window.statusBarColor = Color.rgb(14,14,14)
        window.navigationBarColor = Color.rgb(14,14,14)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(14,14,14)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "D'CLASSIC  /  ULTIMATE"; textSize = 17f
            setTextColor(gold); gravity = Gravity.CENTER; setPadding(8,14,8,14)
        }
        top.addView(title, LinearLayout.LayoutParams(0,-2,1f))
        val settings = Button(this).apply {
            text = "⚙️"; textSize = 21f; isAllCaps = false; setTextColor(gold)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(35,35,35))
            setOnClickListener { showSettings() }
        }
        top.addView(settings, LinearLayout.LayoutParams(64,-2))
        root.addView(top)
        photo = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.BLACK) }
        root.addView(photo, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply {
            setTextColor(gold); gravity = Gravity.CENTER; textSize = 14f
            text = "100 CLASSIC FILTERS"; setPadding(4,12,4,12)
        }
        root.addView(status)
        fun btn(label: String, fn: () -> Unit): Button = Button(this).apply {
            text = label; isAllCaps = false; setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(53,52,51))
            setOnClickListener { fn() }
        }
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        navigation.addView(btn("◀️") { selected = (selected + 99) % 100; updateFilter() }, LinearLayout.LayoutParams(0,-2,1f))
        filterButton = btn("99+ FILTERS") { showFilters() }
        navigation.addView(filterButton, LinearLayout.LayoutParams(0,-2,2f))
        navigation.addView(btn("▶️") { selected = (selected + 1) % 100; updateFilter() }, LinearLayout.LayoutParams(0,-2,1f))
        root.addView(navigation)
        strength = SeekBar(this).apply {
            max = 100; progress = power
            progressTintList = android.content.res.ColorStateList.valueOf(gold)
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
        bottom.addView(btn("GALLERY") { gallery.launch("image/*") }, LinearLayout.LayoutParams(0,-2,1f))
        action = btn("● SNAP") { if (editing) saveEdit() else snap() }
        action.setTextColor(Color.rgb(30,27,22))
        action.backgroundTintList = android.content.res.ColorStateList.valueOf(gold)
        bottom.addView(action, LinearLayout.LayoutParams(0,-2,2f))
        bottom.addView(btn("BACK") {
            if (editing) { editing = false; original = null; action.text = "● SNAP"; startCamera(); updateFilter() }
            else { front = !front; startCamera() }
        }, LinearLayout.LayoutParams(0,-2,1f))
        root.addView(bottom)
        root.addView(TextView(this).apply {
            text = "GALLERY  •  SNAP / SAVE  •  BACK / FLIP"; textSize = 10f
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER; setPadding(4,4,4,8)
        })
        setContentView(root)
        updateFilter()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,12,28,8) }
        panel.addView(TextView(this).apply { text = "ASPECT RATIO"; textSize = 15f; setTextColor(gold); setPadding(0,12,0,4) })
        val ratioGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        ratios.forEachIndexed { i, name ->
            ratioGroup.addView(RadioButton(this).apply { id = 1000+i; text = name; isChecked = ratio == name })
        }
        panel.addView(ratioGroup)
        panel.addView(TextView(this).apply { text = "IMAGE QUALITY (JPEG)"; textSize = 15f; setTextColor(gold); setPadding(0,16,0,4) })
        val qualityGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        qualities.forEachIndexed { i, name ->
            val detail = when (name) { "High" -> "100%"; "Standard" -> "85%"; else -> "60%" }
            qualityGroup.addView(RadioButton(this).apply { id = 2000+i; text = "$name ($detail)"; isChecked = quality == name })
        }
        panel.addView(qualityGroup)
        ScrollView(this).apply { addView(panel) }.also { scroll ->
            android.app.AlertDialog.Builder(this)
                .setTitle("D'CLASSIC  •  CAMERA SETTINGS")
                .setView(scroll)
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("SAVE") { _, _ ->
                    ratio = ratios.getOrElse(ratioGroup.checkedRadioButtonId - 1000) { ratio }
                    quality = qualities.getOrElse(qualityGroup.checkedRadioButtonId - 2000) { quality }
                    prefs.edit().putString("ratio", ratio).putString("quality", quality).apply()
                    if (editing) renderEdit() else updateFilter()
                }.show()
        }
    }

    private fun label(index: Int): String {
        val group = index / 17
        val variant = index % 17
        return "${index + 1}/100  •  ${names[group]} ${variants[variant]}"
    }
    private fun updateFilter() {
        status.text = label(selected) + "  •  $power%  •  $ratio / $quality"
        filterButton.text = "99+ FILTERS  •  ${selected+1}/100"
        if (editing) renderEdit()
    }
    private fun showFilters() {
        val list = (0 until 100).map { label(it) }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("FILTER LIBRARY  •  99+ FILTERS")
            .setSingleChoiceItems(list, selected) { d, which -> selected = which; updateFilter(); d.dismiss() }
            .setNegativeButton("Close", null).show()
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
                val analysis = ImageAnalysis.Builder().setTargetResolution(android.util.Size(640,480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { frame ->
                    if (!busy.compareAndSet(false,true)) { frame.close(); return@setAnalyzer }
                    try {
                        val bmp = frameToBitmap(frame)
                        val cropped = cropRatio(bmp)
                        val filtered = applyFilter(cropped, selected, power)
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
    private fun cropRatio(source: Bitmap): Bitmap {
        val target = when (ratio) { "1:1" -> 1f; "3:4" -> 3f/4f; "9:16" -> 9f/16f; else -> return source }
        val w = source.width; val h = source.height
        val current = w.toFloat()/h
        val cropW: Int; val cropH: Int
        if (current > target) { cropH = h; cropW = (h*target).roundToInt().coerceIn(1,w) }
        else { cropW = w; cropH = (w/target).roundToInt().coerceIn(1,h) }
        return Bitmap.createBitmap(source,(w-cropW)/2,(h-cropH)/2,cropW,cropH)
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
        val cropped = cropRatio(bitmap)
        val maxEdge = max(cropped.width,cropped.height)
        val preview = if (maxEdge > 1200) {
            val scale = 1200f/maxEdge
            Bitmap.createScaledBitmap(cropped,(cropped.width*scale).roundToInt().coerceAtLeast(1),(cropped.height*scale).roundToInt().coerceAtLeast(1),true)
        } else cropped
        photo.setImageBitmap(applyFilter(preview,selected,power))
        status.text = "EDIT  •  " + label(selected) + "  •  $power%  •  $ratio / $quality"
    }
    private fun saveEdit() {
        val bmp = original ?: return
        val savedRatio = ratio; val savedQuality = quality; val savedFilter = selected; val savedPower = power
        action.isEnabled = false; status.text = "Saving..."
        executor.execute {
            var uri: android.net.Uri? = null
            try {
                val target = when(savedRatio) { "1:1" -> 1f; "3:4" -> 3f/4f; "9:16" -> 9f/16f; else -> null }
                val source = if (target == null) bmp else {
                    val w = bmp.width; val h = bmp.height
                    val cw: Int; val ch: Int
                    if (w.toFloat()/h > target) { ch=h; cw=(h*target).roundToInt().coerceIn(1,w) }
                    else { cw=w; ch=(w/target).roundToInt().coerceIn(1,h) }
                    Bitmap.createBitmap(bmp,(w-cw)/2,(h-ch)/2,cw,ch)
                }
                val result = applyFilter(source,savedFilter,savedPower)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME,"DClassic_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/DClassic")
                    put(MediaStore.Images.Media.IS_PENDING,1)
                }
                uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("Cannot create image")
                val compression = when(savedQuality) { "High" -> 100; "Standard" -> 85; else -> 60 }
                val ok = contentResolver.openOutputStream(uri)?.use { result.compress(Bitmap.CompressFormat.JPEG,compression,it) } ?: false
                if (!ok) error("Cannot write image")
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
                runOnUiThread { status.text = "Saved • $savedRatio • $savedQuality • Pictures/DClassic"; action.isEnabled = true }
            } catch (e: Exception) {
                uri?.let { contentResolver.delete(it,null,null) }
                runOnUiThread { status.text = "Save failed: ${e.message}"; action.isEnabled = true }
            }
        }
    }
    override fun onDestroy() { super.onDestroy(); executor.shutdown() }
}
