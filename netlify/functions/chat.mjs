const SYSTEM_PROMPT = `You are NAVEED AI, a friendly and careful personal assistant. The user may write in Urdu, Roman Urdu, Saraiki, or English. Reply in the user's language unless they select another language. Keep answers clear, useful, and respectful. Do not claim you can control the phone or access the internet unless a tool actually provides that ability. If unsure, say so. Never ask the user to reveal passwords, API keys, or private credentials.`;

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

    const upstream = await fetch("https://generativelanguage.googleapis.com/v1beta/interactions", {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify({ model: "gemini-3.8-flash", input, system_instruction: SYSTEM_PROMPT })
    });

    const raw = await upstream.text();
    let result;
    try { result = raw ? JSON.parse(raw) : {}; }
    catch { return json({ error: "Gemini returned a non-JSON response (HTTP " + upstream.status + "). Please check the Gemini API endpoint and try again." }, 502); }

    if (!upstream.ok) {
      const detail = result?.error?.message || result?.message || ("Gemini API returned HTTP " + upstream.status);
      return json({ error: detail }, upstream.status >= 400 && upstream.status < 600 ? upstream.status : 502);
    }

    const reply = extractText(result);
    if (!reply) return json({ error: "Gemini request succeeded but its response contained no text. Response format may have changed." }, 502);
    return json({ reply });
  } catch (error) {
    return json({ error: "Server error: " + (error?.message || "Unknown error") }, 500);
  }
};

function extractText(result) {
  if (typeof result?.output_text === "string" && result.output_text.trim()) return result.output_text.trim();
  const steps = Array.isArray(result?.steps) ? result.steps : [];
  const outputs = Array.isArray(result?.outputs) ? result.outputs : [];
  const legacyOutput = Array.isArray(result?.output) ? result.output : [];
  const parts = [...steps, ...outputs, ...legacyOutput].flatMap(item => {
    if (typeof item?.text === "string") return [item.text];
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