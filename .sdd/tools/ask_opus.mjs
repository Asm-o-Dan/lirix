import fs from "fs";

const API_KEYS = [
  process.env.EXPERIENTIAL_API_KEY,
  "xpl_3655c8b9cebca8b9412e6a92cc14cd41d6cf8d77",
  "xpl_598b04dbab21668b2ed31556f9046345755b971e",
  "xpl_23cb0a98589cc27c214286b49756f37eee614702"
].filter(Boolean);

const BASE_URL = process.env.EXPERIENTIAL_BASE_URL || "https://api.experientiallabs.ai/v1";

export async function askOpus(prompt, systemPrompt = "You are Claude Opus 5.5, a world-class senior software architect.") {
  const model = "claude-opus-5.5";
  const messages = [
    { role: "system", content: systemPrompt },
    { role: "user", content: prompt }
  ];

  let lastError = null;

  for (const key of API_KEYS) {
    try {
      const res = await fetch(`${BASE_URL}/chat/completions`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${key}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model,
          messages,
          temperature: 1.0,
          max_tokens: 3500
        }),
      });

      const data = await res.json();
      if (!res.ok || data.error) {
        lastError = data.error?.message || `HTTP ${res.status}: ${JSON.stringify(data)}`;
        continue;
      }

      return data.choices?.[0]?.message?.content || "";
    } catch (err) {
      lastError = err.message;
    }
  }

  throw new Error(lastError || "Failed to query Claude Opus 5.5");
}

// CLI usage
const args = process.argv.slice(2);
if (args.length > 0) {
  const prompt = args.join(" ");
  askOpus(prompt)
    .then(answer => {
      console.log(answer);
    })
    .catch(err => {
      console.error("Opus Error:", err.message);
      process.exit(1);
    });
}
