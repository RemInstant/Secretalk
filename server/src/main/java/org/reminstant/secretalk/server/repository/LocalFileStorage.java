package org.reminstant.secretalk.server.repository;

import lombok.extern.slf4j.Slf4j;
import org.reminstant.secretalk.server.exception.LocalFileStorageException;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.StandardOpenOption.READ;

@Slf4j
@Repository
public class LocalFileStorage {

  private static final Path FILE_FOLDER_PATH = Path.of("chatFiles");


  public boolean isFileExist(String fileName) throws LocalFileStorageException {
    try {
      createDirectoryIfNotExists(FILE_FOLDER_PATH);
      Path filePath = getFilePath(fileName);
      return Files.exists(filePath);
    } catch (IOException ex) {
      throw new LocalFileStorageException("Failed to check file existence", ex);
    }
  }

  public long getFileSize(String fileName) throws LocalFileStorageException {
    try {
      createDirectoryIfNotExists(FILE_FOLDER_PATH);
      Path filePath = getFilePath(fileName);
      return Files.size(filePath);
    } catch (IOException ex) {
      throw new LocalFileStorageException("Failed to get file size", ex);
    }
  }

  public void writeFilePart(String fileName, long pos, byte[] data) throws LocalFileStorageException {
    try {
      createDirectoryIfNotExists(FILE_FOLDER_PATH);
      Path filePath = getFilePath(fileName);
      try (FileChannel fileChannel = FileChannel.open(filePath, CREATE, WRITE)) {
        int written = fileChannel.write(ByteBuffer.wrap(data), pos);
        if (written != data.length) {
          log.error("Some data was not written ({}/{} bytes was written}", written, data.length);
        }
      }
    } catch (IOException ex) {
      throw new LocalFileStorageException("Failed to write into the file storage", ex);
    }

  }

  // TODO: return byte[]
  public int readFilePart(String fileName, long pos, byte[] data) throws LocalFileStorageException {
    try {
      createDirectoryIfNotExists(FILE_FOLDER_PATH);
      Path filePath = getFilePath(fileName);
      try (FileChannel fileChannel = FileChannel.open(filePath, READ)) {
        return fileChannel.read(ByteBuffer.wrap(data), pos);
      }
    } catch (IOException ex) {
      throw new LocalFileStorageException("Failed to read from the file storage", ex);
    }
  }

  public void deleteFile(String fileName) throws LocalFileStorageException {
    try {
      createDirectoryIfNotExists(FILE_FOLDER_PATH);
      Path filePath = getFilePath(fileName);
      Files.delete(filePath);
    } catch (IOException ex) {
      throw new LocalFileStorageException("Failed to delete file from the file storage", ex);
    }
  }

  @SuppressWarnings("SameParameterValue")
  private void createDirectoryIfNotExists(Path path) throws IOException {
    if (!Files.exists(path)) {
      Files.createDirectory(path);
    }
  }

  private Path getFilePath(String fileName) {
    return FILE_FOLDER_PATH.resolve(fileName);
  }
}
