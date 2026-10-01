package com.omnibuds.tools.shell

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The device-bridge inspection target (ADR-P2-010). Nothing more.
 *
 * This Activity exists so the bridge's deploy, capture, hierarchy-extraction and input paths can be
 * verified against a real screen instead of being declared working. It deliberately contains no
 * OmniBuds logic: no `:core` import, no Bluetooth, no permission request, no network. A screenshot
 * of this shell is evidence about the harness, and the moment it displayed product state a green
 * harness run would start meaning something it never proved.
 *
 * Every element carries three addressable identities, because the bridge resolves targets by each
 * of them in turn:
 *  - a stable `resource-id` from `res/values/ids.xml`, which survives a rebuild and is the only
 *    identifier safe to pin in a test;
 *  - a `content-desc` accessibility label, which is what a human-readable query maps to;
 *  - live bounds, which the bridge computes a centre point from rather than assuming coordinates.
 *
 * The counter, toggle and field are stateful on purpose: an element whose reported state cannot
 * change gives no way to prove that a tap actually landed.
 */
class ShellActivity : Activity() {

    private var probeCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PADDING_DP, PADDING_DP, PADDING_DP, PADDING_DP)
        }

        column.addView(
            TextView(this).apply {
                id = R.id.bridge_title
                text = TITLE_TEXT
                contentDescription = CONTENT_DESC_TITLE
                textSize = TITLE_TEXT_SIZE_SP
            },
        )

        // A per-instance token, never derived from anything on the device, so a harness run can
        // prove it is looking at the app it just installed rather than a stale foreground window.
        column.addView(
            TextView(this).apply {
                id = R.id.bridge_status
                text = STATUS_TEXT
                contentDescription = CONTENT_DESC_STATUS
            },
        )

        column.addView(
            Button(this).apply {
                id = R.id.bridge_probe_button
                text = PROBE_BUTTON_TEXT
                contentDescription = CONTENT_DESC_PROBE_BUTTON
                setOnClickListener {
                    probeCount += 1
                    findViewById<TextView>(R.id.bridge_counter_value).text =
                        "$COUNTER_PREFIX$probeCount"
                }
            },
        )

        column.addView(
            TextView(this).apply {
                id = R.id.bridge_counter_value
                text = "$COUNTER_PREFIX$probeCount"
                contentDescription = CONTENT_DESC_COUNTER
                gravity = Gravity.START
            },
        )

        column.addView(
            CheckBox(this).apply {
                id = R.id.bridge_toggle
                text = TOGGLE_TEXT
                contentDescription = CONTENT_DESC_TOGGLE
            },
        )

        column.addView(
            EditText(this).apply {
                id = R.id.bridge_input_field
                contentDescription = CONTENT_DESC_INPUT
                inputType = InputType.TYPE_CLASS_TEXT
                hint = INPUT_HINT
            },
        )

        setContentView(
            ScrollView(this).apply {
                addView(
                    column,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
        )
    }

    /** Forces layout before a dump, so bounds are never read as an unset rectangle. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.post {
                (window.decorView as View).invalidate()
            }
        }
    }

    private companion object {
        const val TITLE_TEXT = "OmniBuds bridge shell"
        const val STATUS_TEXT = "harness target - no product behaviour"
        const val PROBE_BUTTON_TEXT = "Probe"
        const val TOGGLE_TEXT = "Toggle"
        const val INPUT_HINT = "Input"
        const val COUNTER_PREFIX = "probes: "
        const val CONTENT_DESC_TITLE = "bridge-title"
        const val CONTENT_DESC_STATUS = "bridge-status"
        const val CONTENT_DESC_PROBE_BUTTON = "bridge-probe-button"
        const val CONTENT_DESC_COUNTER = "bridge-counter"
        const val CONTENT_DESC_TOGGLE = "bridge-toggle"
        const val CONTENT_DESC_INPUT = "bridge-input"
        const val PADDING_DP = 24
        const val TITLE_TEXT_SIZE_SP = 20f
    }
}
