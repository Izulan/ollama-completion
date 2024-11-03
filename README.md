# ![Ollama Org](./src/main/resources/META-INF/pluginIcon.svg) Ollama Completion

![Build](https://github.com/Izulan/ollama-completion/workflows/Build/badge.svg)

<!-- Plugin description -->
This IntelliJ plugin adds an Ollama-backed provider for inline completions.

- Supports arbitrary models with configurable parameters
- Caches requests for increased responsiveness
- Includes settings and a status widget

<!-- Plugin description end -->

## Prerequisites

### Install Ollama

Install a version of Ollama starting from `0.4` ([get here](https://github.com/ollama/ollama/releases)).
Previous releases have broken stop-sequence handling, which might cut off unwanted parts of completions.

### Configure a Model

- Head to <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Ollama Completion</kbd>
- Enter your Ollama URL and connect to get an overview of installed models.
- Select a model by clicking the checkmark in the toolbar
- It should now show a checkmark next to the model
- (Optional) Edit other properties such as context size and system message

It can be quite challenging to configure small off-the-shelf models for completions.
Completions can be multi-line, but single-line tends to be easier,
since these models may not adhere to the system prompt very well.

#### Good Sample Model (for Code)

I've found a reconfigured **Qwen2.5-Coder** to be the best choice.
It works very well for full-line-code-completion.

1. Create a modelfile (text file) with the following content:

    ```
    FROM qwen2.5-coder:1.5b

    TEMPLATE """<|fim_prefix|>{{ .Prompt }}<|fim_suffix|><|fim_middle|>"""

    PARAMETER stop "<|endoftext|>"
    PARAMETER stop "
    "
    ```

2. Load it using the Ollama CLI:
    ```
    ollama create full-line-completion -f <modelfile>
    ```
   where `<modelfile>` is the name of the file from step 1.

Alternatively, click the plus icon in the settings page and paste the modelfile.

This is using the 1.5B (~1GB) parameter version of Qwen2.5-Coder.
It feels nearly instant and the results are sufficient for repetitive tasks.

Remap the shortcut for the insertion of inline completions from <kbd>Tab</kbd> to something else
or this will get very annoying, really fast.

If you want something smarter that is still quite fast, use `qwen2.5-coder:7b` (~5GB).
But this configuration can get tight with an IDE and 16GB RAM.

Note that, in rare instances, Ollama will get stuck and time out (probably due to resource limitations).
This only affects the current completion, so type something, and it will resolve itself.

## Installation

- Manually:

  Download the [latest release](https://github.com/Izulan/ollama-completion/releases/latest) and install it manually
  using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Things to Try

Qwen2.5 is especially good at popular and repetitive programming languages, like Java.
Also, files should be largely self-contained, since we don't pass any additional context to the LLM.

- Good
  example: [Generic entity from the spring.io website](https://raw.githubusercontent.com/spring-attic/sagan/refs/heads/main/sagan-site/src/main/java/sagan/site/projects/Project.java)
- Torture test: [SQLite](https://raw.githubusercontent.com/sqlite/sqlite/refs/heads/master/src/btree.c)

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template

[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
