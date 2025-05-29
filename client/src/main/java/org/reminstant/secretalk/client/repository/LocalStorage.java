package org.reminstant.secretalk.client.repository;

import lombok.extern.slf4j.Slf4j;
import org.reminstant.cryptography.Bits;
import org.reminstant.secretalk.client.exception.*;
import org.reminstant.secretalk.client.model.Message;
import org.reminstant.secretalk.client.model.Chat;
import org.reminstant.secretalk.client.util.ObjectMappers;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.*;

@Slf4j
@Repository
public class LocalStorage {

  record FileLocation(
      long begin,
      int length) {
  }

  private static final Path HOME_PATH = Path.of(System.getProperty("user.home"));
  private static final int MESSAGE_ID_BYTE_LENGTH = 36;
  private static final int AUTHOR_BYTE_LENGTH = 32;
  private static final int STRING_LOCATION_BYTE_LENGTH = 12;
  private static final int MESSAGE_CONFIG_LENGTH = 107;
  private static final int MAX_DYNAMIC_STRING_LENGTH = 4096;
  private static final int MAX_RESOURCE_SIZE = 1 << 20;

  private final Map<String, ReadWriteLock> chatLocks;
  private final Map<String, Long> messageConfigPositions;

  private String username;


  public LocalStorage() {
    this.chatLocks = new ConcurrentHashMap<>();
    this.messageConfigPositions = new ConcurrentHashMap<>();
  }

  public void init(String username) throws ModuleInitialisationException {
    this.username = username;

    try {
      createDirectoryIfNotExist(getAppPath());
      createDirectoryIfNotExist(getUserFolderPath());

      indexStorage(getUserFolderPath());
    } catch (IOException ex) {
      throw new ModuleInitialisationException("Failed to initialise local storage", ex);
    }

    log.info("LocalStorage INITIALIZED");
  }

  public void reset() {
    this.username = null;
  }


  public byte[] readAsBytes(Path path) throws LocalStorageReadException {
    try {
      long size = Files.size(path);
      if (size > 2 * MAX_RESOURCE_SIZE) {
        throw new IOException("File is to big");
      }
      return Files.readAllBytes(path);
    } catch (IOException ex) {
      throw new LocalStorageReadException("Failed to read data from the local storage", ex);
    }
  }

  public void writeToFile(Path path, byte[] bytes) throws LocalStorageWriteException {
    try {
      Files.write(path, bytes);
    } catch (IOException ex) {
      throw new LocalStorageWriteException("Failed to write data to the local storage", ex);
    }
  }

  public void copyToFile(Path pathSrc, Path pathDest) throws LocalStorageWriteException {
    try {
      Files.copy(pathSrc, pathDest, REPLACE_EXISTING);
    } catch (IOException ex) {
      throw new LocalStorageWriteException("Failed to copy data in the local storage", ex);
    }
  }



  public Path createTmpFile(String prefix, String postfix) throws LocalStorageCreationException {
    throwIfUninitialised();
    try {
      Path tmpDirPath = getTmpPath();
      createDirectoryIfNotExist(tmpDirPath);
      return Files.createTempFile(tmpDirPath, prefix, postfix);
    } catch (IOException ex) {
      throw new LocalStorageCreationException("Failed to create tmp file", ex);
    }
  }

  public void deleteTmpFile(String name) throws LocalStorageDeletionException {
    throwIfUninitialised();
    try {
      Path tmpDirPath = getTmpPath();
      createDirectoryIfNotExist(tmpDirPath);
      Files.deleteIfExists(tmpDirPath.resolve(name));
    } catch (IOException ex) {
      throw new LocalStorageDeletionException("Failed to delete tmp file", ex);
    }
  }

  public Path createResourceFile(String name) throws LocalStorageCreationException {
    throwIfUninitialised();
    try {
//      if
//      Files.size(path) MAX_RESOURCE_SIZE

      Path resourceDirPath = getResourcePath();
      createDirectoryIfNotExist(resourceDirPath);
      Path resultPath = resourceDirPath.resolve(name);
      return Files.createFile(resultPath);
    } catch (IOException ex) {
      throw new LocalStorageCreationException("Failed to create resource file", ex);
    }
  }

