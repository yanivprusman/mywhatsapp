package com.automatelinux.mywhatsapp

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Lands on a message inside WhatsApp. WhatsApp can be told which chat to open,
 * but not which message, so this does what a person would: ⋮ → Search → type the
 * message's first line → enter. WhatsApp then scrolls to the newest match.
 *
 * It acts only while a job from "Show in WhatsApp" is pending (≤ [TIMEOUT_MS]),
 * only inside WhatsApp, and never sends or edits anything.
 */
class FindMessageService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var step = Step.OPEN_MENU
    private var running = false

    private enum class Step { OPEN_MENU, PICK_SEARCH, TYPE, DONE }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val job = FindMessage.job ?: return
        if (running) return
        if (System.currentTimeMillis() - job.at > TIMEOUT_MS) { FindMessage.job = null; return }
        running = true
        step = Step.OPEN_MENU
        main.post(::tick)
    }

    override fun onInterrupt() {}

    private fun tick() {
        val job = FindMessage.job
        if (job == null || step == Step.DONE) { running = false; return }
        if (System.currentTimeMillis() - job.at > TIMEOUT_MS) {
            fail("Couldn't reach WhatsApp's search in time — the chat is open")
            return
        }
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        if (root == null || pkg == null || !pkg.startsWith("com.whatsapp")) { again(); return }
        when (step) {
            Step.OPEN_MENU -> {
                // Only in the chat itself: its title bar is showing.
                if (byId(root, pkg, "conversation_contact_name") == null) { again(); return }
                val more = byId(root, pkg, "menuitem_overflow") ?: run { again(); return }
                if (click(more)) step = Step.PICK_SEARCH
                again(400)
            }
            Step.PICK_SEARCH -> {
                val item = root.findAccessibilityNodeInfosByViewId("$pkg:id/title")
                    .firstOrNull { it.text?.toString() in SEARCH_LABELS }
                if (item == null) { again(); return }
                if (click(item)) step = Step.TYPE
                again(400)
            }
            Step.TYPE -> {
                val box = byId(root, pkg, "search_view_edit_text") ?: run { again(); return }
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, job.query)
                }
                box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                main.postDelayed({
                    box.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    // Once WhatsApp has scrolled to the match, empty the box: the chat stays on the message.
                    main.postDelayed({
                        box.refresh()
                        box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                        })
                        FindMessage.job = null
                        step = Step.DONE
                        running = false
                    }, CLEAR_AFTER_MS)
                }, 300)
            }
            Step.DONE -> running = false
        }
    }

    private fun again(delay: Long = 200) { main.postDelayed(::tick, delay) }

    private fun fail(msg: String) {
        FindMessage.job = null
        step = Step.DONE
        running = false
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun byId(root: AccessibilityNodeInfo, pkg: String, id: String): AccessibilityNodeInfo? =
        root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstOrNull()

    /** Click the node or its nearest clickable ancestor (menu rows put the click on the row, not the title). */
    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    companion object {
        private const val TIMEOUT_MS = 10_000L
        private const val CLEAR_AFTER_MS = 1_200L
        private val SEARCH_LABELS = setOf("Search", "חיפוש")
    }
}
