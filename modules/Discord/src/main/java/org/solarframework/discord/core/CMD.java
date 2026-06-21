package org.solarframework.discord.core;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.checkboxgroup.CheckboxGroup;
import net.dv8tion.jda.api.components.checkboxgroup.CheckboxGroupOption;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.radiogroup.RadioGroup;
import net.dv8tion.jda.api.components.radiogroup.RadioGroupOption;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.modals.Modal;
import org.solarframework.discord.obj.Discord_GuildInfo;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.solarframework.core.util.TimeUtils.getNow;
import static org.solarframework.db.spring.DatabaseObject.retrieveServiceFor;
import static org.solarframework.discord.core.BotBuilder.LogChannel;
import static org.solarframework.discord.lang.L10N.SYSL;

public class CMD {
    protected Interaction IT;
    protected Discord_GuildInfo GI;

    protected String SYSL(String key, Object... var) {
        return org.solarframework.discord.lang.L10N.SYSL(IT, key, var);
    }
    protected String SYSLG(String key, Object... var) {
        return org.solarframework.discord.lang.L10N.SYSLG(IT.getGuild(), key, var);
    }
    protected String TL(String key, Object... var) {
        return org.solarframework.discord.lang.L10N.TL(IT, key, var);
    }
    protected String TLG(String key, Object... var) {
        return org.solarframework.discord.lang.L10N.TLG(IT.getGuild(), key, var);
    }

