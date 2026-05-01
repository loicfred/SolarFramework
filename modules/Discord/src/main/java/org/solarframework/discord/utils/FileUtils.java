package org.solarframework.discord.utils;

import com.sun.tools.javac.Main;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.ImageProxy;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.solarframework.discord.core.BotBuilder.BotGuild;
import static org.solarframework.discord.core.BotBuilder.TemporaryFilesChannel;

public class FileUtils {

    public static String getFileUrl(InputStream stream, String name) {
        try (FileUpload F = FileUpload.fromData(stream, name)) {
            return TemporaryFilesChannel.sendFiles(F).submit().orTimeout(15, TimeUnit.SECONDS).get().getAttachments().getFirst().getUrl();
        } catch (Exception e) {
            return null;
        }
    }
    public static String getFileUrl(File file, String name) {
        try (FileUpload F = FileUpload.fromData(Objects.requireNonNull(file), name)) {
            return TemporaryFilesChannel.sendFiles(F).submit().orTimeout(15, TimeUnit.SECONDS).get().getAttachments().getFirst().getUrl();
        } catch (Exception e) {
            return null;
        }
    }
    public static String getFileUrl(String temporaryUrl, String name) {
        try (FileUpload F = FileUpload.fromData(new ImageProxy(temporaryUrl).downloadToFile(new File("/" + name.hashCode() + ".png")).get(), name)) {
            return TemporaryFilesChannel.sendFiles(F).submit().orTimeout(15, TimeUnit.SECONDS).get().getAttachments().getFirst().getUrl();
        } catch (Exception e) {
            return temporaryUrl;
        } finally {
            new File("/" + name.hashCode() + ".png").delete();
        }
    }


    public static void getFileUrl(InputStream stream, String name, Consumer<String> url) {
        try (FileUpload F = FileUpload.fromData(stream, name)) {
            TemporaryFilesChannel.sendFiles(F).queue(M -> url.accept(M.getAttachments().getFirst().getUrl()));
        } catch (Exception e) {
            url.accept(null);
        }
    }
    public static void getFileUrl(File file, String name, Consumer<String> url) {
        try (FileUpload F = FileUpload.fromData(Objects.requireNonNull(file), name)) {
            TemporaryFilesChannel.sendFiles(F).queue(M -> url.accept(M.getAttachments().getFirst().getUrl()));
        } catch (Exception e) {
            url.accept(null);
        }
    }
    public static void getFileUrl(String temporaryUrl, String name, Consumer<String> url) {
        try (FileUpload F = FileUpload.fromData(new ImageProxy(temporaryUrl).downloadToFile(new File("/" + name.hashCode() + ".png")).get(), name)) {
            TemporaryFilesChannel.sendFiles(F).queue(M -> {
                url.accept(M.getAttachments().getFirst().getUrl());
                new File("/" + name.hashCode() + ".png").delete();
            });
        } catch (Exception e) {
            url.accept(null);
        } finally {
            new File("/" + name.hashCode() + ".png").delete();
        }
    }

    public static void AddTemporaryUserEmoji(User user, Message message, int num) {
        try {
            File img = new File("/" + user.getId() + ".png");
            ImageIO.write(ImageIO.read(URI.create(user.getEffectiveAvatarUrl()).toURL()), "png", img);
            if (img.exists()) {
                BotGuild.createEmoji("Vote" + num, Icon.from(img))
                        .flatMap(richCustomEmoji -> message.addReaction(Emoji.fromCustom(richCustomEmoji))
                                .flatMap(Void -> {
                                    richCustomEmoji.delete().queue();
                                    img.delete();
                                    return null;
                                })).queue();
            } else {
                try (InputStream is = Main.class.getResourceAsStream("/static/img/AvatarDefault.png")) {
                    BotGuild.createEmoji("Vote" + num, Icon.from(is))
                            .flatMap(richCustomEmoji -> message.addReaction(Emoji.fromCustom(richCustomEmoji))
                                    .flatMap(Void -> {
                                        richCustomEmoji.delete().queue();
                                        img.delete();
                                        return null;
                                    })).queue();
                } catch (Exception ignored) {}
            }
        } catch (IOException | NullPointerException ignored) {}
    }
}
