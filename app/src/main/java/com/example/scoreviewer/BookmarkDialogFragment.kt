package com.example.scoreviewer

import Section
import android.app.Dialog
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class BookmarkDialogFragment : DialogFragment() {

    interface HostCallback {
        fun onNavigateToPage(page: Int)
        /** 북마크 목록이 변경되었을 때 호출 */
        fun onBookmarksChanged(newBookmarks: Set<Int>)
    }

    private var hostCallback: HostCallback? = null

    // PDF 전체 섹션 목록 (악보 구간)
    internal val sections: List<Section>
        get() = requireArguments().getParcelableArrayList<Section>("sections")!!
    // 현재 보고 있는 섹션의 인덱스
    private var currentSectionIndex = 0

    // SharedPrefs 에서 읽어온 전체 북마크
    private val allBookmarks = mutableListOf<Bookmark>()

    // RecyclerView 어댑터
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: BookmarkAdapter

    private val pdfBaseName: String
        get() = requireArguments().getString("pdfBaseName")!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        hostCallback = context as? HostCallback
            ?: throw IllegalStateException("Host must implement HostCallback")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_bookmark_navigation)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCanceledOnTouchOutside(false)

        recycler = dialog.findViewById(R.id.recycler_bookmarks)
        recycler.layoutManager = LinearLayoutManager(context)

        adapter = BookmarkAdapter(this)
        recycler.adapter = adapter

        // 헤더 버튼
        dialog.findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            // 구간 목록 화면으로
            showSections()
        }
        dialog.findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
            dismiss()  // 아무 이동 없이 닫기
        }

        // 초기화: arguments 에서 받거나 Activity 에서 set 해주세요.
        loadSections()      // Section 리스트
        loadAllBookmarks()  // Bookmark 리스트

        // 첫 진입은 “현재 섹션 내 북마크 보기”
        showBookmarksInSection(currentSectionIndex)

        return dialog
    }

    private fun loadSections() {
        // arguments 에서 꺼내서 내부 필드(혹은 adapter)에 전달
        val secs = requireArguments()
            .getParcelableArrayList<Section>("sections")!!
        adapter.updateSections(secs)
    }

    /** prefs에서 전체 북마크+코멘트 다시 로드 */
    private fun loadAllBookmarks() {
        val prefs = requireActivity().getSharedPreferences(
            "Bookmarks_${pdfBaseName}", MODE_PRIVATE
        )
        val bmSet = prefs.getStringSet("bookmarks", emptySet())!!
        allBookmarks.clear()
        bmSet.mapNotNull { it.toIntOrNull() }.forEach { pg ->
            val comment = prefs.getString("comment_$pg", "") ?: ""
            allBookmarks.add(Bookmark(page = pg, comment = comment))
        }
    }

    /** 섹션 목록 화면 */
    private fun showSections() {
        adapter.updateSections(sections)
        dialog?.findViewById<TextView>(R.id.tv_title)
            ?.text = getString(R.string.title_select_section)
        dialog?.findViewById<ImageButton>(R.id.btn_back)
            ?.visibility = View.GONE
    }

    /** currentSectionIndex 섹션의 북마크 리스트 화면 */
    private fun showBookmarksInSection(index: Int) {
        currentSectionIndex = index
        val sec = sections[index]
        val list = allBookmarks.filter { it.page in sec.startPage..sec.endPage }
        adapter.updateBookmarks(list)
        // 섹션 이름 표시
        dialog?.findViewById<TextView>(R.id.tv_title)
            ?.text = sec.name
        dialog?.findViewById<ImageButton>(R.id.btn_back)
            ?.visibility = View.VISIBLE
    }

    /** 내부 어댑터에서 호출 */
    fun onBookmarkClicked(bookmark: Bookmark) {
        hostCallback?.onNavigateToPage(bookmark.page)
        dismiss()
    }

    /** 섹션이 선택되었을 때 호출 */
    fun onSectionClicked(sectionIndex: Int) {
        showBookmarksInSection(sectionIndex)
    }
    fun saveComment(page: Int, comment: String) {
        val prefs = requireActivity().getSharedPreferences(
            "Bookmarks_${pdfBaseName}", MODE_PRIVATE
        )
        prefs.edit().putString("comment_$page", comment).apply()
    }

    /** 북마크 삭제 (prefs & 리스트 갱신) */
    fun removeBookmark(page: Int) {
        val prefs = requireActivity()
            .getSharedPreferences("Bookmarks_$pdfBaseName", Context.MODE_PRIVATE)

        // 1) prefs에서 삭제
        val bmSet = prefs.getStringSet("bookmarks", emptySet())!!.toMutableSet()
        bmSet.remove(page.toString())
        prefs.edit()
            .putStringSet("bookmarks", bmSet)
            .remove("comment_$page")
            .apply()

        // 2) 메모리 모델 갱신
        loadAllBookmarks()
        showBookmarksInSection(currentSectionIndex)

        // 3) 호스트에 알리기
        hostCallback?.onBookmarksChanged(bmSet.mapNotNull { it.toIntOrNull() }.toSet())
    }

    /** 현재 섹션의 북마크 목록만 다시 표시 */
    fun reloadBookmarks() {
        loadAllBookmarks()
        showBookmarksInSection(currentSectionIndex)
    }
}
