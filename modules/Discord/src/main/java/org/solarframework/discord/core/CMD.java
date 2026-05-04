package org.solarframework.discord.core;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.modals.Modal;
import org.solarframework.discord.obj.Discord_GuildInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.solarframework.core.util.TimeUtils.getNow;
import static org.solarframework.db.spring.Provider.dbService;
import static org.solarframework.discord.core.BotBuilder.LogChannel;
import static org.solarframework.discord.lang.L10N.SYSL;
import static org.solarframework.discord.lang.L10N.SYSLG;

public class CMD {
    protected Interaction IT;
    protected Discord_GuildInfo GI;

    protected String TL(String key, Object... var) {
        return org.solarframework.discord.lang.L10N.TL(IT, key, var);
    }
    protected String TLG(String key, Object... var) {
        return org.solarframework.discord.lang.L10N.TLG(IT.getGuild(), key, var);
    }

    public Discord_GuildInfo currentGuild() {
        return GI == null && IT.isFromAttachedGuild() ? GI = dbService.getById(Discord_GuildInfo.class, IT.getGuild().getIdLong()).orElse(null) : GI;
    }

    public void Log(String logMsg) {
        logMsg = (currentGuild() != null ? "[" + IT.getGuild().getName() + "/" + IT.getGuild().getId() + "][" + IT.getChannel().getName() + "/" + IT.getChannel().getId() + "]: " : "[DM/Detached]") + logMsg;
        if (LogChannel != null) LogChannel.sendMessage(logMsg).queue();
        System.out.println("[" + getNow("HH:mm:ss") + "]" + logMsg.replaceAll("(\\*_`\\|)", ""));
    }
    public void LogGuild(String logMsg) {
        if (currentGuild() != null) currentGuild().LogGuild("[" + IT.getGuild().getName() + "]" + logMsg);
        Log(logMsg);
    }


    public boolean isChannelOfType(InteractionHook M, GuildChannel C, ChannelType type) {
        if (C == null && M != null) {
            M.editOriginal(SYSL(M, "missing-channel")).queue();
        } else if (C != null) {
            if (C.getType().equals(type)) return true;
            if (M != null) {
                M.editOriginal("**[" + C.getAsMention() + "]** " + SYSL(M, "wrong-channel-type-text")).queue();
            } else {
                LogGuild("**[" + C.getAsMention() + "]** " + SYSLG(C.getGuild(), "wrong-channel-type-text"));
            }
        }
        return false;
    }

    public boolean isAdmin() {
        return isAdmin(IT.getMember());
    }
    public boolean isAdmin(Member member) {
        return Objects.requireNonNull(member).hasPermission(Permission.ADMINISTRATOR);
    }
    public boolean isAdmin(InteractionHook M) {
        return isAdmin(M, IT.getMember());
    }
    public boolean isAdmin(InteractionHook M, Member member) {
        if (isAdmin(member)) return true;
        M.editOriginal(SYSL(M, "reply-failed-not-enough-permission-you", "ADMINISTRATOR")).queue();
        return false;
    }

    public boolean hasPermissionOverRole(InteractionHook M, Role R) {
        if (R == null && M != null) {
            M.editOriginal(SYSL(M, "missing-role")).queue();
        } else if (R != null) {
            Member U = R.getGuild().getSelfMember();
            if (U.canInteract(R)) return true;
            if (M != null) {
                M.editOriginal(SYSL(M, "role-access-interact-fail", R.getName())).queue();
            } else {
                LogGuild(SYSLG(R.getGuild(), "role-access-interact-fail", R.getName()));
            }
        }
        return false;
    }
    public boolean hasPermissionInChannel(InteractionHook M, GuildChannel C, Permission... Perm) {
        if (C == null && M != null) {
            M.editOriginal(SYSL(M, "missing-channel")).queue();
        } else if (C != null) {
            Member U = C.getGuild().getSelfMember();
            if (U.hasPermission(C, Perm)) return true;
            List<Permission> MissingPerms = Arrays.stream(Perm).filter(P -> !U.hasPermission(C, P)).toList();
            if (M != null) {
                M.editOriginal("**[" + C.getAsMention() + "]** " + SYSL(M, "missing-perm") + "\n" + MissingPerms.stream().map(P -> "> - " + P.getName()).collect(Collectors.joining("\n"))).queue();
            } else {
                LogGuild("**[" + C.getAsMention() + "]** " + SYSLG(C.getGuild(), "missing-perm") + "\n" + MissingPerms.stream().map(P -> "> - " + P.getName()).collect(Collectors.joining("\n")));
            }
        }
        return false;
    }
    public boolean hasPermissionInChannelNoLog(GuildChannel C, Permission... Perm) {
        return C != null && C.getGuild().getSelfMember().hasPermission(C, Perm);
    }


