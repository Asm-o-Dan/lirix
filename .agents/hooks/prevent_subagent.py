import sys
import json

def main():
    try:
        raw_input = sys.stdin.read()
        data = json.loads(raw_input) if raw_input.strip() else {}
    except Exception:
        data = {}

    tool_call = data.get("toolCall", {})
    tool_name = tool_call.get("name", "")

    # Блокируем вызов invoke_subagent согласно регламенту sdd-coordinator
    if tool_name == "invoke_subagent":
        response = {
            "decision": "deny",
            "reason": (
                "СТОП! Сработал защитный хук SDD-Координатора.\n"
                "Запрещено вызывать субагентов через invoke_subagent!\n"
                "Вспомни регламент /sdd-coordinator:\n"
                "1. Координатор не спавнит субагентов через этот инструмент.\n"
                "2. Задачи оформляются в виде спецификаций (.sdd/specs/) и детерминированных карточек (.sdd/tasks/).\n"
                "3. Управление ведется через .sdd/board.md и передачу задач исполнителям (внешние чаты / agy_send_chat_message / пользователь)."
            )
        }
    else:
        response = {"decision": "allow"}

    sys.stdout.write(json.dumps(response))
    sys.stdout.flush()

if __name__ == "__main__":
    main()
