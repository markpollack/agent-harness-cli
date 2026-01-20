# Phase 3 Learnings: Text Input

## Summary

Added text input functionality to the TUI using TUI4J's TextInput component. User can type messages, press Enter to submit, and see conversation history.

## Key Learnings

### TextInput API

**Creation and configuration:**
```java
TextInput input = new TextInput();
input.setPrompt("> ");
input.setPlaceholder("Type a message...");
input.setCharLimit(4000);
input.focus();  // Required to enable input
```

**In init() - enable cursor blinking:**
```java
@Override
public Command init() {
    return TextInput::blink;
}
```

**Getting/resetting value:**
```java
String text = input.value();
boolean empty = input.isEmpty();
input.reset();  // Clears input
```

### Key Press Detection

**Enter key (submit input):**
```java
if (KeyAliases.getKeyType(KeyAlias.KeyEnter) == keyPress.type()) {
    // Handle submission
}
```

**Ctrl+C:**
```java
if (KeyAliases.getKeyType(KeyAlias.KeyCtrlC) == keyPress.type()) {
    return new UpdateResult<>(this, QuitMessage::new);
}
```

**Important**: Use `KeyAliases.getKeyType()` to map logical keys to actual KeyTypes.

### Message Delegation

TextInput handles its own key events. Delegate messages to it:
```java
@Override
public UpdateResult<? extends Model> update(Message msg) {
    // Handle our keys first (Enter, Ctrl+C, etc.)
    if (msg instanceof KeyPressMessage keyPress) {
        // ...
    }

    // Delegate to TextInput for other messages
    UpdateResult<? extends Model> inputResult = input.update(msg);
    return new UpdateResult<>(this, inputResult.command());
}
```

### Terminal Info for Tests

**Problem**: TextInput.view() uses Lipgloss which requires terminal initialization. Tests fail with:
```
NullPointerException: "com.williamcallahan.tui4j.term.TerminalInfo.infoProvider" is null
```

**Solution**: Initialize terminal info in @BeforeAll:
```java
@BeforeAll
static void initTerminal() {
    TerminalInfo.provide(() -> new TerminalInfo(false, new NoColor()));
    Renderer.defaultRenderer().setColorProfile(ColorProfile.Ascii);
}
```

### Brief Chat App Reference

Cloned Brief (`github.com/WilliamAGH/brief`) - excellent TUI4J chat reference:
- `ChatConversationScreen.java` - main chat UI
- `HistoryViewport.java` - scrollable history
- `TuiTheme.java` - styling utilities
- Shows patterns for spinner, slash commands, model selection

### Immutable Model Updates

TUI4J expects immutable model updates. When state changes, create new model:
```java
private UpdateResult<ChatModel> submitInput(String text) {
    List<ChatEntry> newHistory = new ArrayList<>(history);
    newHistory.add(ChatEntry.user(text));
    newHistory.add(ChatEntry.assistant("You said: " + text));

    ChatModel newModel = new ChatModel(newHistory);
    return new UpdateResult<>(newModel, null);
}
```

## Files Created

| File | Purpose |
|------|---------|
| `cli-core/.../ChatEntry.java` | Chat message record |
| `cli-core/.../ChatModel.java` | Main TUI model with TextInput |
| `cli-core/test/.../ChatEntryTest.java` | 3 tests |
| `cli-core/test/.../ChatModelTest.java` | 16 tests |
| `tests/expect/text-input-echo.exp` | Integration test template |

## Test Count

- **Unit tests**: 24 total (16 ChatModel + 3 ChatEntry + 8 HelloModel)

## What Worked Well

- TextInput component is easy to integrate
- KeyAliases provides clean key detection
- Brief app provided excellent patterns
- Terminal info initialization solved test issues

## What Could Be Improved

- Lipgloss styling deferred to Phase 7 (still requires terminal init)
- Integration tests require --linear mode (Phase 4)
- Scrollback/viewport for long conversations (Phase 6)

## Next Steps (Phase 4)

- Implement --linear mode for expect tests
- Implement -p/--print mode for CI
- Connect to MiniAgent for actual AI responses
- Display tool calls
