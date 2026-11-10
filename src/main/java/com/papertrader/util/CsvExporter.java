package com.papertrader.util;

import com.papertrader.interfaces.Exportable;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/** Writes any list of Exportable objects to a CSV file. Works for Transactions, Holdings, etc. */
public final class CsvExporter {

    private CsvExporter() {
    }

    public static void export(List<? extends Exportable> rows, String header, File file) throws IOException {
        try (PrintWriter out = new PrintWriter(file, "UTF-8")) {
            out.println(header);
            for (Exportable row : rows) {
                out.println(row.toCsvRow());   // polymorphism: each class formats itself
            }
        }
    }
}
