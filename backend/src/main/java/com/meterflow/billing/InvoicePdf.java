package com.meterflow.billing;

import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;

@Service
public class InvoicePdf {
  private final BillingService billing;

  public InvoicePdf(BillingService billing) {
    this.billing = billing;
  }

  public byte[] render(UUID org, UUID id) throws IOException {
    var invoice = billing.detail(org, id);
    try (var doc = new PDDocument();
        var out = new ByteArrayOutputStream()) {
      var page = new PDPage();
      doc.addPage(page);
      try (var content = new PDPageContentStream(doc, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
        content.setLeading(23);
        content.newLineAtOffset(48, 740);
        write(
            content,
            "MeterFlow | Invoice " + id.toString().substring(0, 8).toUpperCase(Locale.ROOT));
        write(
            content,
            "Period: "
                + invoice.get("period_start")
                + " to "
                + invoice.get("period_end")
                + " (exclusive)");
        write(content, "Currency: EUR | Status: " + invoice.get("status"));
        write(content, "");
        @SuppressWarnings("unchecked")
        var lines = (List<Map<String, Object>>) invoice.get("lines");
        for (var line : lines)
          write(
              content,
              line.get("description")
                  + " | "
                  + line.get("quantity")
                  + " x "
                  + line.get("unit_price")
                  + " = EUR "
                  + line.get("amount"));
        write(content, "");
        write(content, "Total: EUR " + invoice.get("total"));
        write(
            content, "Portfolio invoice. Tax calculation and payment collection are not included.");
        content.endText();
      }
      doc.save(out);
      return out.toByteArray();
    }
  }

  private void write(PDPageContentStream stream, String text) throws IOException {
    stream.showText(text);
    stream.newLine();
  }
}
