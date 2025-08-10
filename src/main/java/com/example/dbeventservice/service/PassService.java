package com.example.dbeventservice.service;

import de.brendamour.jpasskit.PKBarcode;
import de.brendamour.jpasskit.PKField;
import de.brendamour.jpasskit.PKPass;
import de.brendamour.jpasskit.enums.PKBarcodeFormat;
import de.brendamour.jpasskit.enums.PKPassType;
import de.brendamour.jpasskit.passes.PKGenericPass;
import de.brendamour.jpasskit.signing.PKFileBasedSigningUtil;
import de.brendamour.jpasskit.signing.PKPassTemplateInMemory;
import de.brendamour.jpasskit.signing.PKSigningInformationUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;

@Service
public class PassService {

    @Value("${pass.typeIdentifier}") private String passTypeId;
    @Value("${pass.teamIdentifier}") private String teamId;
    @Value("${pass.organizationName}") private String orgName;
    @Value("${pass.p12.path}") private String p12Path;
    @Value("${pass.p12.password}") private String p12Password;
    @Value("${pass.wwdr.path:}") private String wwdrPathOptional; // required for signing

    // --- Decode PASS_P12_BASE64 -> /tmp/pass.p12 and override p12Path
    @PostConstruct
    void initP12FromEnv() throws Exception {
        String b64 = System.getenv("PASS_P12_BASE64");
        if (b64 == null || b64.isBlank()) {
            System.out.println("[P12] PASS_P12_BASE64 not set; using property path: " + p12Path);
            return;
        }

        byte[] bytes = Base64.getMimeDecoder().decode(b64); // handles newlines
        Path out = Path.of("/tmp/pass.p12");
        Files.write(out, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        this.p12Path = out.toString();
        System.out.println("[P12] Wrote " + p12Path + " (" + bytes.length + " bytes)");

        // SHA-256 so you can compare with your local: 3b45dcf0bd5f6a0e...88149
        String sha = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        System.out.println("[P12] SHA256=" + sha);

        // Hard-load test (fail early if bad password/file)
        try (var is = Files.newInputStream(out)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(is, p12Password.toCharArray());
            System.out.println("[P12] ✅ P12 OK, aliases=" + Collections.list(ks.aliases()));
        }
    }

    public byte[] buildEventPass(
            Long ticketId,
            String title,
            LocalDateTime eventStart,
            String location,
            String code
    ) throws Exception {

        if (p12Path == null || p12Path.isBlank()) throw new IOException("pass.p12.path is empty/null");
        if (p12Password == null || p12Password.isBlank()) throw new IOException("pass.p12.password is empty/null");
        if (wwdrPathOptional == null || wwdrPathOptional.isBlank())
            throw new IOException("pass.wwdr.path is missing. Upload AppleWWDRCAG4.cer and set pass.wwdr.path");

        // Quick visibility
        Path p12 = Path.of(p12Path);
        System.out.println("[DEBUG] P12 path: " + p12.toAbsolutePath());
        System.out.println("[DEBUG] Exists? " + Files.exists(p12));
        System.out.println("[DEBUG] Size: " + (Files.exists(p12) ? Files.size(p12) : -1) + " bytes");

        ZonedDateTime zdt = eventStart.atZone(ZoneId.of("America/Chicago"));

        PKPass pass = PKPass.builder()
                .pass(
                        PKGenericPass.builder()
                                .passType(PKPassType.PKEventTicket)
                                .primaryFieldBuilder(PKField.builder().key("event").label("Event").value(title))
                                .secondaryFieldBuilder(PKField.builder().key("when").label("When").value(formatWhen(zdt)))
                                .auxiliaryFieldBuilder(PKField.builder().key("loc").label("Where").value(location))
                                .headerFieldBuilder(PKField.builder().key("code").label("Code").value(shortCode(code)))
                )
                .barcodeBuilder(PKBarcode.builder()
                        .format(PKBarcodeFormat.PKBarcodeFormatQR)
                        .message(code)
                        .messageEncoding(StandardCharsets.ISO_8859_1.name()))
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

        // Template assets (classpath: src/main/resources/Wallet/...)
        PKPassTemplateInMemory tmpl = new PKPassTemplateInMemory();
        tmpl.addFile(PKPassTemplateInMemory.PK_ICON,        res("/Wallet/icon.png"));
        tmpl.addFile(PKPassTemplateInMemory.PK_ICON_RETINA, res("/Wallet/icon@2x.png"));
        safeAdd(tmpl, PKPassTemplateInMemory.PK_LOGO,        "/Wallet/logo.png");
        safeAdd(tmpl, PKPassTemplateInMemory.PK_LOGO_RETINA, "/Wallet/logo@2x.png");

        // Sign
        var util = new PKSigningInformationUtil();
        try (var p12is  = Files.newInputStream(Path.of(p12Path));
             var wwdris = Files.newInputStream(Path.of(wwdrPathOptional))) {

            var signingInfo = util.loadSigningInformationFromPKCS12AndIntermediateCertificate(
                    p12is, p12Password, wwdris
            );

            var signer = new PKFileBasedSigningUtil();
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

    private InputStream res(String path) throws IOException {
        String clean = path.startsWith("/") ? path.substring(1) : path;
        ClassPathResource cpr = new ClassPathResource(clean);
        if (!cpr.exists()) throw new IOException("Missing resource: " + path);
        return cpr.getInputStream();
    }

    private void safeAdd(PKPassTemplateInMemory tmpl, String name, String path) {
        try {
            String clean = path.startsWith("/") ? path.substring(1) : path;
            ClassPathResource cpr = new ClassPathResource(clean);
            if (cpr.exists()) try (InputStream is = cpr.getInputStream()) { tmpl.addFile(name, is); }
        } catch (IOException ignored) {}
    }
}
