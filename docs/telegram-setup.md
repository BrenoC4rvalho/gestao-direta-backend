# Telegram setup

1. Create a bot with BotFather and keep its token secret.
2. Configure `TELEGRAM_ENABLED=true`, `TELEGRAM_BOT_TOKEN`, and `TELEGRAM_WEBHOOK_SECRET`.
3. Expose the API over HTTPS.
4. Register the webhook with `https://api.telegram.org/bot<token>/setWebhook`, using URL `https://<host>/api/webhooks/messaging/telegram` and the same secret token.
5. Validate `GET /api/messaging/telegram/status` as an administrator and inspect `getWebhookInfo` at Telegram.
6. Send a text message to the bot, generate a linking code in Gestão Direta, and send `/vincular CODIGO` to the bot. Five invalid attempts in 15 minutes temporarily block that Telegram account for 15 minutes; a valid link clears this protection.
7. Administrators can filter the message history by `messagingConversationId`, `messageDirection`, and status. Inactivating or blocking an account cancels its open conversations without deleting history; reactivation starts a new conversation on the next message.
7. Remove the webhook with `deleteWebhook` during troubleshooting.

Never place the token or webhook secret in source control or application logs.
