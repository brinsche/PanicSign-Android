package de.bastianrinsche.panicsign

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.hardware.SensorManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding
import androidx.lifecycle.coroutineScope
import androidx.preference.PreferenceManager
import com.google.android.gms.actions.SearchIntents
import com.google.android.material.snackbar.Snackbar
import com.squareup.seismic.ShakeDetector
import de.bastianrinsche.panicsign.ColorUtils.Companion.resolveColor
import de.bastianrinsche.panicsign.PanicSign.Companion.colorUtils
import de.bastianrinsche.panicsign.PanicSign.Companion.signService
import de.bastianrinsche.panicsign.databinding.ActivitySignBinding
import dev.chrisbanes.insetter.doOnApplyWindowInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException

class SignActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignBinding
    private lateinit var topControl: ColorControl
    private lateinit var bottomControl: ColorControl
    private lateinit var topSign: Drawable
    private lateinit var bottomSign: Drawable
    private var sensorManager: SensorManager? = null
    private var shakeDetector: ShakeDetector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setup()
        enableShakeDetection()
        if (hasVoiceExtra(intent)) {
            handleVoiceInteraction(intent)
        } else if (savedInstanceState != null) {
            topControl.selected = savedInstanceState.getString(getString(R.string.key_top))!!
            bottomControl.selected = savedInstanceState.getString(getString(R.string.key_bottom))!!
        }
    }

    private fun setup() {
        val sign = binding.viewSign.drawable as LayerDrawable
        topSign = sign.findDrawableByLayerId(R.id.sign_top)
        bottomSign = sign.findDrawableByLayerId(R.id.sign_bottom)
        binding.viewSign.setImageDrawable(sign)
        topControl = ColorControl(binding.controlTop, getString(R.string.key_light_blue)) { newColor ->
            changeSignColor(Sign.TOP, newColor)
        }
        bottomControl = ColorControl(binding.controlBottom, getString(R.string.key_blue)) { newColor ->
            changeSignColor(Sign.BOTTOM, newColor)
        }
        binding.buttonOverflow.setOnClickListener { openAbout() }
        binding.buttonChange.setOnClickListener { sendChangeRequest() }
        fixBottomButtonPadding()
    }

    override fun onStart() {
        super.onStart()
        shakeDetector?.start(sensorManager)
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        intent = newIntent
        if (hasVoiceExtra(newIntent)) {
            handleVoiceInteraction(newIntent)
        }
    }

    override fun onStop() {
        super.onStop()
        shakeDetector?.stop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(getString(R.string.key_top), topControl.selected)
        outState.putString(getString(R.string.key_bottom), bottomControl.selected)
        super.onSaveInstanceState(outState)
    }

    private fun enableShakeDetection() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shakeDetector = ShakeDetector(ShakeDetector.Listener {
            val colors = colorUtils.randomColors
            topControl.selected = colors.first
            bottomControl.selected = colors.second
            if (autoSendEnabled()) {
                sendChangeRequest()
            }
        })
    }

    private fun fixBottomButtonPadding() {
        binding.buttonChange.doOnApplyWindowInsets { view, insets, initialState ->
            view.updatePadding(
                    bottom = initialState.paddings.bottom + insets.systemWindowInsetBottom
            )
        }
    }

    private fun hasVoiceExtra(intent: Intent): Boolean {
        return SearchIntents.ACTION_SEARCH == intent.action && intent.hasExtra(SearchManager.QUERY)
    }

    private fun handleVoiceInteraction(intent: Intent) {
        val query = intent.getStringExtra(SearchManager.QUERY)
        try {
            val colors = colorUtils.colorsFromQuery(colorUtils.intlNameKeyMap, query)
            topControl.selected = colors.first
            bottomControl.selected = colors.second
            Timber.d("VOICE Pair<%s, %s>", colors.first, colors.second)
        } catch (e: IllegalArgumentException) {
            //TODO maybe random
        }
        if (autoSendEnabled()) {
            sendChangeRequest()
        }
    }

    private fun changeSignColor(part: Sign, colorString: String) {
        val color = resolveColor(this, colorUtils.colorMap, colorString)
        when (part) {
            Sign.TOP -> topSign.setTint(color)
            Sign.BOTTOM -> bottomSign.setTint(color)
        }
    }

    private fun openAbout() {
        val about = Intent(this, AboutActivity::class.java)
        startActivity(about)
    }

    private fun sendChangeRequest() {
        val topRGB = colorUtils.colorToRGBString(topControl.selected)
        val bottomRGB = colorUtils.colorToRGBString(bottomControl.selected)
        lifecycle.coroutineScope.launch(context = Dispatchers.IO) {
            val message = try {
                val response = signService.setSignColors(topRGB, bottomRGB)
                when (response.code()) {
                    200 -> R.string.success
                    429 -> R.string.error_rate_limited
                    else -> R.string.error_generic
                }
            } catch (e: IOException) {
                R.string.error_generic
            }
            showSnackbar(message)
        }
    }

    private fun autoSendEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.key_pref_auto_send), false)
    }

    private fun showSnackbar(messageId: Int) {
        Snackbar.make(binding.controlBottom.root, messageId, Snackbar.LENGTH_LONG).show()
    }

    enum class Sign {
        TOP, BOTTOM
    }
}
