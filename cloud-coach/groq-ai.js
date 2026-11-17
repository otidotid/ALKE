/** 
 * Groq transport adapter for ALKE Cloud Coach.
 * Groq API credentials live exclusively in Cloudflare Worker secrets.
 * Model identifiers stay compatible with the existing Workers AI service callers.
 */
const GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";
const MODELS = new Map([
  ["@cf/openai/gpt-oss-20b", "openai/gpt-oss-20b"],
  ["@cf/openai/gpt-oss-120b", "openai/gpt-oss-120b"],
]);
const MAX_RESPONSE_BYTES = 128 * 1024;

export function createGroqAI(apiKey, fetchImpl = fetch) {
  if (typeof apiKey !== "string" || !apiKey.trim()) {
    throw new TypeError("Groq API key missing");
  }
  return {
    async run(model, parameters) {
      const groqModel = MODELS.get(model);
      if (!groqModel) throw new TypeError("Unsupported Groq model");
      const messages = parameters?.input ?? parameters?.messages;
      if (!Array.isArray(messages) || messages.length < 1 ||
          !messages.every(item => ["system", "user", "assistant"].includes(item?.role)
            && typeof item.content === "string")) {
        throw new TypeError("Invalid Groq input messages");
      }
      const maxTokens = parameters?.max_output_tokens ?? parameters?.max_tokens;
      if (!Number.isSafeInteger(maxTokens) || maxTokens < 1 || maxTokens > 8192) {
        throw new TypeError("Invalid Groq token budget");
      }
      const effort = parameters?.reasoning?.effort ?? parameters?.reasoning_effort ?? "low";
      if (!["low", "medium", "high"].includes(effort)) throw new TypeError("Invalid Groq reasoning effort");
      const payload = {model:groqModel, messages, max_completion_tokens:maxTokens,
        reasoning_effort:effort, stream:false};
      if (parameters.temperature !== undefined) payload.temperature = parameters.temperature;
      let response;
      try {
        response = await fetchImpl(GROQ_ENDPOINT, {
          method:"POST",
          headers:{"Authorization":`Bearer ${apiKey}`, "Content-Type":"application/json",
            "Accept":"application/json"},
          body:JSON.stringify(payload),
          signal:AbortSignal.timeout(60000),
        });
      } catch (error) {
        if (error?.name === "TimeoutError" || error?.name === "AbortError") {
          throw new Error("Groq upstream timeout");
        }
        throw new Error("Groq upstream network unavailable");
      }
      if (!response.ok) {
        if (response.status === 429) throw new Error("Groq rate limit 429");
        if (response.status >= 500) throw new Error("Groq upstream temporarily unavailable 503");
        // Never log or return upstream bodies; they may contain sensitive provider diagnostics.
        if (response.status === 401 || response.status === 403) {
          throw new Error("Groq server API key rejected");
        }
        throw new Error(`Groq upstream HTTP ${response.status}`);
      }
      if (Number(response.headers?.get("content-length")) > MAX_RESPONSE_BYTES) {
        throw new Error("Groq response too large");
      }
      const text = await response.text();
      if (new TextEncoder().encode(text).length > MAX_RESPONSE_BYTES) {
        throw new Error("Groq response too large");
      }
      let result;
      try { result = JSON.parse(text); } catch (_) {
        throw new Error("Invalid Groq response");
      }
      // extractWorkersAIText validates the one assistant-visible completion and
      // rejects reasoning, tool calls and incomplete responses.
      if (!Array.isArray(result?.choices) || result.choices.length !== 1) {
        throw new Error("Invalid Groq choices");
      }
      return result;
    },
  };
}
