package com.example.scoreviewer

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Page
import com.artifex.mupdf.fitz.Pixmap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var seekBar: SeekBar
    private var document: Document? = null
    private lateinit var adapter: PDFPagerAdapter
    private var previewPopup: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        seekBar = findViewById(R.id.pageSeekBar)

        // assets에 있는 PDF 파일을 캐시로 복사
        val pdfFile = copyAssetToFile("Second Run.pdf")
        if (pdfFile == null) {
            Log.e("PDFViewer", "파일 복사 실패!")
            return
        }

        try {
            document = Document.openDocument(pdfFile.absolutePath)

            // ViewPager2와 어댑터 연결
            adapter = PDFPagerAdapter(this, document!!)
            viewPager.adapter = adapter

            // SeekBar 최대값을 PDF 페이지 수에 맞춤 (0부터 시작)
            seekBar.max = document!!.countPages() - 1

            // SeekBar 이벤트 처리: 미리보기 표시 후 손을 떼면 페이지 전환
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    // 미리보기 PopupWindow 생성 및 표시
                    previewPopup = PopupWindow(this@MainActivity)
                    val previewImageView = ImageView(this@MainActivity).apply {
                        adjustViewBounds = true
                    }
                    previewPopup?.contentView = previewImageView
                    // 미리보기 크기 (필요에 따라 조절 가능)
                    previewPopup?.width = 200
                    previewPopup?.height = 300
                    // SeekBar 위쪽에 미리보기 표시 (오프셋 값은 조절 가능)
                    seekBar?.let {
                        previewPopup?.showAsDropDown(it, 0, -it.height - (previewPopup?.height ?: 300))
                    }
                }

                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        // 해당 페이지의 낮은 해상도 썸네일을 생성해서 미리보기 업데이트
                        val previewBitmap = getPageThumbnail(progress)
                        (previewPopup?.contentView as? ImageView)?.setImageBitmap(previewBitmap)
                    }
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    // 미리보기 닫고 실제 페이지 전환
                    previewPopup?.dismiss()
                    previewPopup = null
                    viewPager.setCurrentItem(seekBar?.progress ?: 0, true)
                }
            })

            // ViewPager에서 페이지가 바뀌면 SeekBar도 업데이트
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    seekBar.progress = position
                }
            })

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PDFViewer", "예외 발생: ${e.message}")
        }
    }

    // 낮은 해상도로 PDF 페이지의 썸네일 생성 (미리보기용)
    private fun getPageThumbnail(pageIndex: Int): Bitmap? {
        try {
            val page = document?.loadPage(pageIndex) ?: return null
            // 미리보기용 낮은 스케일
            val matrix = Matrix.Scale(0.2f)
            val pixmap = page.toPixmap(matrix, ColorSpace.DeviceRGB, true, true)
            val width = pixmap.width
            val height = pixmap.height
            val pixels = pixmap.pixels
            val bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            page.destroy()
            pixmap.destroy()
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // assets 폴더의 PDF 파일을 캐시 디렉토리로 복사
    private fun copyAssetToFile(assetName: String): File? {
        var tempFile: File? = null
        try {
            val inputStream: InputStream = assets.open(assetName)
            tempFile = File.createTempFile("temp_pdf", ".pdf", cacheDir)
            tempFile.deleteOnExit()
            val outputStream: OutputStream = FileOutputStream(tempFile)
            val buffer = ByteArray(1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PDFViewer", "파일 복사 중 예외: ${e.message}")
        }
        return tempFile
    }

    override fun onDestroy() {
        super.onDestroy()
        document?.destroy()
    }
}
