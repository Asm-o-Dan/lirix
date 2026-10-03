const API_KEYS = [
  process.env.EXPERIENTIAL_API_KEY,
  "xpl_3655c8b9cebca8b9412e6a92cc14cd41d6cf8d77",
  "xpl_598b04dbab21668b2ed31556f9046345755b971e",
  "xpl_23cb0a98589cc27c214286b49756f37eee614702"
].filter(Boolean);

const BASE_URL = process.env.EXPERIENTIAL_BASE_URL || "https://api.experientiallabs.ai/v1";

/**
 * Query TypeSafe Jev model via /v1/systemone
 * Exactly as specified: one request only, do not auto-retry blindly.
 */
export async function queryJev(payload) {
  const key = API_KEYS[0];
  const res = await fetch(`${BASE_URL}/systemone`, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${key}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  const text = await res.text();
  try {
    const data = JSON.parse(text);
    return { ok: res.ok, status: res.status, data };
  } catch (e) {
    return { ok: res.ok, status: res.status, raw: text };
  }
}

// Execute user sample
const payload = {
  model: "jev-latest",
  state: {
    task: "Review a proposed change before a human decides whether to merge it.",
    diff_summary: "Reject an empty project name before writing a record.",
    test_summary: "The new empty-name test and existing creation tests pass. No integration tests were run."
  },
  questions: {
    review: {
      type: "choice",
      instructions: "Does the supplied evidence support accepting this change, or does it need further review? Treat missing evidence as a reason to review.",
      criteria: {
        accept: "The described change meets the task and the supplied tests cover its relevant behavior.",
        review: "Evidence is missing, the tests are insufficient, or the change may be incorrect."
      }
    }
  }
};

console.log("Sending query to Jev (systemone)...");
queryJev(payload).then(res => {
  console.log("Status:", res.status);
  console.log("Response:", JSON.stringify(res.data || res.raw, null, 2));
});
