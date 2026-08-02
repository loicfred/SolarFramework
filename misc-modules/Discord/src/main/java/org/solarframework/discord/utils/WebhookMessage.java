package org.solarframework.discord.utils;

import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.WebhookClient;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.solarframework.discord.obj.Discord_MessageInfo;

import java.util.Collection;
import java.util.function.Consumer;

/** A {@link MessageCreateData} plus the webhook identity (username/avatar) it must be posted under, since JDA carries those on the send action rather than on the message itself. */
public class WebhookMessage {
    private final MessageCreateBuilder B = new MessageCreateBuilder();
    private String Username, AvatarUrl;
    private Guild ServedGuild;

    /** Posts under the guild's own identity, applied at send time by {@link WebhookUtils#served} since that needs the action. An explicit {@link #setUsername} still wins. */
    public static WebhookMessage served(Guild G) {
        WebhookMessage W = new WebhookMessage();
        W.ServedGuild = G;
        return W;
    }

    public WebhookMessage setUsername(String username) {Username = username; return this;}
    public WebhookMessage setAvatarUrl(String avatarUrl) {AvatarUrl = avatarUrl; return this;}
    public WebhookMessage setContent(String content) {B.setContent(content); return this;}
    public WebhookMessage addEmbeds(MessageEmbed... embeds) {B.addEmbeds(embeds); return this;}
    public WebhookMessage addEmbeds(Collection<? extends MessageEmbed> embeds) {B.addEmbeds(embeds); return this;}
    public WebhookMessage addComponents(MessageTopLevelComponent... components) {B.addComponents(components); return this;}
    public WebhookMessage addFiles(FileUpload... files) {B.addFiles(files); return this;}
    public WebhookMessage resetEmbeds() {B.setEmbeds(); return this;}
    public WebhookMessage resetFiles() {B.setFiles(); return this;}

    public String getUsername() {return Username;}
    public String getAvatarUrl() {return AvatarUrl;}
    public MessageCreateData build() {return B.build();}

    /** Posts a new message under this identity. */
    public WebhookMessageCreateAction<Message> send(WebhookClient<Message> C) {
        WebhookMessageCreateAction<Message> A = C.sendMessage(build());
        return Username == null && ServedGuild != null ? WebhookUtils.served(A, ServedGuild) : A.setUsername(Username).setAvatarUrl(AvatarUrl);
    }
    /** Edits the tracked message, or creates it under this identity when it does not exist yet. */
    public void into(Discord_MessageInfo MI, Consumer<Message> callback) {
        if (Username == null && ServedGuild != null) MI.ModifyWebhookMessageElseCreate(build(), callback);
        else MI.ModifyWebhookMessageElseCreate(build(), Username, AvatarUrl, callback);
    }
}
