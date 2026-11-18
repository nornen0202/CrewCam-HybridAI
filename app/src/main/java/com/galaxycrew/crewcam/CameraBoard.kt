package com.galaxycrew.crewcam

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import crewcam.core.CrewTopology
import java.text.SimpleDateFormat
import java.util.Date

internal data class RoleProgress(
    val accepted: Boolean,
    val ready: Boolean,
    val fresh: Boolean,
    val original: Boolean,
) {
    fun text(showOriginal: Boolean = true) =
        (listOf("Accepted" to accepted, "Ready" to ready, "Verified" to fresh) +
                if (showOriginal) listOf("Saved" to original) else emptyList())
            .joinToString("   ") { (name, passed) -> (if (passed) "✓ " else "○ ") + name }
}

internal fun actionText(action: String?) =
    when (action) {
        "TIGHTEN_CURRENT" -> "Move closer or zoom in"
        "WIDEN_CURRENT" -> "Step back if clear, or zoom out"
        "HOLD",
        "VERIFIED_HOLD" -> "Hold this framing"
        else -> "Awaiting assignment"
    }

internal class CameraBoard(context: Context) : LinearLayout(context) {
    private val mint = Color.rgb(156, 230, 194)
    private val muted = Color.rgb(190, 204, 200)
    private val clock = SimpleDateFormat("HH:mm:ss", java.util.Locale.US)

    init {
        orientation = VERTICAL
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    private fun label(value: String, size: Int, bold: Boolean = false) =
        TextView(context).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(if (bold) Color.WHITE else muted)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(2))
        }

    private fun tile() =
        LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
            background =
                GradientDrawable().apply {
                    setColor(Color.rgb(36, 53, 57))
                    cornerRadius = dp(10).toFloat()
                }
        }

    fun show(room: Room, virtual: Boolean = false) {
        removeAllViews()
        val host = room.camera == "A"
        val ids = if (host && room.size > 1) CrewTopology.names(room.size) else listOf(room.camera)
        addView(label("Checked views", 18, true))
        for (pair in ids.chunked(2)) {
            val row = LinearLayout(context)
            for (id in pair) {
                val shot = room.shots[id]
                val waiting = id != room.camera && id !in room.online
                val cell = tile()
                cell.addView(
                    label(
                        if (virtual) "Virtual " + id
                        else "Camera " + id + (if (id == room.camera) " · this phone" else ""),
                        14,
                        true,
                    )
                )
                val photo = shot?.photo
                if (photo != null)
                    cell.addView(
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageBitmap(photo)
                            contentDescription = "Camera " + id + " check photo"
                        },
                        LinearLayout.LayoutParams(-1, dp(86)),
                    )
                else
                    cell.addView(
                        label(
                                when {
                                    waiting -> "Waiting for camera " + id
                                    shot != null -> "No photo · result received"
                                    room.freshRequest -> "Awaiting fresh frame"
                                    else -> "No checked frame yet"
                                },
                                14,
                            )
                            .apply {
                                gravity = Gravity.CENTER
                                minHeight = dp(68)
                            }
                    )
                if (shot != null) {
                    val whenChecked =
                        if (shot.checkedAt > 0) clock.format(Date(shot.checkedAt))
                        else "Time unavailable"
                    cell.addView(
                        label(whenChecked + " · " + shot.view.source.replace('_', ' '), 12)
                    )
                    cell.addView(
                        label(
                            shot.view.framing +
                                " · " +
                                (shot.view.latencyMs / 1000.0).let {
                                    String.format(java.util.Locale.US, "%.1f s", it)
                                },
                            14,
                            true,
                        )
                    )
                }
                row.addView(
                    cell,
                    LayoutParams(0, -2, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(3)) },
                )
            }
            addView(row)
        }
        if (room.size == 1) return
        addView(label(if (host) "Crew roles" else "Your camera role", 18, true))
        for (id in ids) {
            val team = room.leader
            val choice =
                team?.choices?.find { it.camera == id }
                    ?: room.lastChoice.takeIf { id == room.camera }
            val observed = room.shots[id]?.view?.framing ?: team?.initialView(id)?.framing
            val state =
                RoleProgress(
                    team?.accepted?.contains(id) ?: (id == room.camera && room.selfAccepted),
                    team?.done?.contains(id) ?: (id == room.camera && room.selfReady),
                    room.lastVerdict == true,
                    id in room.originals,
                )
            val card = tile()
            card.addView(
                label(
                    (if (virtual) "Virtual " else "Camera ") +
                        id +
                        "  ·  " +
                        (observed ?: "Pending") +
                        " → " +
                        (choice?.framing ?: "Pending"),
                    16,
                    true,
                )
            )
            if (room.freshRequest && team?.initialView(id) != null && room.shots[id] != null)
                card.addView(
                    label(
                        "First " +
                            team.initialView(id)!!.framing +
                            " · Fresh " +
                            room.shots.getValue(id).view.framing,
                        14,
                    )
                )
            val instruction =
                when {
                    room.lastVerdict == false -> "Check again · " + room.note
                    id != room.camera && id !in room.online -> "Waiting for connection"
                    room.freshRequest && room.lastVerdict == null && id !in room.shots ->
                        "Check a fresh view"
                    else -> actionText(choice?.adjustment)
                }
            card.addView(label(instruction, 15).apply { setTextColor(mint) })
            card.addView(label(state.text(!virtual), 13))
            addView(card, LayoutParams(-1, -2).apply { setMargins(dp(2), dp(2), dp(2), dp(3)) })
        }
    }
}
