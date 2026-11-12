package com.ttech.basketscore.sheet

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** スコアシートの PDF（F-901）と、共有・印刷（F-904） */
object ScoresheetPdf {

    /** PDF を書き出す（A4 縦。160 点を超えたら 2 枚目に続ける） */
    fun write(sheet: Scoresheet, file: File) {
        val doc = PdfDocument()
        try {
            repeat(sheet.pages) { page ->
                val info = PdfDocument.PageInfo.Builder(ScoresheetRenderer.WIDTH.toInt(), ScoresheetRenderer.HEIGHT.toInt(), page + 1).create()
                val p = doc.startPage(info)
                ScoresheetRenderer.draw(p.canvas, sheet, page)
                doc.finishPage(p)
            }
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** 共有用のファイル（アプリのキャッシュの pdf/ の下。FileProvider で渡す） */
    fun file(context: Context, name: String): File = File(File(context.cacheDir, "pdf"), name)

    fun shareIntent(context: Context, file: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    /** Android の印刷（プリンター・PDF への保存）に、書き出した PDF を渡す */
    fun print(context: Context, file: File, jobName: String) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attrs = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
        manager.print(jobName, FileAdapter(file, jobName), attrs)
    }

    private class FileAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            callback.onLayoutFinished(
                PrintDocumentInfo.Builder("$name.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),
                oldAttributes != newAttributes,
            )
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            try {
                file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
}
