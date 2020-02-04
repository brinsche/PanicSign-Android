package de.bastianrinsche.panicsign

import android.os.Bundle
import android.preference.PreferenceManager
import android.text.method.LinkMovementMethod
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import de.bastianrinsche.panicsign.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        val prefKey = getString(R.string.key_pref_auto_send)
        val autoSend = preferences.getBoolean(prefKey, false)
        binding.aboutText.movementMethod = LinkMovementMethod.getInstance()
        binding.prefVoiceSwitch.isChecked = autoSend
        binding.dismiss.setOnClickListener { onBackPressed() }
        binding.prefVoiceSwitch.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
            preferences.edit().putBoolean(prefKey, checked).apply()
        }
    }
}