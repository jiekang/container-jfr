package es.andrewazor.containertest.commands;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.TestBase;
import es.andrewazor.containertest.commands.CommandRegistry.CommandDefinitionException;

@ExtendWith(MockitoExtension.class)
public class CommandRegistryTest extends TestBase {

    CommandRegistry registry;

    @Nested
    class WithEmptySetCommands {

        @BeforeEach
        public void setup() {
            registry = new CommandRegistry(mockClientWriter, Collections.emptySet());
        }

        @Test
        public void shouldReturnEmptyRegisteredCommandNames() {
            assertThat("registered commands should be empty", registry.getRegisteredCommandNames(),
                    equalTo(Collections.emptySet()));
        }

        @Test
        public void shouldReturnEmptyAvailableCommandNames() {
            assertThat("available commands should be empty", registry.getAvailableCommandNames(),
                    equalTo(Collections.emptySet()));
        }

        @Test
        public void shouldNoOpOnExecute() throws Exception {
            registry.execute("foo", new String[] {});
            assertThat(stdout(), equalTo("Command \"foo\" not recognized\n"));
        }

        @Test
        public void shouldNotValidateCommands() throws Exception {
            assertFalse(registry.validate("foo", new String[0]));
            assertThat(stdout(), equalTo("Command \"foo\" not recognized\n"));
        }
    }

    @Nested
    class WithCommandDefinitions {

        FooCommand fooCommand = new FooCommand();
        BarCommand barCommand = new BarCommand();

        Command[] commands = new Command[] { fooCommand, barCommand };

        @BeforeEach
        public void setup() {
            registry = new CommandRegistry(mockClientWriter, new HashSet<Command>(Arrays.asList(commands)));
        }

        @Test
        public void shouldReturnRegisteredCommandNames() {
            assertThat("registered command names should be returned", registry.getRegisteredCommandNames(),
                    equalTo(new HashSet<String>(Arrays.asList(fooCommand.getName(), barCommand.getName()))));
        }

        @Test
        public void shouldReturnAvailableCommandNames() {
            assertThat("available command names should be returned", registry.getAvailableCommandNames(),
                    equalTo(new HashSet<String>(Arrays.asList(fooCommand.getName()))));
        }

        @Test
        public void shouldExecuteRegisteredAndAvailableCommand() throws Exception {
            assertThat("command should not have been executed", fooCommand.value, nullValue());
            registry.execute("foo", new String[] { "arg" });
            assertThat("command should have been executed", fooCommand.value, equalTo("arg"));
        }

        @Test
        public void shouldNotExecuteRegisteredAndUnavailableCommand() throws Exception {
            assertThat("command should not have been executed", barCommand.value, nullValue());
            registry.execute("bar", new String[] { "arg" });
            assertThat("command should not have been executed", barCommand.value, nullValue());
            assertThat(stdout(), equalTo("Command \"bar\" not available\n"));
        }

        @Test
        public void shouldNoOpOnUnregisteredCommand() throws Exception {
            assertThat("command should not have been executed", fooCommand.value, nullValue());
            assertThat("command should not have been executed", barCommand.value, nullValue());
            registry.execute("baz", new String[] { "arg" });
            assertThat("command should not have been executed", fooCommand.value, nullValue());
            assertThat("command should not have been executed", barCommand.value, nullValue());
            assertThat(stdout(), equalTo("Command \"baz\" not recognized\n"));
        }

        @Test
        public void shouldNotValidateUnknownCommands() throws Exception {
            assertFalse(registry.validate("baz", new String[0]));
            assertThat(stdout(), equalTo("Command \"baz\" not recognized\n"));
        }

        @Test
        public void shouldNotValidateInvalidCommands() throws Exception {
            assertFalse(registry.validate("bar", new String[0]));
            assertFalse(registry.validate(null, new String[0]));
            assertFalse(registry.validate("", new String[0]));
            assertFalse(registry.validate("  ", new String[0]));
        }

        @Test
        public void shouldValidateCommands() throws Exception {
            assertTrue(registry.validate("foo", new String[0]));
        }

        @Test
        public void shouldHandleNullCommandAvailability() throws Exception {
            assertFalse(registry.isCommandAvailable(null));
        }

        @Test
        public void shouldHandleEmptyCommandAvailability() throws Exception {
            assertFalse(registry.isCommandAvailable(""));
        }

        @Test
        public void shouldHandleBlankCommandAvailability() throws Exception {
            assertFalse(registry.isCommandAvailable("  "));
        }
    }

    @Nested
    class WithConflictingCommandDefinitions {
        @Test
        public void shouldThrowCommandDefinitionException() {
            CommandDefinitionException thrown = assertThrows(CommandDefinitionException.class,
                    () -> new CommandRegistry(mockClientWriter,
                            new HashSet<Command>(Arrays.asList(new FooCommand(), new DuplicateFooCommand()))),
                    "should throw CommandDefinitionException for duplicate definitions");
            assertThat(thrown.getMessage(),
                allOf(
                    containsString("\"foo\" command definitions provided by class"),
                    containsString("es.andrewazor.containertest.commands.CommandRegistryTest.DuplicateFooCommand"),
                    containsString("AND class"),
                    containsString("es.andrewazor.containertest.commands.CommandRegistryTest.FooCommand")
                )
            );
        }
    }

    static class FooCommand implements Command {
        String value = null;

        @Override
        public String getName() {
            return "foo";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean validate(String[] args) {
            return true;
        }

        @Override
        public void execute(String[] args) {
            this.value = args[0];
        }
    }

    static class BarCommand implements Command {
        String value = null;

        @Override
        public String getName() {
            return "bar";
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public boolean validate(String[] args) {
            return false;
        }

        @Override
        public void execute(String[] args) {
            this.value = args[0];
        }
    }

    static class DuplicateFooCommand extends FooCommand {
    }

}
