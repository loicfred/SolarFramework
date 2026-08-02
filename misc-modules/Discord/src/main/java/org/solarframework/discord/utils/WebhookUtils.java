package org.solarframework.discord.utils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IncomingWebhookClient;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.entities.channel.attribute.IWebhookContainer;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;

import java.util.Objects;
import java.util.function.Consumer;

import static org.solarframework.discord.core.BotBuilder.DiscordAccount;

public class WebhookUtils {

    public static void getWebhookOfChannel(IWebhookContainer channel, Consumer<IncomingWebhookClient> callback) {
        try {
            channel.retrieveWebhooks().queue(WHs -> {
                try {
                    for (Webhook wb : WHs) {
                        if (Objects.equals(wb.getOwnerAsUser(), DiscordAccount.getSelfUser())) {callback.accept(asClient(wb)); return;}
                    }
                    channel.createWebhook(DiscordAccount.getSelfUser().getEffectiveName()).queue(wb -> callback.accept(asClient(wb)), _ -> callback.accept(null));
                } catch (Exception ignored) {
                    callback.accept(null);
                }
            }, _ -> callback.accept(null));
        } catch (Exception ignored) {
            callback.accept(null);
        }
    }

    public static IncomingWebhookClient asClient(Webhook wb) {
        return WebhookClient.createClient(DiscordAccount, wb.getId(), wb.getToken());
    }

    public static <T> WebhookMessageCreateAction<T> served(WebhookMessageCreateAction<T> action, Guild guild) {
        return action.setUsername(guild.getName()).setAvatarUrl(guild.getIconUrl());
    }

}
