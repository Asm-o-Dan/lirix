# Задача TASK-AUD-WASAPI-01: Честный пик-метр через WASAPI Loopback (IAudioMeterInformation)

- **ID задачи:** `TASK-AUD-WASAPI-01`
- **Роль исполнителя:** Windows / Audio Engineer
- **Зона:** `win-ingress`
- **Файлы:**
  - `native/lirix-media-bridge/WasapiLoopbackMeter.cs`
  - `desktop/src/main/kotlin/com/lirix/app/desktop/ingress/DesktopAudioMeterService.kt`
- **Зависит от:** [ADR-022](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-022), `RESEARCH-UI-01`
- **Приоритет:** HIGH

---

## 1. Цель задачи
Реализовать честное считывание уровня аудиопотока Windows:
1. **Windows Core Audio API (WASAPI):**
   - Получение интерфейса `IAudioMeterInformation` для дефолтного устройства вывода (Render Loopback).
   - Опрос пиковых уровней стерео-каналов $L$ и $R$ (`GetChannelsPeakValues`) с частотой 30–60 Гц.
2. **Масштаб и калибровка:**
   - Перевод амплитуды $A \in [0.0..1.0]$ в децибелы полной цифровой шкалы:
     $$\text{dBFS} = 20 \log_{10}(A)$$
   - Шкала отображения: от $-60\,\text{dBFS}$ (тишина) до $0\,\text{dBFS}$ (максимум).
   - Детекция клиппинга (`Clip`): флаг выставляется при $\text{dBFS} > -1.0\,\text{dBFS}$.
3. **Безопасность и автономность:**
   - Если устройство отключено или сэмплы не поступают, служба переходит в статус `OFFLINE` (никаких фейковых колебаний или синусоид).
   - Потокобезопасная передача в Compose State (`StateFlow<AudioLevels>`).

---

## 2. Критерии приемки (DoD)
- [ ] Индикатор показывает реальный уровень звука системного микса при воспроизведении в любом плеере/браузере.
- [ ] При тишине (паузе) индикаторы падают до нуля.
- [ ] Потребление CPU сервисом замера $< 0.5\%$.
