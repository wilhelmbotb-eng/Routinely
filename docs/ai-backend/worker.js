// Routinely AI backend: a Cloudflare Worker that keeps your Anthropic API key off the phone.
//
// Deploy (one time):
//   1. Create a Worker in the Cloudflare dashboard and paste this file in.
//   2. Add a secret named ANTHROPIC_API_KEY (Settings > Variables and Secrets > Secret).
//   3. Optional variable MODEL (defaults to claude-sonnet-5-5).
//   4. Copy the Worker URL (https://...workers.dev) into Routinely > Settings > AI backend URL.
//
// The app sends {"messages":[{"role":"user"|"assistant","content":"..."}]}
// and expects {"reply":"..."} back. Add rate limiting before sharing the app publicly.

const SYSTEM =
  "You are Routinely AI, a friendly planning assistant inside a daily-routine app. " +
  "Help with routines, habits, study plans and time management. Be concise and practical. " +
  "When you suggest a routine, list it as times with short task names.";

export default {
  async fetch(request, env) {
    if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
    let body;
    try { body = await request.json(); } catch { return new Response("Bad JSON", { status: 400 }); }
    const messages = (body.messages || []).slice(-12).map(m => ({
      role: m.role === "assistant" ? "assistant" : "user",
      content: String(m.content || "").slice(0, 4000),
    }));
    if (!messages.length) return new Response("No messages", { status: 400 });

    const upstream = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-api-key": env.ANTHROPIC_API_KEY,
        "anthropic-version": "2023-06-01",
      },
      body: JSON.stringify({
        model: env.MODEL || "claude-sonnet-5-5",
        max_tokens: 1024,
        system: SYSTEM,
        messages,
      }),
    });
    if (!upstream.ok) return new Response("Upstream error " + upstream.status, { status: 502 });
    const data = await upstream.json();
    const reply = (data.content || []).map(b => b.text || "").join("").trim();
    return new Response(JSON.stringify({ reply }), { headers: { "content-type": "application/json" } });
  },
};
