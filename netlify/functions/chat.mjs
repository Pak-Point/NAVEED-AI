const SYSTEM_PROMPT = `You are NAVEED AI, a friendly and careful personal assistant. The user may write in Urdu, Roman Urdu, Saraiki, or English. Reply in the user's language unless they select another language. Keep answers clear, useful, and respectful. Do not claim you can control the phone or access the internet unless a tool actually provides that ability. If unsure, say so. Never ask the user to reveal passwords, API keys, or private credentials.`;

export default async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders() });
  }
  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }
  try {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) return json({ error: "Server setup incomplete: GEMINI_API_KEY is not configured in Netlify." }, 500);

    const body = await req.json();
    const message = typeof body.message === "string" ? body.message.trim() : "";
    if (!message || message.length > 8000) return json({ error: "Message is empty or too long (maximum 8000 characters)." }, 400);

    const language = typeof body.language === "string" ? body.language : "same";
    const history = Array.isArray(body.history) ? body.history.slice(-10) : [];
    const transcript = history.map(item => {
      const role = item && item.role === "assistant" ? "NAVEED AI" : "User";
      const text = typeof item?.text === "string" ? item.text.slice(0, 4000) : "";
      return text ? role + ": " + text : "";
    }).filter(Boolean).join("\n");
    const input = SYSTEM_PROMPT + "\nPreferred reply language: " + language + "\nConversation so far:\n" + transcript + "\nUser: " + message + "\nNAVEED AI:";

    const response = await fetch("https://generativelanguage.googleapis.com/v1beta/interactions", {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
      body: JSON.stringify({ model: "gemini-3.8-flash", input })
    });
    const result = await response.json();
    if (!response.ok) {
      const detail = result?.error?.message || "Gemini API request failed.";
      return json({ error: detail }, response.status);
    }
    const reply = result.output_text || extractText(result.output);
    if (!reply) return json({ error: "AI returned an empty response. Please try again." }, 502);
    return json({ reply });
  } catch (error) {
    return json({ error: "Server error: " + (error?.message || "Unknown error") }, 500);
  }
};

function extractText(output) {
  if (!Array.isArray(output)) return "";
  return output.flatMap(item => Array.isArray(item.content) ? item.content : [])
    .filter(part => typeof part.text === "string")
    .map(part => part.text).join("\n").trim();
}
function corsHeaders() {
  return { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "Content-Type", "Access-Control-Allow-Methods": "POST, OPTIONS" };
}
function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: corsHeaders() });
}