package br.com.gestaodireta.messaging.telegram.client;

public interface TelegramBotClient {
    TelegramBotIdentity getMe();

    TelegramSendMessageResult sendMessage(String chatId, String text);

    String getFilePath(String fileId);

    byte[] downloadFile(String filePath);
}
