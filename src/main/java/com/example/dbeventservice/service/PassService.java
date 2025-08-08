package com.example.dbeventservice.service;

import de.brendamour.jpasskit.signing.PKPassTemplateInMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

        // Relevant date must be java.util.Date
        ZonedDateTime zdt = eventStart.atZone(ZoneId.of("America/Chicago"));

        // ---- Build the PKPass entirely via builders (no protected ctors, no addXxxField) ----
        de.brendamour.jpasskit.PKPass pass =
                de.brendamour.jpasskit.PKPass.builder()
                        .pass( // choose a pass style; here we use "Event Ticket"
                                de.brendamour.jpasskit.passes.PKGenericPass.builder()
                                        .passType(de.brendamour.jpasskit.enums.PKPassType.PKEventTicket)
                                        .primaryFieldBuilder(
                                                de.brendamour.jpasskit.PKField.builder()
                                                        .key("event").label("Event").value(title)
                                        )
                                        .secondaryFieldBuilder(
                                                de.brendamour.jpasskit.PKField.builder()
                                                        .key("when").label("When").value(formatWhen(zdt))
                                        )
                                        .auxiliaryFieldBuilder(
                                                de.brendamour.jpasskit.PKField.builder()
                                                        .key("loc").label("Where").value(location)
                                        )
                                        .headerFieldBuilder(
                                                de.brendamour.jpasskit.PKField.builder()
                                                        .key("code").label("Code").value(shortCode(code))
                                        )
                        )
                        .barcodeBuilder(
                                de.brendamour.jpasskit.PKBarcode.builder()
                                        .format(de.brendamour.jpasskit.enums.PKBarcodeFormat.PKBarcodeFormatQR)
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
        de.brendamour.jpasskit.signing.PKPassTemplateInMemory tmpl =
                new de.brendamour.jpasskit.signing.PKPassTemplateInMemory();

        // Adjust paths to where your PNGs actually are.
        // If your files are at src/main/resources/logo.png and logo@2x.png, this works:
        tmpl.addFile(PKPassTemplateInMemory.PK_ICON,
                resStream("/Wallet/base/logo.png"));
        tmpl.addFile(PKPassTemplateInMemory.PK_ICON_RETINA,
                resStream("/Wallet/base/logo@2x.png"));

        safeAdd(tmpl, PKPassTemplateInMemory.PK_LOGO,
                "/Wallet/base/logo.png");
        safeAdd(tmpl, PKPassTemplateInMemory.PK_LOGO_RETINA,
                "/Wallet/base/logo@2x.png");



        // ---- Sign & zip (your jpasskit 0.4.2 signature) ----
        de.brendamour.jpasskit.signing.PKSigningInformationUtil util =
                new de.brendamour.jpasskit.signing.PKSigningInformationUtil();

        de.brendamour.jpasskit.signing.PKSigningInformation signingInfo =
                util.loadSigningInformationFromPKCS12AndIntermediateCertificate(
                        p12Path,                                                // <-- String path
                        p12Password,
                        (wwdrPathOptional == null || wwdrPathOptional.isBlank())
                                ? null
                                : wwdrPathOptional                                // <-- String path (or null)
                );

        de.brendamour.jpasskit.signing.PKFileBasedSigningUtil signer =
                new de.brendamour.jpasskit.signing.PKFileBasedSigningUtil();

        return signer.createSignedAndZippedPkPassArchive(
                pass,
                tmpl,          // PKPassTemplateInMemory
                signingInfo    // PKSigningInformation
        );

    }

    // === helpers ===
    private static String shortCode(String code) {
        return (code != null && code.length() > 10) ? code.substring(0, 10) : code;
    }

    private static String formatWhen(ZonedDateTime zdt) {
        return zdt.format(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a"));
    }

    private InputStream res(String path) throws IOException {
        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) throw new IOException("Missing resource: " + path);
        return is;
    }


    private InputStream resStream(String path) throws IOException {
        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) {
            throw new IOException("Missing resource: " + path);
        }
        return is;
    }

    private void safeAdd(PKPassTemplateInMemory tmpl, String name, String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is != null) {
                tmpl.addFile(name, is); // ✅ uses InputStream
            }
        } catch (IOException ignored) {}
    }


}
