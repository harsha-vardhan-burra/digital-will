package com.digitalwill.document.validation;

import com.digitalwill.common.exception.DocumentValidationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Stage C: Safe document parsing and resource bound enforcement.
 * Protects against decompression bombs, parser exhaustion, password-protected files,
 * corrupt payloads, and active macro content.
 */
@Component
public class SafeDocumentParser {

    private static final Logger log = LoggerFactory.getLogger(SafeDocumentParser.class);

    public static final int MAX_PDF_PAGES = 200;
    public static final int MAX_IMAGE_DIMENSION = 10_000; // max width or height
    public static final long MAX_IMAGE_PIXELS = 50_000_000L; // 50 megapixels
    public static final int MAX_DOCX_ZIP_ENTRIES = 500;
    public static final long MAX_DOCX_UNCOMPRESSED_SIZE_BYTES = 50 * 1024 * 1024L; // 50 MB
    public static final int MAX_DOCX_COMPRESSION_RATIO = 100; // 100:1 ratio

    /**
     * Executes safe structural parsing on the file bytes based on detected media type.
     */
    public void parseAndVerify(String detectedContentType, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new DocumentValidationException("MALFORMED_DOCUMENT", "File bytes are empty");
        }

        switch (detectedContentType) {
            case MediaDetector.MEDIA_TYPE_PDF -> parsePdf(fileBytes);
            case MediaDetector.MEDIA_TYPE_JPEG, MediaDetector.MEDIA_TYPE_PNG -> parseImage(fileBytes);
            case MediaDetector.MEDIA_TYPE_DOCX -> parseDocx(fileBytes);
            default -> throw new DocumentValidationException(
                    "UNSUPPORTED_FILE_TYPE",
                    "No safe parser available for content type: " + detectedContentType
            );
        }
    }

    private void parsePdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
            if (document.isEncrypted()) {
                throw new DocumentValidationException(
                        "DOCUMENT_PASSWORD_PROTECTED",
                        "Password-protected or encrypted PDF files are not supported in the vault"
                );
            }
            int pages = document.getNumberOfPages();
            if (pages <= 0) {
                throw new DocumentValidationException("DOCUMENT_CORRUPT", "PDF contains no readable pages");
            }
            if (pages > MAX_PDF_PAGES) {
                throw new DocumentValidationException(
                        "FILE_TOO_LARGE",
                        "PDF exceeds maximum page limit of " + MAX_PDF_PAGES + " pages (found: " + pages + ")"
                );
            }
        } catch (DocumentValidationException dve) {
            throw dve;
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            throw new DocumentValidationException(
                    "DOCUMENT_PASSWORD_PROTECTED",
                    "Password-protected or encrypted PDF files are not supported in the vault"
            );
        } catch (Exception e) {
            log.warn("PDF safe parsing failed: {}", e.getMessage());
            throw new DocumentValidationException("DOCUMENT_CORRUPT", "Malformed or unreadable PDF document");
        }
    }

    private void parseImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new DocumentValidationException("MALFORMED_DOCUMENT", "Cannot read image stream");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new DocumentValidationException("MALFORMED_DOCUMENT", "No suitable image reader found");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width <= 0 || height <= 0) {
                    throw new DocumentValidationException("MALFORMED_DOCUMENT", "Invalid image dimensions");
                }
                if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                    throw new DocumentValidationException(
                            "FILE_TOO_LARGE",
                            "Image dimensions (" + width + "x" + height + ") exceed maximum limit of " + MAX_IMAGE_DIMENSION + "px"
                    );
                }
                long totalPixels = (long) width * height;
                if (totalPixels > MAX_IMAGE_PIXELS) {
                    throw new DocumentValidationException(
                            "FILE_TOO_LARGE",
                            "Image pixel count exceeds maximum decompression threshold (" + MAX_IMAGE_PIXELS + " pixels)"
                    );
                }
            } finally {
                reader.dispose();
            }
        } catch (DocumentValidationException dve) {
            throw dve;
        } catch (Exception e) {
            log.warn("Image safe parsing failed: {}", e.getMessage());
            throw new DocumentValidationException("MALFORMED_DOCUMENT", "Malformed or corrupt image document");
        }
    }

    private void parseDocx(byte[] bytes) {
        int entryCount = 0;
        long totalUncompressedBytes = 0;
        boolean hasContentTypes = false;
        boolean hasDocumentXml = false;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_DOCX_ZIP_ENTRIES) {
                    throw new DocumentValidationException("MALFORMED_DOCUMENT", "DOCX archive exceeds maximum zip entry count");
                }

                String name = entry.getName();
                if (name == null || name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new DocumentValidationException("PATH_TRAVERSAL_DETECTED", "DOCX archive contains illegal path traversal entry");
                }

                // Check for embedded macros
                String lowerName = name.toLowerCase();
                if (lowerName.endsWith(".bin") || lowerName.contains("vba") || lowerName.endsWith(".vbs") || lowerName.endsWith(".exe")) {
                    throw new DocumentValidationException("DOCUMENT_ACTIVE_CONTENT_PROHIBITED", "Macro-enabled or executable components detected in DOCX file");
                }

                if ("[Content_Types].xml".equalsIgnoreCase(name)) {
                    hasContentTypes = true;
                }
                if ("word/document.xml".equalsIgnoreCase(name)) {
                    hasDocumentXml = true;
                }

                // Read entry to track uncompressed size (zip bomb defense)
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    totalUncompressedBytes += read;
                    if (totalUncompressedBytes > MAX_DOCX_UNCOMPRESSED_SIZE_BYTES) {
                        throw new DocumentValidationException("FILE_TOO_LARGE", "DOCX uncompressed size exceeds maximum safe limit");
                    }
                }
                zis.closeEntry();
            }

            if (bytes.length > 0) {
                long ratio = totalUncompressedBytes / bytes.length;
                if (ratio > MAX_DOCX_COMPRESSION_RATIO) {
                    throw new DocumentValidationException("DOCUMENT_DECOMPRESSION_BOMB", "DOCX archive exceeds safe compression expansion ratio");
                }
            }

            if (!hasContentTypes || !hasDocumentXml) {
                throw new DocumentValidationException("MALFORMED_DOCUMENT", "Malformed DOCX file: missing standard package structure");
            }
        } catch (DocumentValidationException dve) {
            throw dve;
        } catch (IOException e) {
            log.warn("DOCX safe parsing failed: {}", e.getMessage());
            throw new DocumentValidationException("MALFORMED_DOCUMENT", "Failed to parse DOCX archive structure");
        }
    }
}
