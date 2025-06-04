package com.example.scoreviewer

import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.preference.PreferenceManager

class SyncPanelManager(
    private val context: android.content.Context,
    private val syncPanel: LinearLayout,
    private val syncOffsetInput: EditText,
    private val startDelayInput: EditText,
    private val btnApplySync: Button,
    private val onSyncSettingsChanged: (syncOffset: Int, startDelay: Int) -> Unit
) {
    private lateinit var syncWatcher: TextWatcher
    private lateinit var delayWatcher: TextWatcher

    init {
        initializeWatchers()
        loadSavedPreferences()
        setupApplyButton()
    }

    fun show() {
        syncPanel.visibility = android.view.View.VISIBLE
    }

    fun hide() {
        syncPanel.visibility = android.view.View.GONE
    }

    fun toggle() {
        syncPanel.visibility = if (syncPanel.visibility == android.view.View.GONE) 
            android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun initializeWatchers() {
        syncWatcher = createTextWatcher { value ->
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt("sync_offset_ms", value)
                .apply()
        }

        delayWatcher = createTextWatcher { value ->
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt("start_delay_sec", value)
                .apply()
        }

        syncOffsetInput.addTextChangedListener(syncWatcher)
        startDelayInput.addTextChangedListener(delayWatcher)
    }

    private fun createTextWatcher(onValueChanged: (Int) -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val value = s.toString().toIntOrNull() ?: return
                onValueChanged(value)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
    }

    private fun loadSavedPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val appPrefs = context.getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE)
        val isFirstRun = appPrefs.getBoolean("isFirstRun", false)
        if (isFirstRun) {
            syncOffsetInput.setText("")
            startDelayInput.setText("")
        } else {
            if (prefs.contains("sync_offset_ms")) {
                syncOffsetInput.setText(prefs.getInt("sync_offset_ms", 0).toString())
            } else {
                syncOffsetInput.setText("")
            }
            if (prefs.contains("start_delay_sec")) {
                startDelayInput.setText(prefs.getInt("start_delay_sec", 0).toString())
            } else {
                startDelayInput.setText("")
            }
        }
    }

    private fun setupApplyButton() {
        btnApplySync.setOnClickListener {
            val syncValue = syncOffsetInput.text.toString().toIntOrNull()
            val delayValue = startDelayInput.text.toString().toIntOrNull()

            if (syncValue != null || delayValue != null) {
                onSyncSettingsChanged(
                    syncValue ?: 0,
                    delayValue ?: 0
                )
                Toast.makeText(context, "싱크 오프셋 및 시작 지연 설정이 적용되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun clearPreferences() {
        syncOffsetInput.removeTextChangedListener(syncWatcher)
        startDelayInput.removeTextChangedListener(delayWatcher)
        
        syncOffsetInput.setText("")
        startDelayInput.setText("")
        
        PreferenceManager.getDefaultSharedPreferences(context).edit().apply {
            remove("sync_offset_ms")
            remove("start_delay_sec")
            apply()
        }
        
        syncOffsetInput.addTextChangedListener(syncWatcher)
        startDelayInput.addTextChangedListener(delayWatcher)
    }
} 