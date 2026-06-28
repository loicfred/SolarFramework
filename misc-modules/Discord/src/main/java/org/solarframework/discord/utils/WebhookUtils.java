package org.solarframework.discord.utils;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;

import java.util.Objects;
import java.util.function.Consumer;

import static org.solarframework.discord.core.BotBuilder.DiscordAccount;

public class WebhookUtils {

    public static void getWebhookOfChannel(IWebhookContainer channel, Consumer<WebhookClient> callback) {
        try {
            channel.retrieveWebhooks().queue(WHs -> {
                try {
                    for (net.dv8tion.jda.api.entities.Webhook wb : WHs) {
                        if (Objects.equals(wb.getOwnerAsUser(), DiscordAccount.getSelfUser())) {
                            try (WebhookClient C = buildWebhookClient(wb).build()) {
                                callback.accept(C);
                            }
                            return;
                        }
                    }
                    channel.createWebhook(DiscordAccount.getSelfUser().getEffectiveName()).queue(wb -> {
                        try (WebhookClient C = buildWebhookClient(wb).build()) {
                            callback.accept(C);
                        }
                    });
                } catch (Exception ignored) {
                    callback.accept(null);
                }
            });
        } catch (Exception ignored) {
            callback.accept(null);
        }
    }

    private static WebhookClientBuilder buildWebhookClient(net.dv8tion.jda.api.entities.Webhook wb) {
        WebhookClientBuilder builder = new WebhookClientBuilder(wb.getUrl());
        builder.setThreadFactory((job) -> {
            Thread thread = new Thread(job);
            thread.setName("Webhook-Thread");
            thread.setDaemon(true);
            return thread;
        });
        return builder;
    }

}
