package com.example.dbeventservice.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

public class QRCodeGenerator {

    private static final String OUTPUT_DIR = "uploads/qr";

    public static String generate(Long ticketId, String email) {
        try {
            String fileName = "ticket_" + ticketId + "_" + UUID.randomUUID() + ".png";
            Path path = Paths.get(OUTPUT_DIR, fileName);

            // Ensure directory exists
            Files.createDirectories(path.getParent());

            String qrText = "TICKET:" + ticketId + ":" + email;
            BitMatrix matrix = new QRCodeWriter().encode(qrText, BarcodeFormat.QR_CODE, 300, 300);
            MatrixToImageWriter.writeToPath(matrix, "PNG", path);

            // Return relative URL path (used in frontend)
            return "/qr/" + fileName;

        } catch (Exception e) {
            System.out.println("❌ QR code generation failed: " + e.getMessage());
            return null;
        }
    }
}
