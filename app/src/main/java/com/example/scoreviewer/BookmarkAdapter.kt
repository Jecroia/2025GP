package com.example.scoreviewer

import Section
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

class BookmarkAdapter(
    private val fragment: BookmarkDialogFragment
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var mode: Mode = Mode.SECTIONS
    private val sections = mutableListOf<Section>()
    private val bookmarks = mutableListOf<Bookmark>()
    private val selectedPosition = mutableSetOf<Int>()

    enum class Mode { SECTIONS, BOOKMARKS }

    fun updateSections(list: List<Section>) {
        mode = Mode.SECTIONS
        sections.apply {
            clear()
            addAll(list)
        }
        selectedPosition.clear()
        notifyDataSetChanged()
    }

    fun updateBookmarks(list: List<Bookmark>) {
        mode = Mode.BOOKMARKS
        bookmarks.apply {
            clear()
            addAll(list)
        }
        selectedPosition.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int =
        if (mode == Mode.SECTIONS) sections.size else bookmarks.size

    override fun getItemViewType(position: Int): Int = mode.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == Mode.SECTIONS.ordinal) {
            SectionViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_section, parent, false)
            )
        } else {
            BookmarkViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_bookmark, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        if (mode == Mode.SECTIONS) {
            (holder as SectionViewHolder).bind(sections[pos])
        } else {
            (holder as BookmarkViewHolder).bind(bookmarks[pos], pos)
        }
    }

    inner class SectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tv_section_name)
        init {
            view.setOnClickListener {
                fragment.onSectionClicked(adapterPosition)
            }
        }
        fun bind(s: Section) {
            tvName.text = s.name
        }
    }

    inner class BookmarkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvInfo: TextView = view.findViewById(R.id.tv_bookmark_info)
        private val btnEdit: ImageButton = view.findViewById(R.id.btn_edit_comment)
        private val btnDelete: ImageButton = view.findViewById(R.id.btn_remove_bookmark)

        fun bind(bm: Bookmark, pos: Int) {
            // 기본 텍스트
            tvInfo.text = "${bm.page + 1}p  ${bm.comment}"

            // 선택 상태에 따라 하이라이트 및 버튼 노출 제어
            val isSelected = selectedPosition.contains(pos)
            itemView.setBackgroundColor(
                if (isSelected)
                    ContextCompat.getColor(itemView.context, R.color.selection_highlight)
                else
                    Color.TRANSPARENT
            )
            btnEdit.isVisible = isSelected
            btnDelete.isVisible = isSelected

            // 클릭 / 더블 클릭 처리
            itemView.setOnClickListener {
                if (selectedPosition.contains(pos)) {
                    // 이미 선택된 상태: 바로 이동
                    fragment.onBookmarkClicked(bm)
                } else {
                    // 첫 클릭: 선택 토글
                    selectedPosition.clear()
                    selectedPosition.add(pos)
                    notifyDataSetChanged()
                }
            }
            itemView.setOnLongClickListener {
                // 더블클릭 대신 롱클릭으로도 바로 이동 처리
                fragment.onBookmarkClicked(bm)
                true
            }

            // 버튼 리스너
            btnEdit.setOnClickListener {
                showEditCommentDialog(bm)
            }
            btnDelete.setOnClickListener {
                showConfirmDelete(bm, pos)
            }
        }

        private fun showEditCommentDialog(bm: Bookmark) {
            val context = itemView.context
            val editText = EditText(context).apply {
                setText(bm.comment)
                hint = "Enter comment"
            }
            AlertDialog.Builder(context)
                .setTitle("Edit Comment")
                .setView(editText)
                .setPositiveButton("Save") { _, _ ->
                    val newComment = editText.text.toString().trim()
                    // SharedPreferences 업데이트
                    fragment.saveComment(bm.page, newComment)
                    // 모델 갱신 및 리스트 다시 로드
                    fragment.reloadBookmarks()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun showConfirmDelete(bm: Bookmark, pos: Int) {
            AlertDialog.Builder(itemView.context)
                .setMessage("Remove bookmark on page ${bm.page + 1}?")
                .setPositiveButton("Remove") { _, _ ->
                    fragment.removeBookmark(bm.page)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