    public Discord_GuildInfo currentGuild() {
        return GI == null && IT.isFromAttachedGuild() ? GI = retrieveServiceFor(Discord_GuildInfo.class).getById(Discord_GuildInfo.class, IT.getGuild().getIdLong()).orElse(null) : GI;
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
            M.editOriginal(SYSL("missing-channel")).queue();
        } else if (C != null) {
            if (C.getType().equals(type)) return true;
            if (M != null) {
                M.editOriginal("**[" + C.getAsMention() + "]** " + SYSL("wrong-channel-type-text")).queue();
            } else {
                LogGuild("**[" + C.getAsMention() + "]** " + SYSLG("wrong-channel-type-text"));
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
        M.editOriginal(SYSL("reply-failed-not-enough-permission-you", "ADMINISTRATOR")).queue();
        return false;
    }

    public boolean hasPermissionOverRole(InteractionHook M, Role R) {
        if (R == null && M != null) {
            M.editOriginal(SYSL("missing-role")).queue();
        } else if (R != null) {
            Member U = R.getGuild().getSelfMember();
            if (U.canInteract(R)) return true;
            if (M != null) {
                M.editOriginal(SYSL("role-access-interact-fail", R.getName())).queue();
            } else {
                LogGuild(SYSLG("role-access-interact-fail", R.getName()));
            }
        }
        return false;
    }
    public boolean hasPermissionInChannel(InteractionHook M, GuildChannel C, Permission... Perm) {
        if (C == null && M != null) {
            M.editOriginal(SYSL("missing-channel")).queue();
        } else if (C != null) {
            Member U = C.getGuild().getSelfMember();
            if (U.hasPermission(C, Perm)) return true;
            List<Permission> MissingPerms = Arrays.stream(Perm).filter(P -> !U.hasPermission(C, P)).toList();
            if (M != null) {
                M.editOriginal("**[" + C.getAsMention() + "]** " + SYSL("missing-perm") + "\n" + MissingPerms.stream().map(P -> "> - " + P.getName()).collect(Collectors.joining("\n"))).queue();
            } else {
                LogGuild("**[" + C.getAsMention() + "]** " + SYSLG("missing-perm") + "\n" + MissingPerms.stream().map(P -> "> - " + P.getName()).collect(Collectors.joining("\n")));
            }
        }
        return false;
    }
    public boolean hasPermissionInChannelNoLog(GuildChannel C, Permission... Perm) {
        return C != null && C.getGuild().getSelfMember().hasPermission(C, Perm);
    }

    public boolean isFromGuild() {
        return IT.isFromGuild();
    }
    public boolean isFromGuild(InteractionHook M) {
        if (isFromGuild()) return true;
        M.editOriginal(SYSL("reply-failed-not-in-guild")).queue();
        return false;
    }

    public boolean isUserInGuild(String userID) {
        return IT.getGuild().getMemberById(userID) != null;
    }
    public boolean isUserInGuild(InteractionHook M, String userID) {
        if (isUserInGuild(userID)) return true;
        M.editOriginal(SYSL("user-not-part-of-guild")).queue();
        return false;
    }
    public boolean isUserInGuild(Guild guild, String userID) {
        return guild.getMemberById(userID) != null;
    }
    public boolean isUserInGuild(InteractionHook M, Guild guild, String userID) {
        if (isUserInGuild(guild, userID)) return true;
        M.editOriginal(SYSL("user-not-part-of-guild")).queue();
        return false;
    }


    protected Button makeButton(Class<? extends ButtonCMD> button, Object... metadata) {
        try {
            ButtonCMD BTN = button.getDeclaredConstructor().newInstance();
            BTN.IT = IT;
            String id = BTN.getData().id() + Arrays.stream(metadata).map(Object::toString).reduce("", (a, b) -> a + "/" + b);
            if (id.length() > Button.ID_MAX_LENGTH) throw new RuntimeException("Button ID is too long for " + id);
            return Button.primary(id, TL(BTN.getData().label())).withStyle(BTN.getData().style());
        } catch (Exception ignored) {
            return null;
        }
    }

    protected Modal.Builder makeModalBuilder(Class<? extends ModalCMD> modal, List<ModalTopLevelComponent> components, Object... metadata) {
        try {
            ModalCMD Mdl = modal.getDeclaredConstructor().newInstance();
            Mdl.IT = IT;
            String id = Mdl.getData().id() + Arrays.stream(metadata).map(Object::toString).reduce("", (a, b) -> a + "/" + b);
            if (id.length() > Button.ID_MAX_LENGTH) throw new RuntimeException("Modal ID is too long for " + id);
            return Modal.create(id, TL(Mdl.getData().title())).addComponents(components);
        } catch (Exception ignored) {
            return null;
        }
    }
    protected Modal makeModal(Class<? extends ModalCMD> modal, List<ModalTopLevelComponent> components, Object... metadata) {
        return makeModalBuilder(modal, components, metadata).build();
    }

    protected StringSelectMenu.Builder makeStringSelectMenuBuilder(Class<? extends StringSelectCMD> select, List<SelectOption> options, Object... metadata) {
        try {
            StringSelectCMD Menu = select.getDeclaredConstructor().newInstance();
            Menu.IT = IT;
            String id = Menu.getData().id() + Arrays.stream(metadata).map(Object::toString).reduce("", (a, b) -> a + "/" + b);
            if (id.length() > Button.ID_MAX_LENGTH) throw new RuntimeException("String Select ID is too long for " + id);
            return StringSelectMenu.create(id)
                    .setPlaceholder(TL(Menu.getData().placeholder()))
                    .setRequiredRange(Menu.getData().minValues(), Menu.getData().maxValues()).addOptions(options)
                    .setRequired(Menu.getData().required());

        } catch (Exception ignored) {
            return null;
        }
    }
    protected EntitySelectMenu.Builder makeEntitySelectMenuBuilder(Class<? extends EntitySelectCMD> select, Object... metadata) {
        try {
            EntitySelectCMD Menu = select.getDeclaredConstructor().newInstance();
            Menu.IT = IT;
            String id = Menu.getData().id() + Arrays.stream(metadata).map(Object::toString).reduce("", (a, b) -> a + "/" + b);
            if (id.length() > Button.ID_MAX_LENGTH) throw new RuntimeException("Entity Select ID is too long for " + id);
            return EntitySelectMenu.create(id, Menu.getData().type())
                    .setPlaceholder(TL(Menu.getData().placeholder()))
                    .setRequiredRange(Menu.getData().minValues(), Menu.getData().maxValues())
                    .setRequired(Menu.getData().required());
        } catch (Exception ignored) {
            return null;
        }
    }

    protected StringSelectMenu makeStringSelectMenu(Class<? extends StringSelectCMD> select, List<SelectOption> options, Object... metadata) {
        return makeStringSelectMenuBuilder(select, options, metadata).build();
    }
    protected EntitySelectMenu makeEntitySelectMenu(Class<? extends EntitySelectCMD> select, Object... metadata) {
        return makeEntitySelectMenuBuilder(select, EntitySelectMenu.SelectTarget.ROLE, metadata).build();
    }

    protected <T extends CMD> T makeCustomCMD(Class<T> clazz) {
        try {
            T obj = clazz.getDeclaredConstructor().newInstance();
            obj.IT = IT;
            obj.GI = GI;
            return obj;
        } catch (Exception ignored) {
            return null;
        }
    }
    protected <T extends CMD> T makeCustomCMD(Class<T> clazz, Object... args) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor(Arrays.stream(args).map(Object::getClass).toArray(Class[]::new));
            T obj = constructor.newInstance(args);
            obj.IT = IT;
            obj.GI = GI;
            return obj;
        } catch (Exception ignored) {
            return null;
        }
    }


    public static ModalTopLevelComponent makeTextInput(String label, String id, TextInputStyle style, String placeholder, int minLength, int maxLength, boolean required) {
        return Label.of(label, TextInput.create(id, style).setPlaceholder(placeholder).setRequiredRange(minLength, maxLength).setRequired(required).build());
    }
    public static ModalTopLevelComponent makeEntitySelectInput(String label, String id, EntitySelectMenu.SelectTarget type, String placeholder, int minLength, int maxLength, boolean required) {
        return Label.of(label, EntitySelectMenu.create(id, type).setPlaceholder(placeholder).setRequiredRange(minLength, maxLength).setRequired(required).build());
    }
    public static ModalTopLevelComponent makeStringSelectInput(String label, String id, String placeholder, int minLength, int maxLength, boolean required, SelectOption... options) {
        return Label.of(label, StringSelectMenu.create(id).setPlaceholder(placeholder).addOptions(options).setRequiredRange(minLength, maxLength).setRequired(required).build());
    }
    public static ModalTopLevelComponent makeCheckboxInput(String label, int minValues, int maxValues, boolean required, CheckboxGroupOption... options) {
        return Label.of(label, CheckboxGroup.create("checkbox-group").addOptions(options).setRequiredRange(minValues, maxValues).setRequired(required).build());
    }
    public static ModalTopLevelComponent makeRadioInput(String label, String id, boolean required, RadioGroupOption... options) {
        return Label.of(label, RadioGroup.create(id).addOptions(options).setRequired(required).build());
    }
    public static ModalTopLevelComponent makeAttachmentInput(String label, String id, int minValues, int maxValues, boolean required) {
        return Label.of(label, AttachmentUpload.create(id).setRequiredRange(minValues, maxValues).setRequired(required).build());
    }
}
