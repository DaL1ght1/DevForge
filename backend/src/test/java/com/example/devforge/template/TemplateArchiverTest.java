package com.example.devforge.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateArchiverTest {

  private final TemplateArchiver archiver = new TemplateArchiver();

  @Test
  void identicalContentProducesIdenticalArchives(@TempDir Path a, @TempDir Path b)
      throws IOException {
    write(a, "template.yaml", "name: x");
    write(a, "base/src/App.java.tmpl", "class App {}");
    write(b, "base/src/App.java.tmpl", "class App {}");
    write(b, "template.yaml", "name: x");
    Files.setLastModifiedTime(
        b.resolve("template.yaml"), java.nio.file.attribute.FileTime.fromMillis(0));

    TemplateArchiver.Archive first = archiver.archive(a);
    TemplateArchiver.Archive second = archiver.archive(b);

    assertThat(first.digest()).hasSize(64).isEqualTo(second.digest());
    assertThat(first.data()).isEqualTo(second.data());
  }

  @Test
  void entriesAreRelativeSortedAndSlashSeparated(@TempDir Path dir) throws IOException {
    write(dir, "template.yaml", "name: x");
    write(dir, "base/b.txt", "b");
    write(dir, "base/a.txt", "a");

    assertThat(entries(archiver.archive(dir).data()))
        .containsExactly("base/a.txt", "base/b.txt", "template.yaml");
  }

  @Test
  void contentChangeChangesDigest(@TempDir Path dir) throws IOException {
    write(dir, "template.yaml", "name: x");
    String before = archiver.archive(dir).digest();
    write(dir, "template.yaml", "name: y");

    assertThat(archiver.archive(dir).digest()).isNotEqualTo(before);
  }

  private static void write(Path root, String relative, String content) throws IOException {
    Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private static List<String> entries(byte[] zip) throws IOException {
    List<String> names = new ArrayList<>();
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
      for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        names.add(entry.getName());
      }
    }
    return names;
  }
}
