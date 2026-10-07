import { askModel } from './ask_ai_gateway.mjs';
import fs from 'fs';
import path from 'path';

const promptPath = path.resolve('build_tmp/architect_master_prompt.txt');
const prompt = fs.readFileSync(promptPath, 'utf8');

async function testQuota(model) {
  try {
    const res = await askModel(model, 'Test ping. Respond READY.', '');
    return res && res.includes('READY');
  } catch (e) {
    return false;
  }
}

async function runModelGeneration(model, outputFile) {
  console.log(`[Batch-Runner] Starting full generation for ${model} -> ${outputFile}...`);
  try {
    const content = await askModel(model, prompt, 'Ты — Главный Системный Архитектор Android Event Engine.');
    if (content) {
      fs.writeFileSync(outputFile, content, 'utf8');
      console.log(`[Batch-Runner] SUCCESS: Generated ${content.length} characters in ${outputFile}`);
      return true;
    }
  } catch (e) {
    console.error(`[Batch-Runner] ERROR generating ${model}: ${e.message}`);
  }
  return false;
}

async function loop() {
  console.log(`[Batch-Runner] Poller started at ${new Date().toISOString()}. Monitoring quotas for reset at 17:00 UTC (20:00 MSK)...`);
  
  let solDone = fs.existsSync('build_tmp/gpt6_sol_architectural_plan.md');
  let opusDone = fs.existsSync('build_tmp/opus_architectural_plan.md');

  while (!solDone || !opusDone) {
    const now = new Date();
    console.log(`[Batch-Runner] [${now.toISOString()}] Status: solDone=${solDone}, opusDone=${opusDone}`);
    
    if (!solDone) {
      const ready = await testQuota('gpt-6-sol');
      if (ready) {
        console.log(`[Batch-Runner] GPT-6 Sol quota is OPEN! Launching...`);
        solDone = await runModelGeneration('gpt-6-sol', 'build_tmp/gpt6_sol_architectural_plan.md');
      } else {
        console.log(`[Batch-Runner] GPT-6 Sol quota still waiting for 17:00 UTC reset.`);
      }
    }

    if (!opusDone) {
      const ready = await testQuota('claude-opus-5.5');
      if (ready) {
        console.log(`[Batch-Runner] Claude Opus 5.5 quota is OPEN! Launching...`);
        opusDone = await runModelGeneration('claude-opus-5.5', 'build_tmp/opus_architectural_plan.md');
      } else {
        console.log(`[Batch-Runner] Claude Opus 5.5 quota still waiting for 17:00 UTC reset.`);
      }
    }

    if (solDone && opusDone) {
      console.log('[Batch-Runner] ALL GENERATIONS COMPLETED!');
      break;
    }

    // Check every 60 seconds
    await new Promise(r => setTimeout(r, 60000));
  }
}

loop();
