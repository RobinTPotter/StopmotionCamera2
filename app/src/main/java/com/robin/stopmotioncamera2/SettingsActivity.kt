package com.robin.stopmotioncamera2

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var onionSkinsSeekBar: SeekBar
    private lateinit var onionSkinsValue: TextView
    private lateinit var opacityStartSeekBar: SeekBar
    private lateinit var opacityStartValue: TextView
    private lateinit var opacityEndSeekBar: SeekBar
    private lateinit var opacityEndValue: TextView
    private lateinit var opacityTotalSeekBar: SeekBar
    private lateinit var opacityTotalValue: TextView
    private lateinit var crosshairCheckbox: CheckBox
    private lateinit var thirdsCheckbox: CheckBox
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Initialize views
        onionSkinsSeekBar = findViewById(R.id.onionSkinsSeekBar)
        onionSkinsValue = findViewById(R.id.onionSkinsValue)
        opacityStartSeekBar = findViewById(R.id.opacityStartSeekBar)
        opacityStartValue = findViewById(R.id.opacityStartValue)
        opacityEndSeekBar = findViewById(R.id.opacityEndSeekBar)
        opacityEndValue = findViewById(R.id.opacityEndValue)
        opacityTotalSeekBar = findViewById(R.id.opacityTotalSeekBar)
        opacityTotalValue = findViewById(R.id.opacityTotalValue)
        crosshairCheckbox = findViewById(R.id.crosshairCheckbox)
        thirdsCheckbox = findViewById(R.id.thirdsCheckbox)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        
        // Load current settings
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val numSkins = prefs.getInt("onion_skins", 2)
        val opacityStart = prefs.getFloat("opacity_start", 0.5f)
        val opacityEnd = prefs.getFloat("opacity_end", 0.35f)
        val opacityTotal = prefs.getFloat("opacity_total", 0.35f)
        val showCrosshair = prefs.getBoolean("show_crosshair", true)
        val showThirds = prefs.getBoolean("show_thirds", false)
        
        // Setup onion skins (0-4)
        onionSkinsSeekBar.max = 4
        onionSkinsSeekBar.progress = numSkins
        onionSkinsValue.text = numSkins.toString()
        onionSkinsSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onionSkinsValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Setup opacity start (0.0 - 1.0, stored as 0-100 in seekbar)
        opacityStartSeekBar.max = 100
        opacityStartSeekBar.progress = (opacityStart * 100).toInt()
        opacityStartValue.text = String.format("%.2f", opacityStart)
        opacityStartSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                opacityStartValue.text = String.format("%.2f", progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Setup opacity end (0.0 - 1.0, stored as 0-100 in seekbar)
        opacityEndSeekBar.max = 100
        opacityEndSeekBar.progress = (opacityEnd * 100).toInt()
        opacityEndValue.text = String.format("%.2f", opacityEnd)
        opacityEndSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                opacityEndValue.text = String.format("%.2f", progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Setup opacity total (0.0 - 1.0, stored as 0-100 in seekbar)
        opacityTotalSeekBar.max = 100
        opacityTotalSeekBar.progress = (opacityTotal * 100).toInt()
        opacityTotalValue.text = String.format("%.2f", opacityTotal)
        opacityTotalSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                opacityTotalValue.text = String.format("%.2f", progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Setup checkboxes
        crosshairCheckbox.isChecked = showCrosshair
        thirdsCheckbox.isChecked = showThirds
        
        // Save button
        saveButton.setOnClickListener {
            val editor = prefs.edit()
            editor.putInt("onion_skins", onionSkinsSeekBar.progress)
            editor.putFloat("opacity_start", opacityStartSeekBar.progress / 100f)
            editor.putFloat("opacity_end", opacityEndSeekBar.progress / 100f)
            editor.putFloat("opacity_total", opacityTotalSeekBar.progress / 100f)
            editor.putBoolean("show_crosshair", crosshairCheckbox.isChecked)
            editor.putBoolean("show_thirds", thirdsCheckbox.isChecked)
            editor.apply()
            finish()
        }
        
        // Cancel button
        cancelButton.setOnClickListener {
            finish()
        }
    }
}
