package com.example.dbeventservice.service;

import de.brendamour.jpasskit.PKBarcode;
import de.brendamour.jpasskit.PKField;
import de.brendamour.jpasskit.PKPass;
import de.brendamour.jpasskit.enums.PKBarcodeFormat;
import de.brendamour.jpasskit.enums.PKPassType;
import de.brendamour.jpasskit.passes.PKGenericPass;
import de.brendamour.jpasskit.signing.PKFileBasedSigningUtil;
import de.brendamour.jpasskit.signing.PKPassTemplateInMemory;
import de.brendamour.jpasskit.signing.PKSigningInformation;
import de.brendamour.jpasskit.signing.PKSigningInformationUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PassService {

    @Value("${pass.typeIdentifier}") private String passTypeId;
    @Value("${pass.teamIdentifier}") private String teamId;
    @Value("${pass.organizationName}") private String orgName;
    @Value("${pass.p12.path}") private String p12Path;
    @Value("${pass.p12.password}") private String p12Password;
    @Value("${pass.wwdr.path:}") private String wwdrPathOptional; // optional

    public byte[] buildEventPass(
            Long ticketId,
            String title,
            LocalDateTime eventStart,
            String location,
            String code
    ) throws Exception {

        ZonedDateTime zdt = eventStart.atZone(ZoneId.of("America/Chicago"));

        // ---- Build pass ----
        PKPass pass = PKPass.builder()
                .pass(
                        PKGenericPass.builder()
                                .passType(PKPassType.PKEventTicket)
                                .primaryFieldBuilder(
                                        PKField.builder().key("event").label("Event").value(title)
                                )
                                .secondaryFieldBuilder(
                                        PKField.builder().key("when").label("When").value(formatWhen(zdt))
                                )
                                .auxiliaryFieldBuilder(
                                        PKField.builder().key("loc").label("Where").value(location)
                                )
                                .headerFieldBuilder(
                                        PKField.builder().key("code").label("Code").value(shortCode(code))
                                )
                )
                .barcodeBuilder(
                        PKBarcode.builder()
                                .format(PKBarcodeFormat.PKBarcodeFormatQR)
                                .message(code)
                                .messageEncoding(StandardCharsets.ISO_8859_1.name())
                )
                .formatVersion(1)
                .passTypeIdentifier(passTypeId)
                .teamIdentifier(teamId)
                .organizationName(orgName)
                .description("WKND Event Ticket")
                .serialNumber("T-" + ticketId)
                .relevantDate(java.util.Date.from(zdt.toInstant()))
                .backgroundColor("rgb(0,0,0)")
                .foregroundColor("rgb(255,255,255)")
                .labelColor("rgb(255,215,0)")
                .build();

        // ---- Template (icons/logos) ----
        // Place files at: src/main/resources/Wallet/{icon.png,icon@2x.png,logo.png,logo@2x.png}
        PKPassTemplateInMemory tmpl = new PKPassTemplateInMemory();
        tmpl.addFile(PKPassTemplateInMemory.PK_ICON,        res("/Wallet/icon.png"));
        tmpl.addFile(PKPassTemplateInMemory.PK_ICON_RETINA, res("/Wallet/icon@2x.png"));
        safeAdd(tmpl, PKPassTemplateInMemory.PK_LOGO,        "/Wallet/logo.png");
        safeAdd(tmpl, PKPassTemplateInMemory.PK_LOGO_RETINA, "/Wallet/logo@2x.png");

        // ---- Sign & zip (require WWDR; use InputStreams) ----
        var util = new de.brendamour.jpasskit.signing.PKSigningInformationUtil();

// Validate paths for clear errors
        if (p12Path == null || p12Path.isBlank()) throw new IOException("pass.p12.path is empty/null");
        if (p12Password == null || p12Password.isBlank()) throw new IOException("pass.p12.password is empty/null");
        if (wwdrPathOptional == null || wwdrPathOptional.isBlank())
            throw new IOException("pass.wwdr.path is missing. Upload AppleWWDRCAG4.cer and set pass.wwdr.path");

// Open streams from filesystem (Render secrets live under /etc/secrets)
        try (var p12is  = new java.io.FileInputStream(p12Path);
             var wwdris = new java.io.FileInputStream(wwdrPathOptional)) {

            var signingInfo = util.loadSigningInformationFromPKCS12AndIntermediateCertificate(
                    p12is, p12Password, wwdris
            );

            var signer = new de.brendamour.jpasskit.signing.PKFileBasedSigningUtil();
            return signer.createSignedAndZippedPkPassArchive(pass, tmpl, signingInfo);
        }
    }

    // === helpers ===

    private static String shortCode(String code) {
        return (code != null && code.length() > 10) ? code.substring(0, 10) : code;
    }

    private static String formatWhen(ZonedDateTime zdt) {
        return zdt.format(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a"));
    }

    /** Load a classpath resource reliably from the fat JAR. */
    private InputStream res(String path) throws IOException {
        String clean = path.startsWith("/") ? path.substring(1) : path; // e.g. "Wallet/icon.png"
        ClassPathResource cpr = new ClassPathResource(clean);
        if (!cpr.exists()) throw new IOException("Missing resource: " + path);
        return cpr.getInputStream();
    }

    /** Optional add: only adds if the file exists on classpath. */
    private void safeAdd(PKPassTemplateInMemory tmpl, String name, String path) {
        try {
            String clean = path.startsWith("/") ? path.substring(1) : path;
            ClassPathResource cpr = new ClassPathResource(clean);
            if (cpr.exists()) {
                try (InputStream is = cpr.getInputStream()) {
                    tmpl.addFile(name, is);
                }
            }
        } catch (IOException ignored) {}
    }
}
