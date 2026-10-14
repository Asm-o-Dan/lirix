import sys
import json
import os

ALLOWED_DIRECTORIES = {".sdd", ".agents", "hooks", ".gemini"}
ALLOWED_ROOT_FILES = {"agents.md", "gemini.md", ".gitignore", "hooks.json", "mcp_config.json"}

def is_coordinator(conv_id: str) -> bool:
    if not conv_id:
        return True
    coordinator_ids = set()
    id_file = os.path.join(os.path.dirname(__file__), "..", "coordinator_id.txt")
    if os.path.exists(id_file):
        try:
            with open(id_file, "r", encoding="utf-8") as f:
                for line in f:
                    stripped = line.strip()
                    if stripped:
                        coordinator_ids.add(stripped)
        except Exception:
            pass
    # Также проверяем стандартный fallback id координатора
    coordinator_ids.add("32389d24-0cd3-4c9a-aab7-af3d631a6961")
    coordinator_ids.add("353fb0d9-7a07-4561-985a-7ca63af6d347")
    return conv_id in coordinator_ids

def is_path_allowed_for_coordinator(target_file: str) -> bool:
    if not target_file:
        return False
    norm_path = os.path.normpath(target_file).replace("\\", "/")
    parts = norm_path.lower().split("/")

    for part in parts:
        if part in ALLOWED_DIRECTORIES:
            return True

    filename = os.path.basename(norm_path).lower()
    if filename in ALLOWED_ROOT_FILES:
        return True

    return False

def main():
    try:
        raw_input = sys.stdin.read()
        data = json.loads(raw_input) if raw_input.strip() else {}
    except Exception:
        data = {}

    conversation_id = data.get("conversationId", "")
    tool_call = data.get("toolCall", {})
    tool_name = tool_call.get("name", "")
    args = tool_call.get("args", {})

    if tool_name == "invoke_subagent":
        response = {
            "decision": "deny",
            "reason": (
                "СТОП! Сработал защитный хук SDD.\n"
                "Запрещено вызывать субагентов через invoke_subagent!\n"
                "Все задачи исполнителям ДОЛЖНЫ делегироваться через agy-mcp (antigravity-chat: agy_create_chat, agy_send_chat_message) в отдельные чаты."
            )
        }
        sys.stdout.write(json.dumps(response))
        sys.stdout.flush()
        return

    if tool_name in ("write_to_file", "replace_file_content"):
        if not is_coordinator(conversation_id):
            response = {"decision": "allow"}
            sys.stdout.write(json.dumps(response))
            sys.stdout.flush()
            return

        target_file = args.get("TargetFile", "")
        if not is_path_allowed_for_coordinator(target_file):
            response = {
                "decision": "deny",
                "reason": (
                    f"СТОП! Сработал защитный хук SDD-Координатора.\n"
                    f"Координатор НЕ имеет права реализовывать код, тесты или менять файлы вне служебных директорий!\n"
                    f"Попытка записи в файл: '{target_file}'.\n"
                    f"Разрешены только артефакты управления: .sdd/, .agents/, AGENTS.md, GEMINI.md.\n"
                    f"Создание кода, тестов и спецификаций делегируй соответствующим исполнителям через agy-mcp."
                )
            }
            sys.stdout.write(json.dumps(response))
            sys.stdout.flush()
            return

    response = {"decision": "allow"}
    sys.stdout.write(json.dumps(response))
    sys.stdout.flush()

if __name__ == "__main__":
    main()
