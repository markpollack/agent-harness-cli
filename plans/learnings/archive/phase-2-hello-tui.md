# Phase 2 Learnings: Hello World TUI

## Summary

Created a minimal TUI4J application with Picocli CLI entry point.

## Key Learnings

### TUI4J API

**Model interface** requires three methods:
```java
public interface Model {
    Command init();                                    // Run on startup
    UpdateResult<? extends Model> update(Message msg); // Handle events
    String view();                                     // Render to string
}
```

**KeyPressMessage** creation:
```java
Key key = new Key(KeyType.KeyRunes, new char[]{'q'});
KeyPressMessage msg = new KeyPressMessage(key);
```

**UpdateResult** for returning new state + optional command:
```java
new UpdateResult<>(this, QuitMessage::new);  // Quit
new UpdateResult<>(this, null);               // No command
UpdateResult.from(newModel);                  // New model, no command
```

**Command.execute()** not `get()`:
```java
Message produced = result.command().execute();
```

### Lipgloss Styling Limitation

**Problem**: Lipgloss `Style.render()` requires terminal initialization. Fails in unit tests with:
```
NullPointerException: "com.williamcallahan.tui4j.term.TerminalInfo.infoProvider" is null
```

**Solution**: Defer styling to Phase 7 (Polish). Use plain text for now.

**Future fix**: Investigate TUI4J's test patterns or mock terminal provider.

### Picocli Integration

Simple integration - just add `@Command` annotation and implement `Callable<Integer>`:

```java
@Command(name = "agent-harness-cli", mixinStandardHelpOptions = true, version = "0.1.0")
public class AgentCli implements Callable<Integer> {
    @Option(names = {"-d", "--directory"}, defaultValue = ".")
    private Path directory;

    @Override
    public Integer call() {
        new Program(new HelloModel()).run();
        return 0;
    }
}
```

### Test Patterns

TUI4J's own tests don't test `view()` with Lipgloss - they focus on logic (key bindings, state transitions).

For testing views with styling, likely need to either:
1. Initialize terminal info provider for tests
2. Mock the terminal info
3. Test rendering separately from styling

## Files Created

| File | Purpose |
|------|---------|
| `cli-core/src/main/java/.../HelloModel.java` | TUI4J Model |
| `cli-core/src/test/java/.../HelloModelTest.java` | 8 unit tests |
| `cli-app/src/main/java/.../AgentCli.java` | Picocli entry point |
| `tests/expect/README.md` | Test infrastructure docs |

## What Worked Well

- TUI4J's Elm-style architecture is clean and testable
- Picocli provides excellent CLI argument handling with minimal code
- Separating cli-core (TUI) from cli-app (entry point) is good architecture

## What Could Be Improved

- Lipgloss styling needs terminal init - investigate for Phase 7
- expect tests require `--linear` mode (Phase 4)
- Consider creating test utilities for TUI4J testing

## Next Steps (Phase 3)

- Add TextInput component for user input
- Handle Enter key to submit
- Display conversation history
