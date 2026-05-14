# ByteBanter
**AnvilSecure** presents **ByteBanter**! **ByteBanter** is a Burp Suite extension that leverages Large Language Models (LLMs) to generate intelligent and context-aware payloads for Burp Intruder. By integrating AI capabilities, ByteBanter enhances automated security testing by producing diverse and adaptive payloads tailored to specific testing scenarios.

## Features
* **LLM-Driven Payload Generation:** Utilizes LLMs to create dynamic payloads based on user-defined prompts and contexts.
* **Seamless Burp Integration:** Registers as a custom payload generator within Burp Intruder, allowing easy selection and use.
* **Configurable Prompts:** Users can define and customize prompts to guide the LLM in generating desired payloads.
* **Support for Multiple LLM Providers:** Burp AI is the default provider; the extension also supports Ollama, the Anthropic Messages API, any OpenAI-compatible `chat/completions` endpoint (e.g. OpenAI, Oobabooga, LM Studio, vLLM), and the Claude Code CLI (locally installed) for users who prefer to route prompts through their existing Anthropic subscription.

This version of ByteBanter complies with the BApp Store standards for extensions that use third-party LLMs:
* Declares `EnhancedCapability.AI_FEATURES` and verifies that Burp AI is enabled before any LLM call.
* Burp AI is configured as the default provider.
* All third-party LLM requests use the Montoya API networking capabilities (`api.http().sendRequest`).
* All third-party LLM requests are sent with `RequestOptions.withUpstreamTLSVerification()` to verify the integrity of the data sent to the provider.

## Installation
You can find this version of ByteBanter in the official BurpSuite BApp store. But If you prefer you can compile the code by yourself according to the following instructions.

* Clone the Repository:

```bash
git clone https://github.com/anvilsecure/bytebanter-burpai/
```
* Build the Extension: Navigate to the project directory and build the JAR file using your preferred build tool (e.g., Maven or Gradle).

```bash
mvn clean package
```

```bash
gradle build
```

* Load into Burp Suite:
  * Open Burp Suite.
  * Go to the **Extender** tab. 
  * Click on **Add**. 
  * Select the built JAR file (the `uber` one) to load the extension.

