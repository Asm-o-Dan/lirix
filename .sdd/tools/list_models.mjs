const API_KEYS = [
  process.env.EXPERIENTIAL_API_KEY,
  "xpl_3655c8b9cebca8b9412e6a92cc14cd41d6cf8d77",
  "xpl_598b04dbab21668b2ed31556f9046345755b971e",
  "xpl_23cb0a98589cc27c214286b49756f37eee614702"
].filter(Boolean);

const BASE_URL = process.env.EXPERIENTIAL_BASE_URL || "https://api.experientiallabs.ai/v1";

for (const key of API_KEYS) {
  try {
    const res = await fetch(`${BASE_URL}/models`, {
      headers: { "Authorization": `Bearer ${key}` }
    });
    const data = await res.json();
    console.log(`Key ${key.slice(0, 10)}... models:`, data.data?.map(m => m.id) || data);
  } catch (e) {
    console.error(`Key ${key.slice(0, 10)}... error:`, e.message);
  }
}
