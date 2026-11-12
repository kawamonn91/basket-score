package com.ttech.basketscore.report

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExportTest {

    @Test
    fun cellsWithCommasQuotesOrNewlinesAreQuoted() {
        assertEquals("abc", CsvExport.cell("abc"))
        assertEquals("\"a,b\"", CsvExport.cell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.cell("say \"hi\""))
        assertEquals("\"1\n2\"", CsvExport.cell("1\n2"))
        assertEquals("", CsvExport.cell(null))
        assertEquals("1,Stars（コピー）,\"#4, #5\"", CsvExport.line(1, "Stars（コピー）", "#4, #5"))
    }
}
