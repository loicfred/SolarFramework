package org.solarframework.discord.core;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.solarframework.discord.interaction.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The constructor runs the whole classgraph scan, so it is built once — it needs no JDA connection and no database. */
class DefaultListenerTest {

    private static DefaultListener L;

    @BeforeAll
    static void scan() {
        L = new DefaultListener("org.solarframework.discord.interaction");
    }

    private boolean holds(List<? extends CMD> registered, Class<?> clazz) {
        return registered.stream().anyMatch(c -> c.getClass() == clazz);
    }

    @Test
    void everyCommandTypeIsDiscoveredInTheScannedPackage() {
        assertTrue(holds(DefaultListener.SlashCommands, SlashHello.class));
        assertTrue(holds(DefaultListener.ButtonCommands, ClickMe.class));
        assertTrue(holds(DefaultListener.ModalCommands, EditName.class));
        assertTrue(holds(DefaultListener.StringSelectCommands, SelectFruit.class));
        assertTrue(holds(DefaultListener.EntitySelectCommands, PickRole.class));
    }
    // the framework's own package is always scanned alongside the caller's, so the built-ins come for free
    @Test
    void theFrameworkOwnCommandsAreDiscoveredToo() {
        assertTrue(holds(DefaultListener.SlashCommands, HelpCMD.class));
        assertTrue(holds(DefaultListener.ButtonCommands, NothingButton.class));
    }
    @Test
    void aCommandThatCannotBeInstantiatedIsSkippedRatherThanFatal() {
        assertFalse(holds(DefaultListener.ButtonCommands, Unannotated.class)); // constructor throws: no @ButtonCommand
    }
    @Test
    void plainCommandsAreNotRegisteredAsInteractions() {
        assertTrue(DefaultListener.GSlashCommands.isEmpty());
        assertTrue(DefaultListener.GUserCommands.isEmpty());
        assertTrue(DefaultListener.GMessageCommands.isEmpty());
    }

    @Test
    void aButtonIsRoutedWithItsMetadata() {
        assertEquals("Here's the data: 42", replyTo(button("click_me/42")));
    }
    // with no metadata at all the id itself lands in metadata[0], because replaceFirst finds nothing to strip
    @Test
    void aButtonWithoutMetadataSeesItsOwnId() {
        assertEquals("Here's the data: click_me", replyTo(button("click_me")));
    }
    @Test
    void anUnknownButtonIsIgnored() {
        ButtonInteractionEvent e = button("no_such_button/42");
        L.onButtonInteraction(e);
        verify(e, never()).reply(anyString());
    }
    // the registered instances are prototypes; dispatching on them would leak the previous interaction into the next
    @Test
    void dispatchNeverTouchesTheRegisteredPrototype() {
        ClickMe prototype = (ClickMe) DefaultListener.ButtonCommands.stream().filter(c -> c.getClass() == ClickMe.class).findFirst().orElseThrow();
        L.onButtonInteraction(button("click_me/42"));
        assertNull(prototype.IT);
    }

    @Test
    void aModalIsRoutedWithItsMetadata() {
        ModalInteractionEvent e = named(mock(ModalInteractionEvent.class, RETURNS_DEEP_STUBS));
        when(e.getModalId()).thenReturn("edit_name/42");
        L.onModalInteraction(e);
        verify(e).reply("Renamed 42");
    }
    @Test
    void aStringSelectIsRoutedWithItsValuesAndMetadata() {
        StringSelectInteractionEvent e = named(mock(StringSelectInteractionEvent.class, RETURNS_DEEP_STUBS));
        when(e.getComponentId()).thenReturn("fruit/42");
        when(e.getValues()).thenReturn(List.of("apple", "pear"));
        L.onStringSelectInteraction(e);
        verify(e).reply("Picked: apple,pear for 42");
    }

    private ButtonInteractionEvent button(String componentId) {
        ButtonInteractionEvent e = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        when(e.getComponentId()).thenReturn(componentId);
        return named(e);
    }

    /** LogCommand runs on its own thread and dereferences the channel name, which a mock leaves null. */
    private <T extends net.dv8tion.jda.api.interactions.Interaction> T named(T e) {
        when(e.getChannel().getName()).thenReturn("test-channel");
        return e;
    }

    private String replyTo(ButtonInteractionEvent e) {
        L.onButtonInteraction(e);
        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(e).reply(reply.capture());
        return reply.getValue();
    }
}