  public Path getResourceFile(String name) throws LocalStorageExistenceException {
    throwIfUninitialised();

    Path resourceDirPath = getResourcePath();
    Path resultPath = resourceDirPath.resolve(name);

    if (!Files.exists(resourceDirPath) || !Files.exists(resultPath)) {
      throw new LocalStorageExistenceException("Resource file %s does not exist".formatted(name));
    }

    return resultPath;
  }



  public Optional<Chat> getChatByChatId(String chatId) throws LocalStorageReadException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chatId);
    lock.readLock().lock();

    Path chatConfigPath = getChatConfigPath(chatId);

    if (!Files.exists(chatConfigPath)) {
      return Optional.empty();
    }

    try {
      return Optional.of(ObjectMappers.bigNumberObjectMapper.readValue(chatConfigPath.toFile(), Chat.class));
    } catch (IOException ex) {
      throw new LocalStorageReadException("Failed to read chat config", ex);
    } finally {
      lock.readLock().unlock();
    }
  }

  public List<Chat> getChats() throws LocalStorageReadException {
    throwIfUninitialised();

    List<String> chatIds;
    try {
      chatIds = getChatIds(getUserFolderPath());
    } catch (IOException ex) {
      throw new LocalStorageReadException("Failed to read chats config", ex);
    }

    List<Chat> chats = new ArrayList<>();
    for (String chatId : chatIds) {
      ReadWriteLock lock = getChatLock(chatId);
      lock.readLock().lock();
      try {
        chats.add(ObjectMappers.bigNumberObjectMapper
            .readValue(getChatConfigPath(chatId).toFile(), Chat.class));
      } catch (IOException ex) {
        throw new LocalStorageReadException("Failed to read chat configs", ex);
      } finally {
        lock.readLock().unlock();
      }
    }
    return chats;
  }

  public void saveChatConfig(Chat chat) throws LocalStorageWriteException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chat.getId());
    lock.writeLock().lock();

    try {
      createDirectoryIfNotExist(getChatFolderPath(chat.getId()));

      File file = getChatConfigPath(chat.getId()).toFile();
      ObjectMappers.bigNumberObjectMapper.writerWithDefaultPrettyPrinter().writeValue(file, chat);
    } catch (IOException ex) {
      throw new LocalStorageWriteException("Failed to save chat config", ex);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void updateChatConfig(String chatId, Chat.State state)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chatId);
    lock.writeLock().lock();

    try {
      Path chatConfigPath = getChatConfigPath(chatId);
      if (!Files.exists(chatConfigPath)) {
        throw new LocalStorageWriteException("No such chat config file");
      }

      File file = chatConfigPath.toFile();
      Chat chat = ObjectMappers.bigNumberObjectMapper.readValue(file, Chat.class);
      chat.setState(state);
      ObjectMappers.bigNumberObjectMapper.writerWithDefaultPrettyPrinter().writeValue(file, chat);
    } catch (IOException ex) {
      throw new LocalStorageWriteException("Failed to update chat config", ex);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void updateChatConfig(String chatId, Chat.State state, BigInteger key)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chatId);
    lock.writeLock().lock();

    try {
      Path chatConfigPath = getChatConfigPath(chatId);
      if (!Files.exists(chatConfigPath)) {
        throw new LocalStorageWriteException("No such chat config file");
      }

      File file = chatConfigPath.toFile();
      Chat chat = ObjectMappers.bigNumberObjectMapper.readValue(file, Chat.class);
      chat.setState(state);
      chat.setKey(key);
      ObjectMappers.bigNumberObjectMapper.writerWithDefaultPrettyPrinter().writeValue(file, chat);
    } catch (IOException ex) {
      throw new LocalStorageWriteException("Failed to update chat config", ex);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void clearChat(String chatId) throws LocalStorageDeletionException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chatId);
    lock.writeLock().lock();

    try {
      Path chatStaticDataPath = getStaticDataPath(chatId);
      Path chatDynamicDataPath = getDynamicDataPath(chatId);

      Files.deleteIfExists(chatStaticDataPath);
      Files.deleteIfExists(chatDynamicDataPath);
    } catch (Exception e) {
      throw new LocalStorageDeletionException("Failed to delete chat data");
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void deleteChat(String chatId) throws LocalStorageDeletionException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chatId);
    lock.writeLock().lock();

    try {
      Path chatFolderPath = getChatFolderPath(chatId);
      Files.walkFileTree(chatFolderPath, new FileDeleteVisitor());
    } catch (IOException e) {
      throw new LocalStorageDeletionException("Failed to delete chat");
    } finally {
      lock.writeLock().unlock();
    }
  }


  public Optional<Message> readMessageById(String chatId, String messageId)
      throws LocalStorageReadException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chatId);
    lock.readLock().lock();

    try {
      Path staticDataPath = getStaticDataPath(chatId);
      Path dynamicDataPath = getDynamicDataPath(chatId);
      return readMessageById(staticDataPath, dynamicDataPath, messageId);
    } catch (IOException ex) {
      throw new LocalStorageReadException("Failed to read message", ex);
    } finally {
      lock.readLock().unlock();
    }
  }

  public List<Message> getMessages(String chatId) throws LocalStorageReadException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chatId);
    lock.readLock().lock();

    try {
      Path staticDataPath = getStaticDataPath(chatId);
      Path dynamicDataPath = getDynamicDataPath(chatId);
      return readMessages(staticDataPath, dynamicDataPath);
    } catch (IOException ex) {
      throw new LocalStorageReadException("Failed to read messages", ex);
    } finally {
      lock.readLock().unlock();
    }
  }

  public void saveMessage(String chatId, Message message) throws LocalStorageWriteException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chatId);
    lock.writeLock().lock();

    try {
      Path staticDataPath = getStaticDataPath(chatId);
      Path dynamicDataPath = getDynamicDataPath(chatId);

      FileLocation textLoc = writeDataString(dynamicDataPath, message.getText());
      FileLocation fileNameLoc = new FileLocation(0, 0);
      FileLocation filePathLoc = new FileLocation(0, 0);
      if (message.getFileName() != null) {
        fileNameLoc = writeDataString(dynamicDataPath, message.getFileName());
      }
      if (message.getFilePath() != null) {
        filePathLoc = writeDataString(dynamicDataPath, message.getFilePath().toString());
      }

      long configPos = messageConfigPositions.getOrDefault(message.getId(), -1L);
      FileLocation configLoc = writeMessageConfig(
          staticDataPath, message, textLoc, fileNameLoc, filePathLoc, configPos);
      messageConfigPositions.put(message.getId(), configLoc.begin());
    } catch (IOException ex) {
      throw new LocalStorageWriteException("Failed to save message", ex);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void updateMessageState(String chatId, String messageId, Message.State state)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chatId);
    lock.writeLock().lock();

    try {
      long configPos = messageConfigPositions.getOrDefault(messageId, -1L);
      if (configPos == -1) {
        log.warn("No config entry for message '{}' (tried to set state '{}')", messageId, state);
        return;
      }

      Path staticDataPath = getStaticDataPath(chatId);
      rewriteMessageState(staticDataPath, configPos, state);
    } catch (IOException ex) {
      throw new LocalStorageWriteException("Failed to rewrite message state", ex);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void updateMessageFileName(String chatId, String messageId, String fileName)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chatId);
    lock.writeLock().lock();

    try {
      long configPos = messageConfigPositions.getOrDefault(messageId, -1L);
      if (configPos == -1) {
        log.warn("No config entry for message '{}' (tried to set file name '{}')", messageId, fileName);
        return;
      }

      FileLocation filePathLoc = new FileLocation(0, 0);
      if (fileName != null) {
        Path dynamicDataPath = getDynamicDataPath(chatId);
        filePathLoc = writeDataString(dynamicDataPath, fileName);
      }

      Path staticDataPath = getStaticDataPath(chatId);
      rewriteMessageStringLocation(staticDataPath, configPos, filePathLoc, 1);
    } catch (IOException ex) {
      throw new LocalStorageWriteException("Failed to rewrite message state", ex);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void updateMessageFilePath(String chatId, String messageId, Path filePath)
      throws LocalStorageWriteException {
    throwIfUninitialised();
    ReadWriteLock lock = getChatLock(chatId);
    lock.writeLock().lock();

    try {
      long configPos = messageConfigPositions.getOrDefault(messageId, -1L);
      if (configPos == -1) {
        log.warn("No config entry for message '{}' (tried to set file path '{}')", messageId, filePath);
        return;
      }

      Path staticDataPath = getStaticDataPath(chatId);
      Path dynamicDataPath = getDynamicDataPath(chatId);
      FileLocation filePathLoc = writeDataString(dynamicDataPath, filePath.toString());
      rewriteMessageStringLocation(staticDataPath, configPos, filePathLoc, 2);
    } catch (IOException ex) {
      throw new LocalStorageWriteException("Failed to rewrite message state", ex);
    } finally {
      lock.writeLock().unlock();
    }
  }



  private List<String> getChatIds(Path chatHolderPath) throws IOException {
    try (Stream<Path> paths = Files.walk(chatHolderPath, 1)) {
      return paths
          .filter(path -> !path.equals(chatHolderPath))
          .filter(Files::isDirectory)
          .map(dir -> dir.getFileName().toString())
          .toList();
    }
  }

  private void indexStorage(Path chatHolderPath) throws IOException {
    List<String> chatIds = getChatIds(chatHolderPath);
    for (String chatId : chatIds) {
      Path staticDataPath = getStaticDataPath(chatId);
      try (FileChannel configChannel = FileChannel.open(staticDataPath, CREATE, READ, WRITE)) {
        byte[] messageIdBuffer = new byte[MESSAGE_ID_BYTE_LENGTH];
        for (int i = 0; i < configChannel.size(); i += MESSAGE_CONFIG_LENGTH) {
          int read = configChannel.read(ByteBuffer.wrap(messageIdBuffer), i);
          if (read != MESSAGE_ID_BYTE_LENGTH) {
            log.warn("Failed to read all {} bytes of message ID while indexing", MESSAGE_ID_BYTE_LENGTH);
          }
          String readMsgId = new String(messageIdBuffer, StandardCharsets.UTF_8).trim();
          messageConfigPositions.put(readMsgId, (long) i);
        }
      }
    }
  }

  private FileLocation writeDataString(Path path, String str) throws IOException {
    try (FileChannel channel = FileChannel.open(path, CREATE, WRITE, APPEND)) {
      long beginPos = channel.position();
      int length = channel.write(ByteBuffer.wrap(str.getBytes(StandardCharsets.UTF_8)));
      return new FileLocation(beginPos, length);
    }
  }

  private FileLocation writeMessageConfig(Path path, Message message, FileLocation textLoc,
                                          FileLocation fileNameLoc, FileLocation filePathLoc,
                                          long position) throws IOException {
    try (FileChannel channel = FileChannel.open(path, CREATE, WRITE)) {
      long beginPos = position != -1 ? position : channel.size();
      int length = 0;

      byte[] msgIdBuffer = message.getId().getBytes();
      byte[] authorBuffer = message.getAuthor().getBytes();
      byte[] locBuffer = new byte[36];
      byte[] stateBuffer = new byte[3];

      Bits.unpackLongToBigEndian(textLoc.begin(), locBuffer, 0);
      Bits.unpackIntToBigEndian(textLoc.length(), locBuffer, 8);
      Bits.unpackLongToBigEndian(fileNameLoc.begin(), locBuffer, 12);
      Bits.unpackIntToBigEndian(fileNameLoc.length(), locBuffer, 20);
      Bits.unpackLongToBigEndian(filePathLoc.begin(), locBuffer, 24);
      Bits.unpackIntToBigEndian(filePathLoc.length(), locBuffer, 32);
      stateBuffer[0] = (byte) (message.isBelongedToReceiver() ? 1 : 0);
      stateBuffer[1] = (byte) (message.getState().ordinal());
      stateBuffer[2] = (byte) (message.isImage() ? 1 : 0);

      if (msgIdBuffer.length != MESSAGE_ID_BYTE_LENGTH) {
        msgIdBuffer = Arrays.copyOf(msgIdBuffer, MESSAGE_ID_BYTE_LENGTH);
      }
      if (authorBuffer.length != AUTHOR_BYTE_LENGTH) {
        authorBuffer = Arrays.copyOf(authorBuffer, AUTHOR_BYTE_LENGTH);
      }

      channel.position(beginPos);
      length += channel.write(ByteBuffer.wrap(msgIdBuffer));
      length += channel.write(ByteBuffer.wrap(authorBuffer));
      length += channel.write(ByteBuffer.wrap(locBuffer));
      length += channel.write(ByteBuffer.wrap(stateBuffer));

      if (length != MESSAGE_CONFIG_LENGTH) {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "rw")) {
          randomAccessFile.setLength(beginPos);
        }
        throw new IOException("Message config has unexpected length");
      }

      return new FileLocation(beginPos, length);
    }
  }

  private void rewriteMessageState(Path staticDataPath, Long messageConfigPosition,
                                   Message.State state) throws IOException {
    try (FileChannel channel = FileChannel.open(staticDataPath, WRITE)) {
      byte[] stateBuffer = new byte[1];
      stateBuffer[0] = (byte) (state.ordinal());

      int offset = MESSAGE_ID_BYTE_LENGTH + AUTHOR_BYTE_LENGTH + 3 * STRING_LOCATION_BYTE_LENGTH + 1;
      channel.position(messageConfigPosition + offset);
      int length = channel.write(ByteBuffer.wrap(stateBuffer));

      if (length != stateBuffer.length) {
        throw new IOException("Failed to rewrite state");
      }
    }
  }

  private void rewriteMessageStringLocation(Path staticDataPath, Long messageConfigPosition,
                                            FileLocation filePathLoc, int index) throws IOException {
    try (FileChannel channel = FileChannel.open(staticDataPath, WRITE)) {
      byte[] locBuffer = new byte[12];
      Bits.unpackLongToBigEndian(filePathLoc.begin(), locBuffer, 0);
      Bits.unpackIntToBigEndian(filePathLoc.length(), locBuffer, 8);

      int offset = MESSAGE_ID_BYTE_LENGTH + AUTHOR_BYTE_LENGTH + index * STRING_LOCATION_BYTE_LENGTH;
      channel.position(messageConfigPosition + offset);
      int length = channel.write(ByteBuffer.wrap(locBuffer));

      if (length != locBuffer.length) {
        throw new IOException("Failed to rewrite filePath");
      }
    }
  }

  private String readDataString(FileChannel channel, FileLocation loc) throws IOException {
    byte[] buffer = new byte[loc.length()];
    channel.read(ByteBuffer.wrap(buffer), loc.begin());
    return new String(buffer);
  }

  private Optional<Message> readMessageById(Path staticDataPath, Path dynamicDataPath, String msgId)
      throws IOException {
    try (FileChannel staticDataChannel = FileChannel.open(staticDataPath, CREATE, READ, WRITE);
         FileChannel dynamicDataChannel = FileChannel.open(dynamicDataPath, CREATE, READ, WRITE)) {
      long pos = messageConfigPositions.getOrDefault(msgId, -1L);
      if (pos == -1) {
        byte[] chatIdBuffer = new byte[MESSAGE_ID_BYTE_LENGTH];
        for (int i = 0; i < staticDataChannel.size(); i += MESSAGE_CONFIG_LENGTH) {
          @SuppressWarnings("unused")
          int readLength = staticDataChannel.read(ByteBuffer.wrap(chatIdBuffer));
          String readMsgId = new String(chatIdBuffer, StandardCharsets.UTF_8);
          if (readMsgId.equals(msgId)) {
            pos = i;
            break;
          }
        }
      }

      if (pos == -1) {
        return Optional.empty();
      }

      return Optional.of(readMessage(staticDataChannel, dynamicDataChannel, pos));
    }
  }

  private List<Message> readMessages(Path staticDataPath, Path dynamicDataPath) throws IOException {
    try (FileChannel staticDataChannel = FileChannel.open(staticDataPath, CREATE, READ, WRITE);
         FileChannel dynamicDataChannel = FileChannel.open(dynamicDataPath, CREATE, READ, WRITE)) {
      List<Message> messages = new ArrayList<>();

      for (int i = 0; i < staticDataChannel.size(); i += MESSAGE_CONFIG_LENGTH) {
        messages.add(readMessage(staticDataChannel, dynamicDataChannel, i));
      }

      return messages;
    }
  }

  private Message readMessage(FileChannel staticDataChannel, FileChannel dynamicDataChannel, long pos)
      throws IOException {
    byte[] msgIdBuffer = new byte[MESSAGE_ID_BYTE_LENGTH];
    byte[] authorBuffer = new byte[AUTHOR_BYTE_LENGTH];
    byte[] locBuffer = new byte[36];
    byte[] stateBuffer = new byte[3];

    staticDataChannel.position(pos);
    staticDataChannel.read(ByteBuffer.wrap(msgIdBuffer));
    staticDataChannel.read(ByteBuffer.wrap(authorBuffer));
    staticDataChannel.read(ByteBuffer.wrap(locBuffer));
    staticDataChannel.read(ByteBuffer.wrap(stateBuffer));

    String msgId = new String(msgIdBuffer, StandardCharsets.UTF_8).trim();
    String author = new String(authorBuffer, StandardCharsets.UTF_8).trim();
    long textBegin = Bits.packBigEndianToLong(locBuffer, 0);
    int textLength = Bits.packBigEndianToInt(locBuffer, 8);
    long fileNameBegin = Bits.packBigEndianToLong(locBuffer, 12);
    int fileNameLength = Bits.packBigEndianToInt(locBuffer, 20);
    long filePathBegin = Bits.packBigEndianToLong(locBuffer, 24);
    int filePathLength = Bits.packBigEndianToInt(locBuffer, 32);
    boolean isBelongedToReceiver = stateBuffer[0] == 1;
    Message.State state = Message.State.values()[stateBuffer[1]];
    boolean isImage = stateBuffer[2] == 1;

    if (textLength > MAX_DYNAMIC_STRING_LENGTH ||
        fileNameLength > MAX_DYNAMIC_STRING_LENGTH ||
        filePathLength > MAX_DYNAMIC_STRING_LENGTH) {
      throw new IOException("Corrupted chat config");
    }

    String text = readDataString(dynamicDataChannel, new FileLocation(textBegin, textLength));
    String fileName = null;
    Path filePath = null;
    if (fileNameLength > 0) {
      fileName = readDataString(dynamicDataChannel, new FileLocation(fileNameBegin, fileNameLength));
    }
    if (filePathLength > 0) {
      filePath = Path.of(readDataString(dynamicDataChannel, new FileLocation(filePathBegin, filePathLength)));
    }

    return new Message(msgId, text, author, fileName, isBelongedToReceiver, filePath, isImage, state);
  }



  private void createDirectoryIfNotExist(Path path) throws IOException {
    if (Files.exists(path)) {
      if (!Files.isDirectory(path)) {
        throw new IOException("Directory name is occupied by file (path: %s)".formatted(path));
      }
    } else {
      try {
        Files.createDirectory(path);
      } catch (IOException e) {
        throw new IOException("Failed to create directory (path: %s)".formatted(path));
      }
    }
  }

  private Path getAppPath() {
    return HOME_PATH.resolve(".secretalk");
  }

  private Path getTmpPath() {
    return getAppPath().resolve("tmp");
  }

  private Path getResourcePath() {
    return getAppPath().resolve("res");
  }

  private Path getUserFolderPath() {
    return getAppPath().resolve(username);
  }

  private Path getChatFolderPath(String chatId) {
    return getUserFolderPath().resolve(chatId);
  }

  private Path getChatConfigPath(String chatId) {
    return getChatFolderPath(chatId).resolve("config.json");
  }

  private Path getStaticDataPath(String chatId) {
    return getChatFolderPath(chatId).resolve("data0");
  }

  private Path getDynamicDataPath(String chatId) {
    return getChatFolderPath(chatId).resolve("data1");
  }

  private ReadWriteLock getChatLock(String chatId) {
    return chatLocks.computeIfAbsent(chatId, _ -> new ReentrantReadWriteLock());
  }

  private void throwIfUninitialised() {
    if (username == null) {
      throw new ModuleUninitialisedStateException("LocalStorage is uninitialised");
    }
  }

  private static class FileDeleteVisitor extends SimpleFileVisitor<Path> {
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      Files.delete(file);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
      if (e == null) {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      } else {
        throw e;
      }
    }
  }
}