package org.solarframework.discord.core;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.interactions.Interaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.solarframework.discord.interaction.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CMDTest {

    private CMD C;

    @BeforeEach
    void newCommand() {
        C = new CMD();
        C.IT = mock(Interaction.class); // isFromAttachedGuild() defaults to false, so currentGuild() never reaches the DB
    }

    @Test
    void aButtonCarriesItsAnnotationAndMetadataInTheCustomId() {
        Button B = C.makeButton(ClickMe.class, "42", "next");
        assertEquals("click_me/42/next", B.getCustomId());
        assertEquals(ButtonStyle.PRIMARY, B.getStyle());
    }
    @Test
    void aButtonWithoutMetadataKeepsTheBareId() {
        assertEquals("nothing", C.makeButton(NothingButton.class).getCustomId());
        assertEquals(ButtonStyle.SECONDARY, C.makeButton(NothingButton.class).getStyle());
    }
    // an untranslated label falls through to the key itself rather than blowing up
    @Test
    void anUntranslatedLabelIsUsedVerbatim() {
        assertEquals("Click me!", C.makeButton(ClickMe.class, "42").getLabel());
    }
    @Test
    void anOversizedCustomIdYieldsNoButton() {
        assertNull(C.makeButton(LongIdButton.class, "1234567890"));
    }
    @Test
    void anUnannotatedCommandYieldsNoButton() {
        assertNull(C.makeButton(Unannotated.class, "42"));
    }

    @Test
    void aModalCarriesItsAnnotationAndMetadataInTheCustomId() {
        assertEquals("edit_name/42", C.makeModal(EditName.class, List.of(CMD.makeTextInput("Name", "name", TextInputStyle.SHORT, "ph", 1, 10, true)), "42").getId());
    }

    @Test
    void aStringSelectCarriesItsAnnotationAndMetadataInTheCustomId() {
        var M = C.makeStringSelectMenu(SelectFruit.class, List.of(SelectOption.of("Apple", "apple"), SelectOption.of("Pear", "pear"), SelectOption.of("Plum", "plum")), "42");
        assertEquals("fruit/42", M.getCustomId());
        assertEquals(1, M.getMinValues());
        assertEquals(3, M.getMaxValues());
        assertTrue(M.isRequired());
    }
    @Test
    void anEntitySelectCarriesItsAnnotationAndMetadataInTheCustomId() {
        var M = C.makeEntitySelectMenuBuilder(PickRole.class, "42").build();
        assertEquals("pick_role/42", M.getCustomId());
        assertEquals(1, M.getMinValues());
        assertEquals(2, M.getMaxValues());
    }
    // makeEntitySelectMenu forwards its hardcoded SelectTarget and the whole metadata array positionally into the
    // varargs of makeEntitySelectMenuBuilder: the target leaks into the custom id and the caller's metadata is
    // stringified as an array reference, so an id built this way can never be routed back. Use the builder instead.
    @Test
    void theEntitySelectShortcutManglesTheCustomId() {
        String id = C.makeEntitySelectMenu(PickRole.class, "42").getCustomId();
        assertTrue(id.startsWith("pick_role/ROLE/"), id);
        assertFalse(id.contains("42"), id);
    }

    @Test
    void aCustomCommandInheritsTheInteraction() {
        NothingButton N = C.makeCustomCMD(NothingButton.class);
        assertSame(C.IT, N.IT);
        assertNull(N.GI);
    }
    @Test
    void aCustomCommandCanBeBuiltFromItsConstructorArguments() {
        Extension E = C.makeCustomCMD(Extension.class, "hi");
        assertEquals("hi", E.getHi());
        assertSame(C.IT, E.IT);
    }
    @Test
    void aCustomCommandWithoutAMatchingConstructorIsNull() {
        assertNull(C.makeCustomCMD(Extension.class));
    }

    @Test
    void aSinglePageNeedsNoPagerRow() {
        assertNull(C.makePageRow(ClickMe.class, 1, 10, 5));
        assertNull(C.makePageRow(ClickMe.class, 1, false));
    }
    @Test
    void thePagerRowEncodesTheNeighbouringPagesAfterTheMetadata() {
        List<Button> B = C.makePageRow(ClickMe.class, 2, 10, 25, "42").getButtons();
        assertEquals("click_me/42/1", B.get(0).getCustomId());
        assertEquals("click_me/42/2", B.get(1).getCustomId());
        assertEquals("click_me/42/3", B.get(2).getCustomId());
        assertFalse(B.get(2).isDisabled());
    }
    @Test
    void theUnknownTotalPagerRowStopsAtTheLastPage() {
        ActionRow R = C.makePageRow(ClickMe.class, 3, false, "42");
        assertTrue(R.getButtons().get(2).isDisabled());
        assertFalse(R.getButtons().get(0).isDisabled());
    }

    @Test
    void modalInputsAreWrappedInALabel() {
        Label L = (Label) CMD.makeTextInput("Name", "name", TextInputStyle.SHORT, "Type it", 2, 20, true);
        assertEquals("Name", L.getLabel());
        TextInput T = (TextInput) L.getChild();
        assertEquals("name", T.getCustomId());
        assertEquals(2, T.getMinLength());
        assertEquals(20, T.getMaxLength());
        assertTrue(T.isRequired());
    }
    @Test
    void entityAndStringSelectInputsAreWrappedInALabel() {
        assertEquals("Role", ((Label) CMD.makeEntitySelectInput("Role", "role", EntitySelectMenu.SelectTarget.ROLE, "Pick", 1, 1, true)).getLabel());
        assertEquals("Fruit", ((Label) CMD.makeStringSelectInput("Fruit", "fruit", "Pick", 1, 1, true, SelectOption.of("Apple", "apple"))).getLabel());
    }
}
