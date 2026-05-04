package com.yapeseguro.application.services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.yapeseguro.api.dto.response.ReceiptResponse;
import com.yapeseguro.api.dto.response.ReceiptValidationResponse;
import com.yapeseguro.infrastructure.persistence.entities.BusinessProfileEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionEntity;
import com.yapeseguro.infrastructure.persistence.entities.TransactionReceiptEntity;
import com.yapeseguro.infrastructure.persistence.entities.UserEntity;
import com.yapeseguro.infrastructure.persistence.entities.WalletEntity;
import com.yapeseguro.infrastructure.persistence.repositories.BusinessProfileRepository;
import com.yapeseguro.infrastructure.persistence.repositories.TransactionReceiptRepository;
import com.yapeseguro.infrastructure.persistence.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final TransactionRepository transactionRepository;
    private final TransactionReceiptRepository receiptRepository;
    private final BusinessProfileRepository businessProfileRepository;

    @Transactional
    public ReceiptResponse generateReceiptForTransaction(UUID transactionId) {
        TransactionEntity transaction = transactionRepository.findDetailedById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        validateTransactionCanHaveReceipt(transaction);

        TransactionReceiptEntity receipt = receiptRepository.findByTransaction(transaction)
                .orElseGet(() -> buildReceipt(transaction));

        refreshGeneratedContent(receipt);

        return toResponse(receiptRepository.save(receipt));
    }

    @Transactional
    public ReceiptResponse getOrGenerateReceiptByTransaction(
            UUID transactionId,
            String username
    ) {
        TransactionEntity transaction = transactionRepository.findDetailedById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        validateTransactionBelongsToUsername(transaction, username);
        validateTransactionCanHaveReceipt(transaction);

        TransactionReceiptEntity receipt = receiptRepository.findByTransaction(transaction)
                .orElseGet(() -> buildReceipt(transaction));

        refreshGeneratedContent(receipt);

        return toResponse(receiptRepository.save(receipt));
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getReceiptByTransaction(
            UUID transactionId,
            String username
    ) {
        TransactionEntity transaction = transactionRepository.findDetailedById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        validateTransactionBelongsToUsername(transaction, username);

        TransactionReceiptEntity receipt = receiptRepository.findByTransaction(transaction)
                .orElseThrow(() -> new IllegalArgumentException("Comprobante no encontrado"));

        return toResponse(receipt);
    }

    @Transactional
    public String getReceiptHtmlByTransaction(
            UUID transactionId,
            String username
    ) {
        TransactionEntity transaction = transactionRepository.findDetailedById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        validateTransactionBelongsToUsername(transaction, username);
        validateTransactionCanHaveReceipt(transaction);

        TransactionReceiptEntity receipt = receiptRepository.findByTransaction(transaction)
                .orElseGet(() -> buildReceipt(transaction));

        refreshGeneratedContent(receipt);

        return receiptRepository.save(receipt).getReceiptHtml();
    }

    @Transactional
    public byte[] getReceiptPdfByTransaction(
            UUID transactionId,
            String username
    ) {
        TransactionEntity transaction = transactionRepository.findDetailedById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transacción no encontrada"));

        validateTransactionBelongsToUsername(transaction, username);
        validateTransactionCanHaveReceipt(transaction);

        TransactionReceiptEntity receipt = receiptRepository.findByTransaction(transaction)
                .orElseGet(() -> buildReceipt(transaction));

        refreshGeneratedContent(receipt);

        int currentPrintedCount = receipt.getPrintedCount() != null
                ? receipt.getPrintedCount()
                : 0;

        receipt.setPrintedCount(currentPrintedCount + 1);

        TransactionReceiptEntity savedReceipt = receiptRepository.save(receipt);

        return buildSimplePdf(savedReceipt);
    }

    @Transactional(readOnly = true)
    public ReceiptValidationResponse validateReceipt(String receiptNumber) {
        TransactionReceiptEntity receipt = receiptRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new IllegalArgumentException("Comprobante no encontrado"));

        TransactionEntity transaction = receipt.getTransaction();

        return ReceiptValidationResponse.builder()
                .valid(true)
                .receiptNumber(receipt.getReceiptNumber())
                .transactionReference(transaction.getReference())
                .businessName(receipt.getBusinessName())
                .businessRuc(receipt.getBusinessRuc())
                .customerName(receipt.getCustomerName())
                .amount(receipt.getAmount())
                .currency(receipt.getCurrency())
                .transactionType(transaction.getType().name())
                .transactionStatus(transaction.getStatus().name())
                .marketplaceStatus(transaction.getMarketplaceStatus().name())
                .issuedAt(receipt.getCreatedAt())
                .build();
    }

    private void refreshGeneratedContent(TransactionReceiptEntity receipt) {
        receipt.setQrCodeUrl(generateQrCodeDataUrl(buildValidationPayload(receipt)));
        receipt.setReceiptHtml(buildReceiptHtml(receipt));
        receipt.setReceiptPdfUrl("/receipts/transactions/" + receipt.getTransaction().getId() + "/pdf");
    }

    private TransactionReceiptEntity buildReceipt(TransactionEntity transaction) {
        WalletEntity walletTo = transaction.getWalletTo();

        Optional<BusinessProfileEntity> businessProfile = businessProfileRepository
                .findByBusinessWalletAndActiveTrue(walletTo);

        String businessName = businessProfile
                .map(BusinessProfileEntity::getBusinessName)
                .orElseGet(() -> fullName(walletTo.getUser()));

        String businessRuc = businessProfile
                .map(BusinessProfileEntity::getRuc)
                .orElse(null);

        String customerName = fullName(transaction.getWalletFrom().getUser());

        return TransactionReceiptEntity.builder()
                .transaction(transaction)
                .receiptNumber(generateReceiptNumber())
                .businessName(businessName)
                .businessRuc(businessRuc)
                .customerName(customerName)
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .concept(resolveConcept(transaction))
                .description(transaction.getDescription())
                .printedCount(0)
                .sentWhatsapp(false)
                .build();
    }

    private void validateTransactionCanHaveReceipt(TransactionEntity transaction) {
        boolean validStatus =
                transaction.getStatus() == TransactionEntity.TxStatus.COMPLETED
                        || transaction.getStatus() == TransactionEntity.TxStatus.RELEASED
                        || transaction.getStatus() == TransactionEntity.TxStatus.CANCELLED;

        if (!validStatus) {
            throw new IllegalArgumentException("Solo se puede generar comprobante para transacciones finalizadas");
        }
    }

    private void validateTransactionBelongsToUsername(
            TransactionEntity transaction,
            String username
    ) {
        UserEntity sender = transaction.getWalletFrom().getUser();
        UserEntity recipient = transaction.getWalletTo().getUser();

        boolean belongsToSender =
                username.equals(sender.getEmail())
                        || username.equals(sender.getPhoneNumber());

        boolean belongsToRecipient =
                username.equals(recipient.getEmail())
                        || username.equals(recipient.getPhoneNumber());

        if (!belongsToSender && !belongsToRecipient) {
            throw new IllegalArgumentException("No tienes permiso para ver este comprobante");
        }
    }

    private String buildReceiptHtml(TransactionReceiptEntity receipt) {
        TransactionEntity transaction = receipt.getTransaction();

        return """
                <!doctype html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <title>Comprobante %s</title>
                    <style>
                        body {
                            font-family: Arial, Helvetica, sans-serif;
                            background: #f4f6f8;
                            margin: 0;
                            padding: 32px;
                            color: #111827;
                        }
                        .receipt {
                            max-width: 720px;
                            margin: 0 auto;
                            background: #ffffff;
                            border-radius: 18px;
                            padding: 28px;
                            box-shadow: 0 10px 30px rgba(15, 23, 42, 0.10);
                        }
                        .header {
                            display: flex;
                            justify-content: space-between;
                            gap: 16px;
                            border-bottom: 1px solid #e5e7eb;
                            padding-bottom: 18px;
                            margin-bottom: 22px;
                        }
                        .brand {
                            font-size: 24px;
                            font-weight: 800;
                            letter-spacing: -0.03em;
                        }
                        .status {
                            display: inline-block;
                            padding: 6px 12px;
                            border-radius: 999px;
                            background: #ecfdf5;
                            font-size: 12px;
                            font-weight: 700;
                            color: #065f46;
                        }
                        .amount {
                            font-size: 38px;
                            font-weight: 800;
                            margin: 12px 0 4px;
                        }
                        .grid {
                            display: grid;
                            grid-template-columns: 1fr 1fr;
                            gap: 14px;
                            margin-top: 18px;
                        }
                        .item {
                            background: #f9fafb;
                            border: 1px solid #e5e7eb;
                            border-radius: 12px;
                            padding: 12px;
                        }
                        .label {
                            font-size: 12px;
                            color: #6b7280;
                            margin-bottom: 4px;
                        }
                        .value {
                            font-size: 14px;
                            font-weight: 700;
                            word-break: break-word;
                        }
                        .qr {
                            margin-top: 24px;
                            text-align: center;
                            border-top: 1px solid #e5e7eb;
                            padding-top: 22px;
                        }
                        .qr img {
                            width: 180px;
                            height: 180px;
                        }
                        .footer {
                            margin-top: 18px;
                            font-size: 12px;
                            color: #6b7280;
                            text-align: center;
                        }
                    </style>
                </head>
                <body>
                    <main class="receipt">
                        <section class="header">
                            <div>
                                <div class="brand">YapeSeguro</div>
                                <div>Comprobante de operación</div>
                            </div>
                            <div>
                                <span class="status">%s</span>
                            </div>
                        </section>

                        <section>
                            <div class="label">Monto</div>
                            <div class="amount">%s %s</div>
                            <div>%s</div>
                        </section>

                        <section class="grid">
                            <div class="item">
                                <div class="label">N.º comprobante</div>
                                <div class="value">%s</div>
                            </div>
                            <div class="item">
                                <div class="label">Referencia</div>
                                <div class="value">%s</div>
                            </div>
                            <div class="item">
                                <div class="label">Comercio / receptor</div>
                                <div class="value">%s</div>
                            </div>
                            <div class="item">
                                <div class="label">RUC</div>
                                <div class="value">%s</div>
                            </div>
                            <div class="item">
                                <div class="label">Cliente / pagador</div>
                                <div class="value">%s</div>
                            </div>
                            <div class="item">
                                <div class="label">Concepto</div>
                                <div class="value">%s</div>
                            </div>
                            <div class="item">
                                <div class="label">Tipo</div>
                                <div class="value">%s</div>
                            </div>
                            <div class="item">
                                <div class="label">Estado marketplace</div>
                                <div class="value">%s</div>
                            </div>
                        </section>

                        <section class="qr">
                            <div class="label">QR de validación</div>
                            <img src="%s" alt="QR de validación">
                            <div class="footer">%s</div>
                        </section>

                        <div class="footer">
                            Comprobante generado automáticamente por YapeSeguro.
                        </div>
                    </main>
                </body>
                </html>
                """.formatted(
                escapeHtml(receipt.getReceiptNumber()),
                escapeHtml(transaction.getStatus().name()),
                escapeHtml(nullToDash(receipt.getCurrency())),
                escapeHtml(receipt.getAmount().toPlainString()),
                escapeHtml(nullToDash(receipt.getDescription())),
                escapeHtml(receipt.getReceiptNumber()),
                escapeHtml(transaction.getReference()),
                escapeHtml(nullToDash(receipt.getBusinessName())),
                escapeHtml(nullToDash(receipt.getBusinessRuc())),
                escapeHtml(nullToDash(receipt.getCustomerName())),
                escapeHtml(nullToDash(receipt.getConcept())),
                escapeHtml(transaction.getType().name()),
                escapeHtml(transaction.getMarketplaceStatus().name()),
                escapeHtml(nullToDash(receipt.getQrCodeUrl())),
                escapeHtml(buildValidationPayload(receipt))
        );
    }

    private String generateQrCodeDataUrl(String payload) {
        try {
            BitMatrix bitMatrix = new MultiFormatWriter()
                    .encode(payload, BarcodeFormat.QR_CODE, 300, 300);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            return "data:image/png;base64," + base64;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar QR de validación", ex);
        }
    }

    private String buildValidationPayload(TransactionReceiptEntity receipt) {
        TransactionEntity transaction = receipt.getTransaction();

        return "YAPESEGURO_RECEIPT"
                + "|RECEIPT_NUMBER=" + receipt.getReceiptNumber()
                + "|TX_ID=" + transaction.getId()
                + "|REFERENCE=" + transaction.getReference()
                + "|AMOUNT=" + receipt.getAmount()
                + "|CURRENCY=" + receipt.getCurrency();
    }

    private byte[] buildSimplePdf(TransactionReceiptEntity receipt) {
        TransactionEntity transaction = receipt.getTransaction();

        String[] lines = {
                "YapeSeguro - Comprobante",
                "Comprobante: " + receipt.getReceiptNumber(),
                "Referencia: " + transaction.getReference(),
                "Tipo: " + transaction.getType().name(),
                "Estado: " + transaction.getStatus().name(),
                "Marketplace: " + transaction.getMarketplaceStatus().name(),
                "Comercio/Receptor: " + nullToDash(receipt.getBusinessName()),
                "RUC: " + nullToDash(receipt.getBusinessRuc()),
                "Cliente/Pagador: " + nullToDash(receipt.getCustomerName()),
                "Monto: " + receipt.getCurrency() + " " + receipt.getAmount(),
                "Concepto: " + nullToDash(receipt.getConcept()),
                "Descripcion: " + nullToDash(receipt.getDescription()),
                "Validacion: " + buildValidationPayload(receipt)
        };

        StringBuilder content = new StringBuilder();

        content.append("BT\n");
        content.append("/F1 12 Tf\n");
        content.append("50 790 Td\n");

        for (String line : lines) {
            content.append("(")
                    .append(escapePdf(toPdfSafeText(line)))
                    .append(") Tj\n");
            content.append("0 -22 Td\n");
        }

        content.append("ET\n");

        byte[] contentBytes = content.toString().getBytes(StandardCharsets.ISO_8859_1);

        String[] objects = new String[]{
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + contentBytes.length + " >>\nstream\n" + content + "endstream"
        };

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writePdf(output, "%PDF-1.4\n");

        int[] offsets = new int[objects.length + 1];

        for (int i = 0; i < objects.length; i++) {
            offsets[i + 1] = output.size();

            writePdf(output, (i + 1) + " 0 obj\n");
            writePdf(output, objects[i]);
            writePdf(output, "\nendobj\n");
        }

        int xrefStart = output.size();

        writePdf(output, "xref\n");
        writePdf(output, "0 " + (objects.length + 1) + "\n");
        writePdf(output, "0000000000 65535 f \n");

        for (int i = 1; i < offsets.length; i++) {
            writePdf(output, String.format("%010d 00000 n \n", offsets[i]));
        }

        writePdf(output, "trailer\n");
        writePdf(output, "<< /Size " + (objects.length + 1) + " /Root 1 0 R >>\n");
        writePdf(output, "startxref\n");
        writePdf(output, xrefStart + "\n");
        writePdf(output, "%%EOF");

        return output.toByteArray();
    }

    private void writePdf(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private String generateReceiptNumber() {
        String receiptNumber;

        do {
            receiptNumber = "REC-"
                    + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "-"
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 10)
                    .toUpperCase();
        } while (receiptRepository.existsByReceiptNumber(receiptNumber));

        return receiptNumber;
    }

    private String resolveConcept(TransactionEntity transaction) {
        String concept = normalize(transaction.getConcept());

        if (concept != null) {
            return concept;
        }

        return switch (transaction.getType()) {
            case P2P -> "Transferencia P2P";
            case QR_PAYMENT -> "Pago QR";
            case MARKETPLACE -> "Yape Seguro";
            case SCHEDULED -> "Pago programado";
        };
    }

    private ReceiptResponse toResponse(TransactionReceiptEntity receipt) {
        TransactionEntity transaction = receipt.getTransaction();

        return ReceiptResponse.builder()
                .id(receipt.getId())
                .transactionId(transaction.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .businessName(receipt.getBusinessName())
                .businessRuc(receipt.getBusinessRuc())
                .customerName(receipt.getCustomerName())
                .amount(receipt.getAmount())
                .currency(receipt.getCurrency())
                .concept(receipt.getConcept())
                .description(receipt.getDescription())
                .transactionType(transaction.getType().name())
                .transactionStatus(transaction.getStatus().name())
                .marketplaceStatus(transaction.getMarketplaceStatus().name())
                .transactionReference(transaction.getReference())
                .receiptHtml(receipt.getReceiptHtml())
                .receiptPdfUrl(receipt.getReceiptPdfUrl())
                .qrCodeUrl(receipt.getQrCodeUrl())
                .printedCount(receipt.getPrintedCount())
                .emailedTo(receipt.getEmailedTo())
                .sentWhatsapp(receipt.isSentWhatsapp())
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }

    private String fullName(UserEntity user) {
        String firstName = normalize(user.getFirstName());
        String lastName = normalize(user.getLastName());

        if (firstName == null && lastName == null) {
            return "-";
        }

        if (firstName == null) {
            return lastName;
        }

        if (lastName == null) {
            return firstName;
        }

        return firstName + " " + lastName;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullToDash(String value) {
        String normalized = normalize(value);

        return normalized != null ? normalized : "-";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapePdf(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    private String toPdfSafeText(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized.replaceAll("[^\\x20-\\x7E]", "");
    }
}