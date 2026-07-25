/**
 * Adapters for the Telegram Bot API — one subpackage per external system.
 *
 * <p>Today it holds the inbound side only: a long-polling listener that receives updates and drives the
 * application's inbound message port. The outbound adapters for the same system — voice-file fetch and
 * notification sending — land here as well when they are implemented.
 *
 * <p>Every Telegram-specific type stays inside this package: the pengrad client's {@code Update},
 * {@code Message} and {@code Chat} are transport detail and never cross into {@code domain}/{@code application}.
 */
package bot.finance.adapter.telegram;
