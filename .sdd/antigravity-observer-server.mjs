import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';

// Reuse the installed MCP operations, adding a read-only chat receipt/status tool.
const original = 'C:/Users/DaniilTuT/.gemini/config/antigravity-chat-mcp/server.mjs';
const require = createRequire(original);
let source = readFileSync(original, 'utf8');
source = source.replaceAll('"@modelcontextprotocol/server"', JSON.stringify(pathToFileURL(require.resolve('@modelcontextprotocol/server')).href));
source = source.replaceAll('"@modelcontextprotocol/server/stdio"', JSON.stringify(pathToFileURL(require.resolve('@modelcontextprotocol/server/stdio')).href));
source = source.replaceAll('"zod/v4"', JSON.stringify(pathToFileURL(require.resolve('zod/v4')).href));
const observer = `
server.registerTool('agy_stop_chat', {
  title: 'Stop Antigravity Chat Task',
  description: 'Cancel the active turn of one explicitly named conversation.',
  inputSchema: z.object({name:z.string(),callerId:z.string().optional()})
}, async ({name,callerId}) => {
  try {
    return await withCDP(async session => {
      const target=await switchToChatByName(session,name);
      const cancelSelector='button[aria-label="Cancel (Ctrl+D)"]';
      const clicked=await cdpEval(session, radixClick('document.querySelector(' + JSON.stringify(cancelSelector) + ')'));
      await delay(1000);
      const state=await cdpEval(session, \`(() => ({url:location.href,title:document.title,cancelVisible:!!document.querySelector('button[aria-label="Cancel (Ctrl+D)"]'),text:document.body.innerText.slice(-12000)}))()\`);
      if(callerId && callerId!==target.conversationId) await switchToChat(session,callerId);
      return {content:[{type:'text',text:JSON.stringify({clicked,...state})}]};
    });
  } catch(error) {return {isError:true,content:[{type:'text',text:error.message}]};}
});
server.registerTool('agy_read_chat', {
  title: 'Read Antigravity Chat Status',
  description: 'Read visible conversation text and composer controls; no messages sent.',
  inputSchema: z.object({name: z.string(), callerId: z.string().optional()})
}, async ({name, callerId}) => {
  try {
    return await withCDP(async session => {
      const previous = callerId || await cdpEval(session, 'window.location.href.match(/\\/c\\/([a-f0-9-]+)/)?.[1] || null');
      const target = await switchToChatByName(session, name);
      const state = await cdpEval(session, \`(() => ({
        url: location.href, title: document.title,
        text: document.body.innerText.slice(-18000),
        inputs: Array.from(document.querySelectorAll('textarea,[contenteditable="true"]')).map(el => ({label:el.getAttribute('aria-label'), text:el.value || el.innerText})),
        buttons: Array.from(document.querySelectorAll('button')).map(el => ({label:el.getAttribute('aria-label'),text:el.innerText,disabled:el.disabled})).filter(el=>el.label || el.text).slice(-40)
      }))()\`);
      if (previous && previous !== target.conversationId) await switchToChat(session, previous);
      return {content:[{type:'text',text:JSON.stringify(state)}]};
    });
  } catch(error) {return {isError:true,content:[{type:'text',text:error.message}]};}
});
`;
source = source.replace('main().catch', observer + '\nmain().catch');
try {
  await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
} catch (error) {
  process.stderr.write(error.name + ': ' + error.message + '\n');
  process.exitCode=1;
}
