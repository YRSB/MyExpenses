package org.totschnig.myexpenses.util

import android.view.View
import com.itextpdf.text.DocumentException
import com.itextpdf.text.Font
import com.itextpdf.text.Phrase
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import org.totschnig.myexpenses.util.LazyFontSelector.FontType
import org.totschnig.myexpenses.util.crashreporting.CrashHandler.Companion.report
import java.io.File
import java.io.IOException
import java.util.Arrays
import java.util.Locale
import java.util.regex.Pattern
import androidx.core.text.layoutDirection
import com.itextpdf.text.Chunk
import com.itextpdf.text.Element

class PdfHelper(private val baseFontSize: Float, memoryClass: Int) {
    private var lfs: LazyFontSelector?
    private val fNormal: Font by lazy { convertFallback(FontType.NORMAL) }
    private val fTitle: Font by lazy { convertFallback(FontType.TITLE) }
    private val fHeader: Font by lazy { convertFallback(FontType.HEADER) }
    private val fBold: Font by lazy { convertFallback(FontType.BOLD) }
    private val fItalic: Font by lazy { convertFallback(FontType.ITALIC) }
    private val fUnderline: Font by lazy { convertFallback(FontType.UNDERLINE) }
    private val fIncome: Font by lazy { convertFallback(FontType.INCOME) }
    private val fExpense: Font by lazy { convertFallback(FontType.EXPENSE) }
    private val fTransfer: Font by lazy { convertFallback(FontType.TRANSFER) }
    private val fIncomeBold: Font by lazy { convertFallback(FontType.INCOME_BOLD) }
    private val fExpenseBold: Font by lazy { convertFallback(FontType.EXPENSE_BOLD) }

    private val layoutDirectionFromLocaleIsRTL: Boolean

    init {
        val l = Locale.getDefault()
        layoutDirectionFromLocaleIsRTL = (l.layoutDirection == View.LAYOUT_DIRECTION_RTL)
        lfs = if (memoryClass >= 32) {
            //we want the Default Font to be used first
            try {
                val dir = File("/system/fonts")
                val files = dir.listFiles { _: File?, filename: String ->
                    //NotoSans*-Regular.otf files found not to work:
                    (filename.endsWith("ttf") || filename.endsWith("ttc"))
                            //BaseFont.charExists finds chars that are not visible in PDF
                            //NotoColorEmoji.ttf and SamsungColorEmoji.ttf are known not to work
                            && !filename.contains("ColorEmoji")
                            //vivo devices report 43653f0a14cf41b707579c51642b7046
                            && !filename.startsWith("NEX-")
                            //seen on Pixel with Android 15 Beta
                            && filename != "NotoSansCJK-Regular.ttc"
                            //cannot be embedded due to licensing restrictions: report 55cdc91d2279b63b23419bc9cec1a21d
                            && filename != "Kindle_Symbol.ttf"

                }
                Arrays.sort(files!!) { f1: File, f2: File ->
                    val n1 = f1.name
                    val n2 = f2.name
                    if (n1 == "DroidSans.ttf") {
                        return@sort -1
                    } else if (n2 == "DroidSans.ttf") {
                        return@sort 1
                    }
                    if (n1.startsWith("Droid")) {
                        if (n2.startsWith("Droid")) {
                            return@sort n1.compareTo(n2)
                        } else {
                            return@sort -1
                        }
                    } else if (n2.startsWith("Droid")) {
                        return@sort 1
                    }
                    n1.compareTo(n2)
                }
                LazyFontSelector(files, baseFontSize)
            } catch (e: Exception) {
                report(e)
                null
            }
        } else null
    }

    private fun convertFallback(fontType: FontType) = Font(
        Font.FontFamily.TIMES_ROMAN,
        fontType.factor * baseFontSize,
        fontType.style,
        fontType.color
    )

