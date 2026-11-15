const SYSTEM_PROMPT = `You are NAVEED AI, a friendly and careful personal assistant. The user may write in Urdu, Roman Urdu, Saraiki, or English. Reply in the user's language unless they select another language. Keep answers clear, useful, and respectful. Do not claim you can control the phone or access the internet unless a tool actually provides that ability. If unsure, say so. Never ask the user to reveal passwords, API keys, or private credentials.`;

const MODELS = ["gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash"];

export default async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders() });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) return json({ error: "GEMINI_API_KEY is missing in Netlify environment variables." }, 500);

    const body = await req.json();
    const message = typeof body.message === "string" ? body.message.trim() : "";
    if (!message || message.length > 8000) return json({ error: "Message is empty or too long (maximum 8000 characters)." }, 400);

    const language = typeof body.language === "string" ? body.language : "same";
    const history = Array.isArray(body.history) ? body.history.slice(-10) : [];
    const transcript = history.map(item => {
      const role = item && item.role === "assistant" ? "NAVEED AI" : "User";
      const value = typeof item?.text === "string" ? item.text.slice(0, 4000) : "";
      return value ? role + ": " + value : "";
    }).filter(Boolean).join("\n");
    const input = "Preferred reply language: " + language + "\nConversation so far:\n" + transcript + "\nUser: " + message;

    let lastError = "All available Gemini models are temporarily unavailable.";
    for (const model of MODELS) {
      const upstream = await fetch("https://generativelanguage.googleapis.com/v1beta/interactions", {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
        body: JSON.stringify({ model, input, system_instruction: SYSTEM_PROMPT })
      });

      const raw = await upstream.text();
      let result;
      try { result = raw ? JSON.parse(raw) : {}; }
      catch { return json({ error: "Gemini returned a non-JSON response (HTTP " + upstream.status + ")." }, 502); }

      if (!upstream.ok) {
        lastError = result?.error?.message || result?.message || ("Gemini API returned HTTP " + upstream.status);
        // Try the next stable Flash model only for temporary capacity/rate-limit errors.
        if (upstream.status === 429 || upstream.status === 503 || upstream.status === 502) continue;
        return json({ error: lastError }, upstream.status);
      }

      const reply = extractText(result);
      if (!reply) return json({ error: "Gemini returned no text. Please try again." }, 502);
      return json({ reply, model });
    }

    return json({ error: "Gemini models are currently busy. Automatic fallback was attempted. Please try again shortly. Last error: " + lastError }, 503);
  } catch (error) {
    return json({ error: "Server error: " + (error?.message || "Unknown error") }, 500);
  }
};

function extractText(result) {
  if (typeof result?.output_text === "string" && result.output_text.trim()) return result.output_text.trim();
  const collections = [result?.steps, result?.outputs, result?.output].filter(Array.isArray);
  const parts = collections.flat().flatMap(item => {
    if (typeof item?.text === "string") return [{ text: item.text }];
    return Array.isArray(item?.content) ? item.content : [];
  });
  return parts.filter(part => typeof part?.text === "string").map(part => part.text).join("\n").trim();
}
function corsHeaders() {
  return { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "Content-Type", "Access-Control-Allow-Methods": "POST, OPTIONS" };
}
function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: corsHeaders() });
}