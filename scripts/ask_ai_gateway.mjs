import fs from 'fs';
import path from 'path';

const API_KEYS = [
  process.env.EXPERIENTIAL_API_KEY,
  'xpl_3655c8b9cebca8b9412e6a92cc14cd41d6cf8d77',
  'xpl_598b04dbab21668b2ed31556f9046345755b971e',
  'xpl_23cb0a98589cc27c214286b49756f37eee614702'
].filter(Boolean);

const BASE_URL = process.env.EXPERIENTIAL_BASE_URL || 'https://api.experientiallabs.ai/v1';

export async function askModel(model, prompt, systemPrompt = '', options = {}) {
  const allowed = ['claude-opus-5.5', 'gpt-6-sol', 'gpt-6-luna', 'gpt-5.6-luna', 'nemotron-3-ultra', 'jev'];
  if (!allowed.includes(model)) {
    throw new Error(`Model ${model} is not in the free whitelist: ${allowed.join(', ')}`);
  }

  const messages = [];
  if (systemPrompt) messages.push({ role: 'system', content: systemPrompt });
  messages.push({ role: 'user', content: prompt });

  let lastError = null;
  for (let i = 0; i < API_KEYS.length; i++) {
    const key = API_KEYS[i];
    try {
      console.log(`[AI-Gateway] Calling ${model} (Key #${i + 1})...`);
      const body = {
        model,
        messages,
        temperature: 1.0
      };
      if (options.max_tokens) body.max_tokens = options.max_tokens;

      const res = await fetch(`${BASE_URL}/chat/completions`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${key}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(body)
      });

      const data = await res.json();
      if (!res.ok || data.error) {
        lastError = data.error?.message || `HTTP ${res.status}: ${JSON.stringify(data)}`;
        console.warn(`[AI-Gateway] Key #${i + 1} warning: ${lastError}`);
        continue;
      }

      const content = data.choices?.[0]?.message?.content;
      if (content) {
        console.log(`[AI-Gateway] Success! Received ${content.length} characters from ${model}.`);
        return content;
      }
    } catch (e) {
      lastError = e.message;
      console.warn(`[AI-Gateway] Network error on key #${i + 1}: ${e.message}`);
    }
  }

  throw new Error(`All keys failed. Last error: ${lastError}`);
}

const args = process.argv.slice(2);
if (args.length >= 2) {
  const [model, promptArg, outputFile, sysPromptArg] = args;
  let prompt = promptArg;
  if (fs.existsSync(promptArg)) {
    prompt = fs.readFileSync(promptArg, 'utf-8');
  }

  let sysPrompt = '';
  if (sysPromptArg && fs.existsSync(sysPromptArg)) {
    sysPrompt = fs.readFileSync(sysPromptArg, 'utf-8');
  }

  askModel(model, prompt, sysPrompt)
    .then(reply => {
      if (outputFile) {
        fs.mkdirSync(path.dirname(outputFile), { recursive: true });
        fs.writeFileSync(outputFile, reply, 'utf-8');
        console.log(`[AI-Gateway] Saved response to ${outputFile}`);
      } else {
        console.log('\n--- RESPONSE ---\n');
        console.log(reply);
      }
    })
    .catch(err => {
      console.error('[AI-Gateway Fatal Error]:', err.message);
      process.exit(1);
    });
}
