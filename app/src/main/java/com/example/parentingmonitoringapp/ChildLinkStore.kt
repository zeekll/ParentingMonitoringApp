package com.example.parentingmonitoringapp

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Central helper for resolving which linked child a parent is currently
 * viewing. Supports parents with more than one linked child while staying
 * backward compatible with existing single-child accounts.
 *
 * Data model on users/{parentUid}:
 *  - studentIds: List<String>  (preferred - supports any number of children)
 *  - studentId:  String        (legacy single-child field, still written for
 *    backward compatibility and used as a fallback when studentIds is absent)
 *
 * The "currently selected" child is remembered locally per signed-in parent
 * via SharedPreferences, so the choice persists across screens and app
 * restarts until the parent switches.
 */
object ChildLinkStore {

    private const val PREFS_NAME = "child_selection_prefs"
    private fun prefKey(parentUid: String) = "selected_student_id_$parentUid"

    /** All student IDs linked to this parent, preferring the new array field. */
    fun getLinkedStudentIds(userDoc: DocumentSnapshot): List<String> {
        @Suppress("UNCHECKED_CAST")
        val list = (userDoc.get("studentIds") as? List<Any?>)
            ?.mapNotNull { it?.toString()?.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
        if (!list.isNullOrEmpty()) return list

        val single = userDoc.getString("studentId")?.trim()
        return if (!single.isNullOrEmpty()) listOf(single) else emptyList()
    }

    fun getSelectedStudentId(context: Context, parentUid: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(prefKey(parentUid), null)
    }

    fun setSelectedStudentId(context: Context, parentUid: String, studentId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(prefKey(parentUid), studentId).apply()
    }

    fun clearSelection(context: Context, parentUid: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(prefKey(parentUid)).apply()
    }

    /**
     * Resolves the student ID to show right now: the locally-remembered
     * selection if it's still one of the parent's linked children, otherwise
     * the first linked child (which is then remembered as the new selection).
     * Returns null if the parent has no linked children at all.
     */
    fun resolveSelectedStudentId(context: Context, parentUid: String, userDoc: DocumentSnapshot): String? {
        val linked = getLinkedStudentIds(userDoc)
        if (linked.isEmpty()) return null

        val saved = getSelectedStudentId(context, parentUid)
        if (saved != null && linked.contains(saved)) return saved

        val fallback = linked.first()
        setSelectedStudentId(context, parentUid, fallback)
        return fallback
    }
}