    @Throws(DocumentException::class, IOException::class)
    fun printToCell(text: String, font: FontType = FontType.NORMAL, border: Int = Rectangle.NO_BORDER): PdfPCell {
        val cell = PdfPCell(print(text, font))
        if (hasAnyRtl(text)) {
            cell.runDirection = PdfWriter.RUN_DIRECTION_RTL
        }
        cell.setPadding(5f)
        cell.border = border
        cell.verticalAlignment = Element.ALIGN_MIDDLE
        return cell
    }

    @Throws(DocumentException::class, IOException::class)
    fun printToCell(phrase: Phrase, border: Int = Rectangle.NO_BORDER): PdfPCell {
        val cell = PdfPCell(phrase)
        if (hasAnyRtl(phrase.content)) {
            cell.runDirection = PdfWriter.RUN_DIRECTION_RTL
        }
        cell.border = border
        return cell
    }

    fun List<Chunk>.join() = Phrase().also { phrase ->
        forEach { phrase.add(it) }
    }

    /**
     * variant of [print] that returns list of Chunks instead of Phrase
     */
    @Throws(DocumentException::class, IOException::class)
    fun print0(text: String, font: FontType): List<Chunk> = lfs?.process(text, font) ?: listOf(
        when (font) {
            FontType.BOLD -> Chunk(text, fBold)
            FontType.EXPENSE -> Chunk(text, fExpense)
            FontType.HEADER -> Chunk(text, fHeader)
            FontType.INCOME -> Chunk(text, fIncome)
            FontType.ITALIC -> Chunk(text, fItalic)
            FontType.NORMAL -> Chunk(text, fNormal)
            FontType.TITLE -> Chunk(text, fTitle)
            FontType.UNDERLINE -> Chunk(text, fUnderline)
            FontType.INCOME_BOLD -> Chunk(text, fIncomeBold)
            FontType.EXPENSE_BOLD -> Chunk(text, fExpenseBold)
            FontType.TRANSFER ->  Chunk(text, fTransfer)
        }
    )

    @Throws(DocumentException::class, IOException::class)
    fun print(text: String, font: FontType): Phrase = lfs?.process(text, font)?.join() ?: when (font) {
        FontType.BOLD -> Phrase(text, fBold)
        FontType.EXPENSE -> Phrase(text, fExpense)
        FontType.HEADER -> Phrase(text, fHeader)
        FontType.INCOME -> Phrase(text, fIncome)
        FontType.ITALIC -> Phrase(text, fItalic)
        FontType.NORMAL -> Phrase(text, fNormal)
        FontType.TITLE -> Phrase(text, fTitle)
        FontType.UNDERLINE -> Phrase(text, fUnderline)
        FontType.INCOME_BOLD -> Phrase(text, fIncomeBold)
        FontType.EXPENSE_BOLD -> Phrase(text, fExpenseBold)
        FontType.TRANSFER -> Phrase(text, fTransfer)
    }

    fun emptyCell(border: Int = Rectangle.NO_BORDER) = PdfPCell().apply {
        this.border = border
    }

    fun newTable(numColumns: Int) = PdfPTable(numColumns).apply {
        if (layoutDirectionFromLocaleIsRTL) {
            this.runDirection = PdfWriter.RUN_DIRECTION_RTL
        }
    }

    fun addNestedCells(table: PdfPTable, cell1: PdfPCell?, cell2: PdfPCell?, border: Int = Rectangle.TOP + Rectangle.RIGHT) {
        if (cell1 != null && cell2 != null) {
            val nested = PdfPTable(1)
            nested.setWidthPercentage(100f)
            nested.addCell(cell1)
            nested.addCell(cell2)
            val cell = PdfPCell(nested)
            cell.border = border
            table.addCell(cell)
        } else if(cell1 != null) {
            table.addCell(cell1.also { it.border = border })
        } else if(cell2 != null) {
            table.addCell(cell2.also { it.border = border })
        } else {
            table.addCell(emptyCell(border))
        }
    }

    companion object {
        private val HAS_ANY_RTL_RE: Pattern = Pattern.compile(".*[\\p{InArabic}\\p{InHebrew}].*")

        @JvmStatic
        fun hasAnyRtl(str: String): Boolean {
            return HAS_ANY_RTL_RE.matcher(str).matches()
        }
    }
}
