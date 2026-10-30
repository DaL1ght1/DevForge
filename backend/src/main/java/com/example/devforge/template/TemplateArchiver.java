package com.example.devforge.template;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

@Component
public class TemplateArchiver {

  public static final int MAX_ARCHIVE_BYTES = 20 * 1024 * 1024;
  private static final LocalDateTime FIXED_TIME = LocalDateTime.of(1980, 1, 1, 0, 0);

  public record Archive(String digest, byte[] data) {}

  public Archive archive(Path templateDir) throws IOException {
    Path dir = templateDir.toRealPath();
    List<Path> files;
    try (Stream<Path> walk = Files.walk(dir)) {
      files =
          walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
              .sorted(Comparator.comparing(a -> entryName(dir, a)))
              .toList();
    }
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      for (Path file : files) {
        ZipEntry entry = new ZipEntry(entryName(dir, file));
        entry.setTimeLocal(FIXED_TIME);
        zip.putNextEntry(entry);
        Files.copy(file, zip);
        zip.closeEntry();
        if (bytes.size() > MAX_ARCHIVE_BYTES) {
          throw new IOException("template archive exceeds " + MAX_ARCHIVE_BYTES + " bytes");
        }
      }
    }
    byte[] data = bytes.toByteArray();
    return new Archive(sha256(data), data);
  }

  private static String entryName(Path dir, Path file) {
    return dir.relativize(file).toString().replace('\\', '/');
  }

  private static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