    protected Button makeButton(Class<? extends ButtonCMD> button, String... metadata) {
        try {
            ButtonCMD BTN = button.getDeclaredConstructor().newInstance();
            BTN.IT = IT;
            String id = BTN.getData().id() + "/" + String.join("/", metadata);
            if (id.length() > Button.ID_MAX_LENGTH) throw new RuntimeException("Button ID is too long for " + id);
            return Button.primary(id, TL(BTN.getData().label())).withStyle(BTN.getData().style());
        } catch (Exception ignored) {
            return null;
        }
    }
    protected Modal makeModal(Class<? extends ModalCMD> modal, List<ModalTopLevelComponent> components, String... metadata) {
        try {
            ModalCMD Mdl = modal.getDeclaredConstructor().newInstance();
            Mdl.IT = IT;
            String id = Mdl.getData().id() + "/" + String.join("/", metadata);
            if (id.length() > Button.ID_MAX_LENGTH) throw new RuntimeException("Modal ID is too long for " + id);
            return Modal.create(id, TL(Mdl.getData().title()))
                    .addComponents(components).build();
        } catch (Exception ignored) {
            return null;
        }
    }
    protected StringSelectMenu makeStringSelectMenu(Class<? extends StringSelectCMD> select, List<SelectOption> options, String... metadata) {
        try {
            StringSelectCMD Menu = select.getDeclaredConstructor().newInstance();
            Menu.IT = IT;
            String id = Menu.getData().id() + "/" + String.join("/", metadata);
            if (id.length() > Button.ID_MAX_LENGTH) throw new RuntimeException("String Select ID is too long for " + id);
            return StringSelectMenu.create(id)
                    .setPlaceholder(TL(Menu.getData().placeholder()))
                    .setRequiredRange(Menu.getData().minValues(), Menu.getData().maxValues()).addOptions(options)
                    .setRequired(Menu.getData().required()).build();

        } catch (Exception ignored) {
            return null;
        }
    }
    protected EntitySelectMenu makeUserSelectMenu(Class<? extends EntitySelectCMD> select, Interaction event, String... metadata) {
        return makeEntitySelectMenu(select, EntitySelectMenu.SelectTarget.USER, metadata);
    }
    protected EntitySelectMenu makeChannelSelectMenu(Class<? extends EntitySelectCMD> select, Interaction event, String... metadata) {
        return makeEntitySelectMenu(select, EntitySelectMenu.SelectTarget.CHANNEL, metadata);
    }
    protected EntitySelectMenu makeRoleSelectMenu(Class<? extends EntitySelectCMD> select, Interaction event, String... metadata) {
        return makeEntitySelectMenu(select, EntitySelectMenu.SelectTarget.ROLE, metadata);
    }
    private EntitySelectMenu makeEntitySelectMenu(Class<? extends EntitySelectCMD> select, EntitySelectMenu.SelectTarget target, String... metadata) {
        try {
            EntitySelectCMD Menu = select.getDeclaredConstructor().newInstance();
            Menu.IT = IT;
            String id = Menu.getData().id() + "/" + String.join("/", metadata);
            if (id.length() > Button.ID_MAX_LENGTH) throw new RuntimeException("Entity Select ID is too long for " + id);
            return EntitySelectMenu.create(id, target)
                    .setPlaceholder(TL(Menu.getData().placeholder()))
                    .setRequiredRange(Menu.getData().minValues(), Menu.getData().maxValues())
                    .setRequired(Menu.getData().required()).build();
        } catch (Exception ignored) {
            return null;
        }
    }
}
