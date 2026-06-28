package org.solarframework.discord.utils;

import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
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

public class ModalUtils {

    public static ModalTopLevelComponent makeTextInput(String label, String id, TextInputStyle style, String placeholder, int minLength, int maxLength, boolean required) {
        return Label.of(label, TextInput.create(id, style).setPlaceholder(placeholder).setRequiredRange(minLength, maxLength).setRequired(required).build());
    }
    public static ModalTopLevelComponent makeEntityInput(String label, String id, EntitySelectMenu.SelectTarget type, String placeholder, int minLength, int maxLength, boolean required) {
        return Label.of(label, EntitySelectMenu.create(id, type).setPlaceholder(placeholder).setRequiredRange(minLength, maxLength).setRequired(required).build());
    }
    public static ModalTopLevelComponent makeEntityInput(String label, String id, String placeholder, int minLength, int maxLength, boolean required, SelectOption... options) {
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