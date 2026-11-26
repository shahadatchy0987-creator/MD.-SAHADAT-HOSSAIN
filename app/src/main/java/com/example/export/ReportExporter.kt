package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.ReportDataset
import com.example.i18n.Formatters
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReportExporter {

  fun writePdfToStream(dataset: ReportDataset, out: OutputStream) {
    val doc = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 32f
    var pageNumber = 1

    fun startPage(): Pair<PdfDocument.Page, android.graphics.Canvas> {
      val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
      val page = doc.startPage(info)
      return page to page.canvas
    }

    val titlePaint =
      Paint().apply {
        color = Color.rgb(13, 59, 54)
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val subPaint =
      Paint().apply {
        color = Color.rgb(75, 99, 96)
        textSize = 11f
      }
    val headerBgPaint =
      Paint().apply {
        color = Color.rgb(13, 59, 54)
        style = Paint.Style.FILL
      }
    val headerTextPaint =
      Paint().apply {
        color = Color.WHITE
        textSize = 10f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
    val rowTextPaint =
      Paint().apply {
        color = Color.rgb(15, 31, 29)
        textSize = 9.5f
      }
    val linePaint =
      Paint().apply {
        color = Color.rgb(210, 224, 221)
        strokeWidth = 1f
      }
    val summaryPaint =
      Paint().apply {
        color = Color.rgb(13, 59, 54)
        textSize = 11f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }

    var (page, canvas) = startPage()
    var y = 44f

    canvas.drawText("Plusnet Accounts", margin, y, titlePaint)
    y += 18f
    canvas.drawText(dataset.title, margin, y, summaryPaint)
    y += 14f
    canvas.drawText("${dataset.subtitle}  •  Generated ${Formatters.currentDateFormatted()}", margin, y, subPaint)
    y += 20f

    val colCount = dataset.headers.size.coerceAtLeast(1)
    val tableWidth = pageWidth - margin * 2
    val colWidth = tableWidth / colCount

    fun drawTableHeader() {
      canvas.drawRect(margin, y, pageWidth - margin, y + 22f, headerBgPaint)
      dataset.headers.forEachIndexed { idx, h ->
        canvas.drawText(h.take(18), margin + idx * colWidth + 4f, y + 15f, headerTextPaint)
      }
      y += 26f
    }

    drawTableHeader()

    if (dataset.rows.isEmpty()) {
      canvas.drawText("No records found for this period.", margin + 4f, y + 14f, subPaint)
      y += 24f
    } else {
      for (row in dataset.rows) {
        if (y > pageHeight - 80f) {
          doc.finishPage(page)
          pageNumber++
          val next = startPage()
          page = next.first
          canvas = next.second
          y = 44f
          drawTableHeader()
        }
        row.forEachIndexed { idx, cell ->
          canvas.drawText(cell.take(20), margin + idx * colWidth + 4f, y + 12f, rowTextPaint)
        }
        y += 18f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 4f
      }
    }

    y += 14f
    if (y > pageHeight - 80f) {
      doc.finishPage(page)
      pageNumber++
      val next = startPage()
      page = next.first
      canvas = next.second
      y = 44f
    }
    dataset.summaryLines.forEach { (k, v) ->
      canvas.drawText("$k: $v", margin, y, summaryPaint)
      y += 16f
    }

    doc.finishPage(page)
    doc.writeTo(out)
    doc.close()
  }

  fun writeXlsxToStream(dataset: ReportDataset, out: OutputStream) {
    val allRows = buildList {
      add(listOf("Plusnet Accounts — ${dataset.title}"))
      add(listOf(dataset.subtitle, "Generated: ${Formatters.currentDateFormatted()}"))
      add(emptyList())
      add(dataset.headers)
      addAll(dataset.rows)
      add(emptyList())
      dataset.summaryLines.forEach { (k, v) -> add(listOf(k, v)) }
    }

    val sheetXml = buildString {
      append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
      append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
      append("<sheetData>")
      allRows.forEachIndexed { rowIndex, cols ->
        val r = rowIndex + 1
        append("<row r=\"$r\">")
        cols.forEachIndexed { colIndex, value ->
          val cellRef = "${colLetter(colIndex)}$r"
          val escaped = escapeXml(value)
          append("<c r=\"$cellRef\" t=\"inlineStr\"><is><t>$escaped</t></is></c>")
        }
        append("</row>")
      }
      append("</sheetData></worksheet>")
    }

    val contentTypes =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    val rels =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    val workbook =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Report" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

    val workbookRels =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

    ZipOutputStream(out).use { zip ->
      fun addEntry(name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
      }
      addEntry("[Content_Types].xml", contentTypes)
      addEntry("_rels/.rels", rels)
      addEntry("xl/workbook.xml", workbook)
      addEntry("xl/_rels/workbook.xml.rels", workbookRels)
      addEntry("xl/worksheets/sheet1.xml", sheetXml)
    }
  }

  fun exportAndShare(
    context: Context,
    dataset: ReportDataset,
    isPdf: Boolean,
  ) {
    val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val safeName = dataset.title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    val ext = if (isPdf) "pdf" else "xlsx"
    val file = File(exportsDir, "${safeName}_${System.currentTimeMillis()}.$ext")
    FileOutputStream(file).use { out ->
      if (isPdf) writePdfToStream(dataset, out) else writeXlsxToStream(dataset, out)
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mime =
      if (isPdf) "application/pdf"
      else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Plusnet Accounts — ${dataset.title}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    context.startActivity(Intent.createChooser(intent, dataset.title))
  }

  private fun colLetter(index: Int): String {
    var i = index
    val sb = StringBuilder()
    while (i >= 0) {
      sb.append(('A' + (i % 26)))
      i = (i / 26) - 1
    }
    return sb.reverse().toString()
  }

  private fun escapeXml(input: String): String =
    input
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
}