## Usage
* Configure the engine:
  * Open the newly added ByteBanter tab in Burp Suite.
  * Pick an engine from the combo box in the top right corner (Burp AI by default; switch to Ollama, OpenAI-compatible, or Anthropic if you prefer).
  * Fill in URL / API key / model for the selected engine — see [Configuration](#configuration) for per-engine details.
* Set Up Intruder Attack:
  * Go to the Intruder tab.
  * Configure your target and positions as usual.
* Select ByteBanter as Payload Source:
  * In the Payloads tab:
  * Set Payload type to Extension-generated.
  * Choose ByteBanter from the list of available generators.
* Define Prompts:
  * Within the ByteBanter tab, create and customize prompts that will guide the LLM in generating payloads.
  * Optionally enable Success Verification to highlight responses that meet a user-defined criterion (see [Success Verification](#success-verification) below).
* Start the Attack:
  * Run the Intruder attack.
  * ByteBanter will generate and supply payloads dynamically using the configured LLM.

## Configuration
In the **ByteBanter** extension tab, select the engine you want to use from the combo box in the top right corner (Burp AI is the default). Each engine has its own settings; switching engines preserves the per-engine configuration.

### Per-engine configuration
* **Burp AI:** no endpoint configuration needed. Make sure AI features are enabled in Burp settings; ByteBanter will surface a dialog if they are not.
* **Ollama:** set the base URL (default `http://localhost:11434/`). The model dropdown auto-populates by querying `/api/tags` on the configured URL as soon as it becomes reachable.
* **OpenAI-compatible (Chat Completions):** set the URL of any `/v1/chat/completions` endpoint (OpenAI itself, Oobabooga, LM Studio, vLLM, etc.) and add an `Authorization: Bearer <token>` header in the headers field if the provider requires it.
* **Anthropic:** keep the default URL `https://api.anthropic.com/v1/messages`. There is **no dedicated field** for the API key — paste it in the **Headers** field of the engine configuration:

  ```
  x-api-key: YOUR_ANTHROPIC_API_KEY
  ```

  ByteBanter automatically adds the required `anthropic-version` and `Content-Type` headers, so `x-api-key` is the only one you need to enter. Pick a model from the editable dropdown (you can also type a custom model ID).

  When the URL points to the official Anthropic API (`api.anthropic.com`), the engine refuses to send the request if `x-api-key` is missing from the Headers field. If you point the URL at a proxy or a mock server that does not require this header, the check is skipped.

> **Multiple headers** — the Headers field accepts **one header per line**. Use a literal newline as separator. Example for Anthropic with an extra custom header:
>
> ```
> x-api-key: YOUR_ANTHROPIC_API_KEY
> x-custom-trace: my-trace-id
> ```
>
> The same applies to the OpenAI-compatible engine if the provider needs both an `Authorization` and additional headers. Empty lines are ignored.

* **Claude Code (CLI):** routes prompts through the [Claude Code](https://docs.claude.com/en/docs/claude-code) command-line agent installed on the same machine as Burp. Useful when you want to use your existing Anthropic subscription (Claude Pro / Max) without provisioning a separate API key, or when you have Bedrock/Vertex auth already configured for Claude Code.

  Configuration:
  * **Claude Code binary** — defaults to `claude`. Override with an absolute path if the CLI is not on the user's `PATH` (e.g. `/Users/you/.local/bin/claude`).
  * **Model** — leave empty to use whatever model Claude Code is configured for, or pick / type a Claude model ID such as `claude-sonnet-4-6`, `claude-opus-4-7`, etc.

  How it works internally:
  * ByteBanter spawns `claude -p --output-format text` and pipes the conversation into the process via stdin.
  * The system role from the conversation is forwarded with `--append-system-prompt`.
  * Authentication is handled entirely by Claude Code itself (whatever you configured: API key, OAuth via `claude.ai`, AWS Bedrock, Google Vertex).

  Caveats:
  * Requires the Claude Code CLI to be installed on the host running Burp.
  * Each LLM call spawns a subprocess (≈ 1–3 seconds of overhead). With Success Verification enabled this adds up quickly, so use a small `requestsLimit`.
  * **BApp Store note:** this engine bypasses the Montoya networking API (the subprocess makes its own HTTP calls), so it does not satisfy PortSwigger's policy for third-party LLM extensions. It ships with the source for personal/research use; if you build a JAR you intend to submit to the BApp Store, comment out the `engines.add(new ClaudeCodeEngine(api))` line in `ByteBanterPayloadGenerator` first.

### Prompt and response context
Write your prompt to instruct the model on the kind of payloads to generate. Use the **Optimize!** button to rewrite your prompt while preserving the attack goal and your concrete details (target names, parameter names, secrets, regex patterns). Toggle **Stateful Interaction** to keep the conversation across payloads and provide the regex used to extract the relevant portion of the target response into the conversation.

### Sensitive Data persistence
The **"Persist API key and custom headers across sessions"** checkbox controls whether sensitive fields are written to Burp's extension data. It is **off by default**: API keys and custom headers live only in memory and must be re-entered each session. When checked, those fields are persisted to Burp's extension data file in plaintext on disk — only enable it on machines you trust. The checkbox state itself is always remembered.

### Success Verification
After each Intruder response, the selected LLM can judge whether the attack succeeded according to a user-defined criterion. The feature is **off by default**.

* Enable the checkbox in the Success Verification panel and write your success criterion in the textarea, or click **Generate from prompt!** to derive a starting criterion from your payload-generation prompt.
* The third widget in the panel changes shape based on **Stateful Interaction** (in the Context Regex panel):
  * **Stateless** (checkbox unchecked): the spinner is **"Truncate request/response (chars)"**. The verifier receives the raw HTTP request and response of the single payload being judged, capped at the configured number of characters per side. Default 4000.
  * **Stateful** (checkbox checked): the spinner becomes **"Conversation history depth (turns)"**. The verifier receives the last N `(ByteBanter payload, regex-extracted target response)` turns from the running conversation, so it can detect successes that emerge across multiple exchanges (e.g. a password reconstructed letter-by-letter). Default 1 = only the most recent turn.
  * Both values are persisted independently — toggling Stateful Interaction does not lose the value you set in the other mode.
* When a response matches the criterion, the Intruder result row is highlighted **red** and a banner-formatted entry is written to Burp's Event Log under the header `[ByteBanter] SUCCESSFUL ATTACK DETECTED`, including URL, status code, and a short English summary of the winning strategy.
* Only Intruder responses are evaluated; Repeater, Proxy, and other tools are unaffected.
* Each verification triggers one extra LLM call per Intruder response — factor that into your usage and any provider rate limits.

Settings are automatically saved by the extension and persisted in Burp's extension data (subject to the Sensitive Data opt-in above).

## Development
### Prerequisites
* Java Development Kit (JDK) 17 or higher
* Build tool (Maven or Gradle)
* Burp Suite (Community or Professional Edition)

### Contributing
Contributions are welcome!

## License
This project is licensed under the [MIT License](https://opensource.org/license/mit).

## Acknowledgments
* [PortSwigger](https://portswigger.net/) for Burp Suite and its extensibility.
* [OpenAI](https://openai.com/), [Anthropic](https://www.anthropic.com/), [Ollama](https://ollama.com/), and [Oobabooga](https://github.com/oobabooga) for providing powerful LLM APIs.
